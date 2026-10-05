# Nâng cấp ngày 27/09/2026 — Quyền truy cập đơn hàng

[Kế hoạch](00-ke-hoach-fresher.md) · [P0](01-p0-tinh-dung-va-bao-mat.md)

## Vấn đề và thay đổi

Trước đây GET orders được permitAll; service đọc theo ID/userId không kiểm tra chủ đơn. Người biết ID có thể đọc dữ liệu không thuộc mình. Chỉ kiểm tra JWT hoặc ROLE_USER chưa giải quyết được lỗi này.

OrderAccess kiểm tra tài khoản đang hoạt động, role và owner. OrderService/OrderDetailService gọi kiểm tra trước khi trả dữ liệu; quyền không chỉ nằm ở controller. API tìm kiếm toàn bộ đơn và các thao tác quản trị yêu cầu ADMIN.

## Hợp đồng HTTP sau thay đổi

Các đường dẫn dưới đây có tiền tố /api/v1.

| Endpoint | Chưa đăng nhập | USER chủ đơn | USER khác | ADMIN |
|---|---|---|---|---|
| GET /orders/{id} | 401 | 200 | 403 | 200 |
| GET /orders/user/{userId} | 401 | Danh sách của mình | 403 | Danh sách user được chọn |
| GET /order-details/{id} | 401 | 200 | 403 | 200 |
| GET /order-details/order/{orderId} | 401 | 200 | 403 | 200 |
| GET /orders/get-orders-by-keyword | 401 | 403 | 403 | 200 |
| PUT /orders/cancel/{id} | 401 | Theo trạng thái | 403 | Dùng endpoint status của admin |
| PUT /orders/{id}, PUT /orders/{id}/status, DELETE /orders/{id} | 401 | 403 | 403 | Theo nghiệp vụ hiện có |
| POST/PUT/DELETE /order-details | 401 | 403 | 403 | 403 |

Chi tiết đơn là snapshot giá/số lượng do luồng tạo đơn sinh ra cùng tồn kho và tổng tiền. API ghi chi tiết độc lập đã bị chặn cả controller lẫn service, kể cả admin, vì trước đây bỏ qua các quy tắc này. Client cần gửi cart_items qua POST /orders. Đây là thay đổi hợp đồng có chủ đích; giao diện đang dùng CRUD chi tiết đơn cần điều chỉnh.

GET theo ID và cancel trả 404 khi không tồn tại. GET /orders/{id} chỉ nhận primary key của order, không còn tự suy đoán ID là VNPay transaction reference. Phần tra cứu thanh toán cần một hợp đồng riêng có kiểm tra quyền.

POST /orders lấy owner từ principal; đổi user_id trong JSON không tạo đơn cho người khác. Tổng tiền vẫn được tính từ giá server.

## Hủy đơn và transaction

Controller gọi cancelOrder. Service lấy row lock của đơn, kiểm tra owner ngay dưới khóa đó, rồi áp dụng state transition và hoàn kho. Hủy lại một đơn đã hủy trả thành công mà không hoàn kho lần hai. Đơn shipped/delivered không được hủy bằng luồng này.

Kiểm tra quyền dưới cùng khóa tránh khoảng trống giữa việc đọc owner ở controller và cập nhật trạng thái ở service. Test cạnh tranh tồn kho hiện có được giữ và chuyển ca hủy đồng thời sang cancelOrder mới.

## Cách chạy kiểm chứng

```powershell
mvn -B '-Dtest=OrderAuthorizationTest,OrderServiceTest,OrderDetailServiceTest' test
mvn -B clean verify
# Chỉ chạy khi Docker/MySQL/Redis test đã sẵn sàng:
mvn -B verify -Pintegration
```

OrderAuthorizationTest chạy filter chain, service và H2 thật; không mock service đơn hàng hoặc OrderAccess. Có ma trận anonymous/owner/stranger/admin, ID mất, giả user_id, JWT thật do ứng dụng ký, hủy lại, trạng thái không cho hủy và chặn CRUD chi tiết đơn. Test gọi service trực tiếp cũng xác minh không thể bỏ qua quyền tại controller.

OrderConcurrencyTest mock riêng OrderAccess để tập trung transaction/tồn kho; test này không phải bằng chứng phân quyền. H2 không thay thế bằng chứng row lock trên MySQL. Ca kiểm tra tồn kho sau bulk update phải clear persistence context để đọc lại DB, tránh đọc entity cũ trong bộ nhớ.

## Môi trường và phạm vi xác minh

- Commit nền: 6eff4370447150a0e62514981f06b83a81f22bea; working tree đã có nhiều thay đổi chưa commit trước lần nâng cấp này.
- Windows 11, Temurin Java 21.0.9, Maven 3.9.11, Spring Boot 3.3.5, profile test/H2.
- Baseline trước sửa: mvn -B verify thành công, 88 test, không failure/error/skipped, đạt coverage gate hiện có.
- Sau sửa: mvn -B clean verify thành công; 118 test, 0 failure/error/skipped. Riêng OrderAuthorizationTest có 32 ca. Service line coverage 589/802 = 73,44%, đạt gate 70%.
- Log sau sửa: target/fresher-verify-20260927.log; JUnit reports: target/surefire-reports; coverage: target/site/jacoco/index.html. Các file target là artifact local, không commit.
- Docker engine chưa chạy trong lần xác minh này; chưa chạy lại integration MySQL/Redis, không xác nhận CI hoặc deploy từ xa.

## Việc tiếp theo

Đây là phần đơn hàng của F01, chưa đóng toàn bộ P0. Còn rà ownership của comment/user/avatar/payment; F02 chuyển Float sang BigDecimal/DECIMAL; F03 hoàn thiện thanh toán hoặc giới hạn demo COD; F04 refresh lifecycle và endpoint nội bộ. Hợp đồng mã lỗi not-found chung hiện vẫn cần chuẩn hóa ở F05.

Khi phỏng vấn, có thể trình bày tình huống cụ thể: user A đổi orderId thành đơn của B; tại sao role không đủ; vì sao kiểm tra ở service; tại sao khóa đơn khi hủy; và test H2 chưa chứng minh điều gì trên MySQL.