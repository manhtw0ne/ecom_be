# 07 — Bản đồ mã nguồn và luồng hiện tại

[Lộ trình](00-lo-trinh.md) · [Kế hoạch nâng cấp](../upgrades/00-ke-hoach-fresher.md)

Đối chiếu code ngày 23/09/2026. Đây là bản đồ đọc code; các nhận xét cần kiểm chứng runtime không được coi là kết quả test mới.

## Bản đồ tra cứu

Gốc Java: [com/manh/ecom_be](../../src/main/java/com/manh/ecom_be).

| Câu hỏi | Điểm bắt đầu | Bài học |
| --- | --- | --- |
| Request được phép đi đâu? | [WebSecurityConfig](../../src/main/java/com/manh/ecom_be/configurations/WebSecurityConfig.java), [JwtTokenFilter](../../src/main/java/com/manh/ecom_be/filters/JwtTokenFilter.java) | 03 |
| Giá/quyền của đơn lấy từ đâu? | [OrderController](../../src/main/java/com/manh/ecom_be/controllers/OrderController.java), [OrderService](../../src/main/java/com/manh/ecom_be/services/orders/OrderService.java) | 04 |
| Khóa dữ liệu ở đâu? | [ProductRepository](../../src/main/java/com/manh/ecom_be/repositories/ProductRepository.java), [OrderRepository](../../src/main/java/com/manh/ecom_be/repositories/OrderRepository.java) | 04 |
| Cache được xóa lúc nào? | [ProductListener](../../src/main/java/com/manh/ecom_be/models/ProductListener.java), [ProductRedisService](../../src/main/java/com/manh/ecom_be/services/product/ProductRedisService.java) | 05 |
| Email có rollback cùng đơn không? | [EmailService](../../src/main/java/com/manh/ecom_be/services/email/EmailService.java), [TransactionCallbacks](../../src/main/java/com/manh/ecom_be/components/TransactionCallbacks.java) | 05 |
| Kafka dùng vào đâu? | [CategoryController](../../src/main/java/com/manh/ecom_be/controllers/CategoryController.java), [MyKafkaListener](../../src/main/java/com/manh/ecom_be/components/MyKafkaListener.java) | 05 |
| Thanh toán đã đầy đủ chưa? | [PaymentController](../../src/main/java/com/manh/ecom_be/controllers/PaymentController.java), [VNPayService](../../src/main/java/com/manh/ecom_be/services/vnpay/VNPayService.java) | 05 |
| Schema được tạo thế nào? | [Migration V0–V8](../../src/main/resources/db/migration), [FlywayConfig](../../src/main/java/com/manh/ecom_be/configurations/FlywayConfig.java) | 01 |
| Cách chứng minh hành vi? | [Tests](../../src/test/java/com/manh/ecom_be), [CI](../../.github/workflows/ci.yml) | 06 |

## 1. Đăng nhập và gọi API

UserController nhận DTO → UserService kiểm tra tài khoản/mật khẩu → tiện ích JWT và TokenService cấp/lưu token → trả response. Request tiếp theo mang Bearer token → filter xác thực → SecurityContext → endpoint kiểm tra quyền.

Điểm cần đọc riêng: endpoint refresh, token revoked/expired, tài khoản bị khóa và quyền sở hữu. Có JWT không tự bảo vệ tất cả các đường đọc dữ liệu.

## 2. Tạo đơn và hủy đơn

OrderController.createOrder gán userId từ người đăng nhập → OrderService mở transaction → gộp dòng giỏ/sắp productId → lấy Redis lock khi có Redisson và DB row lock → đọc giá/tồn → trừ kho/tính coupon → lưu order/details → phát OrderPlaced → commit → email listener và metric sau commit.

Redis lock được thả khi transaction hoàn tất. Nếu nghiệp vụ thất bại, thay đổi DB rollback. Email chỉ là best-effort; process chết có thể làm mất tác vụ gửi.

Hủy đơn: controller kiểm tra owner/trạng thái → service khóa dòng order → kiểm tra chuyển trạng thái → hoàn kho → đổi trạng thái → commit. Cần phân biệt soft delete đơn với hủy đơn hoàn kho, không coi hai thao tác tương đương.

## 3. Đọc và sửa catalog

ProductController phối hợp ProductService và ProductRedisService. Key cache có page, limit, keyword, categoryId, sort. ProductRedisService giữ danh sách response trong 5 phút; metadata/phần query còn lại cần đọc tại controller.

Entity Product có lifecycle listener gọi clear; clear lên lịch xóa namespace products:* sau commit. Đây là cache có thể nhất quán trễ, không phải nguồn đảm bảo tồn kho cho checkout.

Category dùng cache riêng với TTL 10 phút; controller còn gửi sự kiện Kafka khi tạo/lấy danh mục. Không suy ra Kafka là bắt buộc đối với mọi nghiệp vụ catalog.

## 4. Thanh toán hiện có và khoảng trống

PaymentController cung cấp create_payment_url, query, refund. VNPayService lấy amount từ DTO, tạo reference ngẫu nhiên và URL ký; còn gọi API query/refund.

Không thấy luồng callback/IPN lưu kết quả thanh toán vào order trong controller/service này. Vì vậy không mô tả ứng dụng đã xác nhận thanh toán end-to-end. Kế hoạch bổ sung gồm payment attempt liên kết order, xác minh thông báo, đối chiếu số tiền và idempotency.

## 5. Upload và tính năng bổ sung

FileUtils cùng các controller/service ảnh xử lý file và metadata. Kiểm tra riêng đường dẫn lưu, kiểm tra loại/kích thước, quyền và dọn file khi thất bại.

Comment/Coupon có controller/service/test. Favorite có model/repository/response trong cây code hiện tại; cần kiểm tra API và quy tắc trước khi ghi là chức năng hoàn chỉnh.

## Cách đọc một luồng

Chọn một request thật và ghi method/path → DTO → filter/quyền → controller → service → repository/SQL → commit/tác dụng phụ → response. Đặt breakpoint ở ranh giới mình chưa hiểu; dùng test làm ví dụ đầu vào. Không đọc toàn bộ repository từ file đầu tới cuối.
