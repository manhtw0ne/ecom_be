# 05 — Cache, email và các tích hợp bên ngoài

[Lộ trình](00-lo-trinh.md) · Trước: [Đơn hàng](04-don-hang-va-ton-kho.md) · Tiếp: [Kiểm thử/vận hành](06-kiem-thu-va-van-hanh.md)

## Cách học

Chỉ bắt đầu khi catalog, quyền truy cập và đặt hàng đã có test. Thêm từng tích hợp, ghi: vấn đề cần giải quyết, nguồn dữ liệu đúng, điều gì xảy ra khi tích hợp lỗi. Không bật tất cả cùng lúc.

## 1. Redis cache: tối ưu đường đọc

Tham chiếu [ProductRedisService](../../src/main/java/com/manh/ecom_be/services/product/ProductRedisService.java), [ProductListener](../../src/main/java/com/manh/ecom_be/models/ProductListener.java), [TransactionCallbacks](../../src/main/java/com/manh/ecom_be/components/TransactionCallbacks.java).

1. Đo số query/thời gian API catalog khi chưa cache với cùng bộ dữ liệu.
2. Tạo key từ toàn bộ tham số ảnh hưởng kết quả: page, size, keyword, category, sort.
3. Cache miss đọc DB rồi lưu; cache hit đọc cache.
4. Dùng TTL có lý do. Repo hiện đặt cache sản phẩm 5 phút; cache category 10 phút trong RedisConfig.
5. Invalidate sau commit khi sửa sản phẩm; chỉ xóa namespace liên quan. Không dùng FLUSHALL để làm mới catalog.
6. Test thay đổi sản phẩm rồi đọc lại, rollback, hết TTL, Redis lỗi và giữ nguyên key không liên quan.

**Bài tập:** cache hai trang khác nhau, sửa một sản phẩm rồi đọc cả hai. Ghi vì sao key thiếu sort/filter có thể trả sai dữ liệu.

After-commit invalidation và TTL giúp giảm dữ liệu cũ nhưng không tự chứng minh cache nhất quán tuyệt đối: request đọc song song có thể nạp lại dữ liệu cũ. Checkout phải kiểm tra giá/tồn từ DB, không lấy cache làm nguồn quyết định bán hàng. Đọc cách controller dùng cache cùng metadata để xác định truy vấn nào thực sự được tránh.

## 2. Email: tác dụng phụ sau commit

Tham chiếu [OrderPlaced](../../src/main/java/com/manh/ecom_be/services/email/OrderPlaced.java), [EmailService](../../src/main/java/com/manh/ecom_be/services/email/EmailService.java), [AsyncConfig](../../src/main/java/com/manh/ecom_be/configurations/AsyncConfig.java).

1. Khi tạo đơn, phát event chứa dữ liệu cần gửi, tránh truyền cả entity lazy sang luồng khác.
2. Listener chạy sau commit; dùng executor có giới hạn.
3. SMTP lỗi không rollback đơn đã lưu; log trạng thái để chẩn đoán.
4. Test đơn rollback không phát tác dụng phụ sau commit; giả lập mail sender thất bại.
5. Chỉ thử SMTP thật khi đã có cấu hình riêng; test mock không chứng minh thư đến hộp thư thật.

Hiện email mặc định tắt và cơ chế gửi là best-effort. Process chết sau commit có thể mất email. Outbox là bài mở rộng khi bạn muốn lưu sự kiện bền vững, không phải điều kiện để học xong Spring.

## 3. Rate limit: Redis có trách nhiệm khác cache

Đọc [RateLimitInterceptor](../../src/main/java/com/manh/ecom_be/components/RateLimitInterceptor.java) và [RateLimitConfig](../../src/main/java/com/manh/ecom_be/configurations/RateLimitConfig.java).

1. Xác định endpoint, định danh khách và quota.
2. Thử vượt quota, kiểm tra 429 và Retry-After.
3. Test hai instance dùng chung quota, không chỉ một counter trong bộ nhớ.
4. Định nghĩa hành vi khi Redis lỗi; fail-open tăng tính sẵn sàng, fail-closed bảo vệ quota nhưng có thể chặn người dùng.
5. Nếu có reverse proxy, chỉ tin thông tin IP từ proxy đã cấu hình tin cậy.

Cache được phép bỏ qua khi lỗi không có nghĩa rate limit hoặc stock lock cũng xử lý lỗi như vậy. Đánh giá từng trách nhiệm riêng.

