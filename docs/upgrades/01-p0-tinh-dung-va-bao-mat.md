# 01 — P0: tính đúng và quyền truy cập

[Tổng kế hoạch](00-ke-hoach-fresher.md) · Tiếp: [P1/P2](02-p1-chat-luong-va-mo-rong.md)

Đây là backlog P0; tiến độ mới được ghi ngay dưới từng mục. Phần không có cập nhật vẫn chờ triển khai hoặc xác minh.

## F00 — Lập baseline có thể lặp lại

**Việc làm**

1. Ghi commit, thay đổi working tree, Java/Maven/Docker và profile.
2. Chạy test/verify rồi integration trên MySQL/Redis riêng theo [hướng dẫn nghiệm thu](03-nghiem-thu-va-ho-so.md).
3. Ghi rõ bước nào pass/fail/chưa chạy, lưu report; không chép số liệu lịch sử thành kết quả mới.
4. Nếu test lỗi, phân biệt lỗi môi trường với lỗi nghiệp vụ; giữ test tái hiện lỗi.

**Hoàn thành khi:** có một báo cáo đầu vào để so sánh sau sửa, kể cả khi baseline đang fail. Báo cáo ghi đủ nguyên nhân chưa chạy nếu thiếu hạ tầng.

## F01 — Quyền trên đơn hàng và dữ liệu người dùng

> Cập nhật 29/09/2026: upload ảnh sản phẩm kiểm tra admin tại service và rollback cả batch khi lỗi. Xem [báo cáo batch ảnh](10-upload-anh-theo-batch.md). Đã bổ sung khóa chung khi xóa từng ảnh và dọn file sau commit trong [bài 11](11-xoa-anh-va-transaction.md). Đối soát file sau crash còn mở.

> Cập nhật 28/09/2026: đã mở rộng kiểm tra quyền xuống service hồ sơ/bình luận và chặn social ID chưa xác minh. Xem [báo cáo hồ sơ](08-quyen-ho-so-binh-luan.md). Đã bổ sung kiểm tra ảnh, đường dẫn và transaction thay avatar trong [báo cáo an toàn ảnh](09-an-toan-anh.md). Payment, đối soát file sau crash và vòng đời batch ảnh sản phẩm vẫn còn mở.

> Cập nhật 27/09/2026: phần order/order-detail đã triển khai theo [báo cáo mới](05-phan-quyen-don-hang.md). Phát hiện permitAll bên dưới là hiện trạng trước sửa. Rà soát user/comment/payment vẫn còn mở.