## 4. Kafka: học sau khi email nội bộ đã hiểu

Hiện [CategoryController](../../src/main/java/com/manh/ecom_be/controllers/CategoryController.java) gửi topic insert-a-category và get-all-categories; [MyKafkaListener](../../src/main/java/com/manh/ecom_be/components/MyKafkaListener.java) nhận sự kiện. Đây là tích hợp minh họa, không phải bằng chứng có luồng nghiệp vụ bền vững.

Trong bản học:

1. Chọn một sự kiện có người dùng thực sự, ví dụ phục vụ tác vụ nền sau đặt hàng.
2. Định nghĩa eventId, loại sự kiện, thời điểm và payload tối thiểu.
3. Phân biệt producer gửi, broker nhận và consumer xử lý xong.
4. Gửi trùng sự kiện; consumer không được áp dụng tác dụng phụ hai lần.
5. Thử consumer lỗi rồi chạy lại; ghi retry/offset và nơi xử lý sự kiện thất bại.

DB commit rồi Kafka send có thể thất bại; send thành công nhưng DB rollback cũng là vấn đề nếu bố trí sai. Học outbox khi cần giải quyết khoảng trống này. Không thêm Kafka vào mọi API GET chỉ để thể hiện công nghệ.

## 5. OAuth: bổ sung sau login nội bộ

Đọc [AuthService](../../src/main/java/com/manh/ecom_be/services/auth/AuthService.java) và callback trong [UserController](../../src/main/java/com/manh/ecom_be/controllers/UserController.java).

Bản học cần mô tả: tạo authorization URL → nhà cung cấp trả code → backend đổi code → lấy danh tính đã xác minh → tìm/tạo liên kết tài khoản → cấp token nội bộ.

Ghi và kiểm thử state/redirect URI, code không hợp lệ, nhà cung cấp lỗi, và quy tắc liên kết tài khoản. Đừng tự động coi email do client gửi là danh tính đã được nhà cung cấp xác nhận. Test mock cần tách khỏi lần demo với tài khoản nhà cung cấp thật.

## 6. VNPay: tách cái hiện có và cái cần xây

[VNPayService](../../src/main/java/com/manh/ecom_be/services/vnpay/VNPayService.java) hiện tạo URL ký HMAC-SHA512, truy vấn và gửi yêu cầu hoàn tiền. [PaymentController](../../src/main/java/com/manh/ecom_be/controllers/PaymentController.java) chưa có callback/IPN xác nhận thanh toán gắn với đơn. Tạo URL thành công chưa có nghĩa đã thanh toán.

Trình tự cho **bản học cần xây thêm**:

1. Lấy order thuộc user từ DB và tính số tiền phải trả từ order; không tin amount tùy ý của client.
2. Tạo bản ghi payment attempt liên kết order, transaction reference duy nhất, số tiền và trạng thái chờ.
3. Tạo URL theo hợp đồng nhà cung cấp; dùng sandbox khi tích hợp thật.
4. Nhận kết quả ở backend, xác minh chữ ký, mã giao dịch, số tiền và các trường bắt buộc theo tài liệu nhà cung cấp tại thời điểm triển khai.
5. Xử lý callback lặp một cách nguyên tử; kết quả sai không đổi trạng thái thành paid.
6. Tách payment status khỏi trạng thái giao hàng: paid không có nghĩa shipped/delivered.

Test chữ ký sai, amount sai, reference lạ, callback lặp, timeout. Khi triển khai giao thức thật, đối chiếu tài liệu VNPay chính thức thay vì suy ra từ test giả lập hiện tại. Nếu chưa hoàn thiện được, demo COD và công bố VNPay ở mức thử nghiệm.

## Nghiệm thu và tự vấn

- [ ] Mỗi tích hợp có một ca lỗi được kiểm chứng.
- [ ] Cache không làm thay nguồn quyết định giá/tồn kho.
- [ ] Email rollback/SMTP lỗi có hành vi rõ.
- [ ] Giải thích DB commit khác Kafka ack và email delivered.
- [ ] Phân biệt test giả lập với end-to-end nhà cung cấp.

**Câu hỏi:** vì sao cần TTL dù đã invalidate? Async khác message queue? At-least-once khiến consumer cần gì? Refresh token nội bộ khác access token của Google thế nào? Vì sao không tin trang redirect của trình duyệt để tự đánh dấu paid?