**Bằng chứng đọc code:** [WebSecurityConfig](../../src/main/java/com/manh/ecom_be/configurations/WebSecurityConfig.java) permitAll cho GET orders/** và order_details/**. [OrderController](../../src/main/java/com/manh/ecom_be/controllers/OrderController.java) có các đường đọc theo ID/userId và tìm kiếm chưa thể hiện đầy đủ ownership. Cần test cả filter để xác định response runtime chính xác.

**Việc làm**

- Lập danh sách endpoint từ controller, không chỉ sửa một matcher.
- Bỏ public access không phù hợp ở order/order detail; xác thực xong vẫn kiểm tra owner.
- Giới hạn danh sách theo user cho chính user đó hoặc admin. API tìm kiếm toàn bộ đơn chỉ dành cho quyền quản trị đã định.
- Đưa quy tắc ownership vào service/query dùng chung, tránh đường gọi khác bỏ qua.
- Trả 404 hoặc 403 theo hợp đồng khi sai owner; không lộ dữ liệu trước khi kiểm tra.
- Xử lý order không tồn tại trước mapping/truy cập thuộc tính; rà luồng hủy có thể gặp null.
- Rà các API comment, thông tin user, ảnh cá nhân và payment query/refund bằng cùng ma trận.

**Test chấp nhận**

| Tác nhân | Đọc đơn B | Liệt kê đơn B | Đọc detail đơn B | Tìm tất cả đơn |
| --- | --- | --- | --- | --- |
| Anonymous | 401 | 401 | 401 | 401 |
| USER A | Bị từ chối | Bị từ chối | Bị từ chối | Bị từ chối |
| USER B | Được phép | Chỉ dữ liệu B | Được phép | Theo endpoint riêng đã giới hạn |
| ADMIN | Theo quyền quản trị | Theo quyền quản trị | Theo quyền quản trị | Được phép nếu thiết kế |

- [ ] HTTP test dùng security filter thật, không chỉ mock service.
- [ ] Đổi userId trong request tạo đơn không đổi owner.
- [ ] ID không tồn tại không tạo 500 hoặc lộ thông tin.
- [ ] Luồng hợp lệ của chủ đơn vẫn chạy.

**Giải thích khi phỏng vấn:** tại sao JWT/role không đủ chống truy cập đối tượng của người khác.

## F02 — Tiền và tính nhất quán đơn hàng

> Cập nhật 27/09/2026: đã chuyển kiểu tiền đồng bộ, thêm V9 và regression test. Xem [báo cáo tiền thập phân](06-tien-thap-phan.md). Còn kiểm chứng MySQL và rà soft delete/refund.

**Bằng chứng trước sửa:** Product.price, Order.totalMoney, OrderDetail.price/totalMoney dùng Float; OrderService tính trung gian BigDecimal nhưng chuyển về float. Order đã có row lock, Redisson và test cạnh tranh, không cần lập mục “thêm chống oversell từ đầu”.

**Việc làm**

1. Chốt precision/scale và cách làm tròn cho tiền/giảm giá.
2. Chuyển đồng bộ entity, DTO, response, coupon calculation, email event và mapping sang kiểu tiền nhất quán. Không chỉ sửa phép cộng.
3. Thêm migration mới cho kiểu DECIMAL; kiểm tra schema hiện có và dữ liệu biên trước chuyển đổi. Không sửa V0–V8 đã áp dụng.
4. Giữ server tính giá và snapshot chi tiết đơn; test tổng giả từ client.
5. Rà mọi đường sửa tồn/chi tiết đơn, không cho bypass transaction của OrderService.
6. Phân biệt soft delete, cancel và refund: mỗi thao tác có quy tắc riêng, không tự hoàn kho hai lần.
7. Giữ thứ tự khóa ổn định, kiểm tra rollback và trạng thái kết thúc.

**Test chấp nhận**

- [ ] Tổng/giảm giá/làm tròn có kết quả định trước, không sai số float.
- [ ] Giỏ trùng dòng, số lượng sai, vượt tồn và sản phẩm mất đều được xử lý.
- [ ] Lỗi giữa chừng rollback order/detail/stock.
- [ ] 20 request trên kho 5 cho đúng 5 đơn, không tồn âm; test trên MySQL.
- [ ] Hủy đồng thời hoàn kho một lần; đường update/detail không phá invariant.
- [ ] Migration chạy trên DB trống và bản sao DB phiên bản trước với dữ liệu kiểm thử.

## F03 — Thanh toán: chọn phạm vi trước khi mở rộng

**Bằng chứng:** [VNPayService](../../src/main/java/com/manh/ecom_be/services/vnpay/VNPayService.java) nhận amount từ DTO và tự sinh reference; phần code đã đọc chưa có payment attempt gắn order/callback cập nhật paid. PaymentController đếm paymentsSuccess ngay khi tạo URL.

**Hai cách hoàn thành phù hợp**

- **Phạm vi COD:** demo luồng đặt hàng COD, ghi rõ VNPay là tích hợp thử nghiệm, không quảng bá đã thanh toán online end-to-end. Tắt/giới hạn endpoint tài chính chưa đủ kiểm soát trong môi trường public; metric phải phản ánh đúng tạo URL.
- **Phạm vi online:** làm đầy đủ các bước dưới đây trước khi tuyên bố hoàn tất.

**Nếu chọn online**

1. Tạo payment attempt liên kết order/user, amount/currency, reference duy nhất, trạng thái và thời điểm.
2. Tạo URL từ số tiền order phía server; kiểm tra ownership và trạng thái được thanh toán.
3. Triển khai nhận thông báo backend theo tài liệu VNPay chính thức tại thời điểm code: chữ ký, reference, amount, merchant và kết quả phải được đối chiếu.
4. Khóa/cập nhật nguyên tử để callback lặp không ghi nhận hai lần; lưu đủ thông tin để đối soát.
5. Tách payment status khỏi fulfillment status; paid không tự chuyển delivered.
6. Giới hạn query/refund theo quyền, số tiền và giao dịch hợp lệ; đặt timeout, xử lý upstream lỗi.
7. Đổi tên/định nghĩa metric: URL generated khác payment confirmed.

**Test chấp nhận**

- [ ] Sai owner/amount/reference/chữ ký không đổi order thành paid.
- [ ] Callback hợp lệ xác nhận một lần; callback lặp và cạnh tranh không nhân đôi tác dụng phụ.
- [ ] Từ chối refund trái quyền/vượt mức theo quy tắc đã định.
- [ ] Test giả lập tách biệt bằng chứng sandbox thật; không dùng response mock để khẳng định tương thích VNPay.
- [ ] Nếu chọn COD, tài liệu/demo/UI không gây hiểu lầm về thanh toán online.

## F04 — Token lifecycle và cấu hình trước demo public

> Cập nhật 03/10/2026: đã thống nhất CORS tại Spring Security với origin allowlist theo môi trường và sửa smoke/CI theo quyền metrics. Xem [bài 19](19-chot-pham-vi-demo-fresher.md). Reverse proxy và hạ tầng thực chưa được kiểm chứng.

> Cập nhật 02/10/2026: Actuator vận hành và healthcheck cũ yêu cầu ADMIN; chỉ public health tổng quát/liveness/readiness và ẩn chi tiết khỏi USER. Xem [bài 15](15-bao-ve-actuator.md), gồm cấu hình token scrape. Proxy/CORS và hạ tầng thực vẫn cần kiểm chứng.

> Cập nhật 28/09/2026: đã sửa refresh độc lập access token, rotation có khóa và thu hồi khi đổi mật khẩu/block. Xem [báo cáo mới](07-refresh-token.md). Các mô tả điểm rà dưới đây xuất phát từ trước sửa; cấu hình public và các phần còn mở chưa được coi là hoàn tất.

**Điểm rà:** [TokenService](../../src/main/java/com/manh/ecom_be/services/token/TokenService.java) hiện kiểm tra tồn tại/hạn và rotation; cần đọc cả caller/filter để kiểm tra đầy đủ revoked/expired/owner/active. Security config public actuator/**; profile prod tin forwarded headers theo framework.

**Việc làm**

- Test refresh đã thu hồi/hết hạn, token không thuộc user, user bị khóa, dùng lại token cũ và refresh đồng thời.
- Chốt thời hạn access/refresh và chính sách logout/đổi mật khẩu; tránh thay thời hạn mà không test client.
- Rà DTO đăng ký/cập nhật để user không tự cấp role hoặc sửa trường quản trị.
- Quy định endpoint actuator nào public; metrics và dashboard nên trong phạm vi quản trị/monitoring.
- Cấu hình CORS theo origin thực tế; xác định proxy nào được tin cậy trước khi lấy IP để rate limit.
- Biến môi trường/secrets nằm ngoài Git; startup cấu hình thiếu phải dễ chẩn đoán.
- Thử quota login/register sau proxy, và hành vi khi Redis lỗi.

**Hoàn thành khi:** test token/role/ownership có kết quả và cấu hình demo chỉ mở dịch vụ cần thiết. Đã có profile/rate limiting trong repo; nhiệm vụ là kiểm chứng và sửa thiếu sót, không tạo lại tính năng đã tồn tại.
