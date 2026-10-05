# 02 — P1: chất lượng API và đo lường; P2: mở rộng có chọn lọc

[Tổng kế hoạch](00-ke-hoach-fresher.md) · Trước: [P0](01-p0-tinh-dung-va-bao-mat.md) · Tiếp: [Nghiệm thu](03-nghiem-thu-va-ho-so.md)

Backlog gốc bên dưới được giữ để đối chiếu. Tiến độ mới: API danh mục và lỗi request ở bài 17–18; CORS, giới hạn catalog, cache metadata, CI/smoke và bộ request đã được bổ sung trong [bài 19](19-chot-pham-vi-demo-fresher.md). Benchmark và xác minh hạ tầng vẫn chưa hoàn tất.

## F05 — Hợp đồng API dễ dùng và nhất quán

### Validation và DTO

1. Lập bảng endpoint → DTO → constraint → lỗi mong đợi.
2. Tách DTO create/update/status/payment nếu trường bắt buộc khác nhau; tránh client gửi trường không được sửa.
3. Kiểm tra cả annotation lẫn @Valid tại controller, validation lồng cho cart item, null/rỗng/độ dài/miền giá trị.
4. Bổ sung page/limit/sort validation; whitelist sort và giới hạn page size.
5. Rà amount phép nhân/chuyển đơn vị và quantity cộng dồn để tránh tràn số.

**Nghiệm thu:** có HTTP test dữ liệu sai và ca biên, không chỉ test DTO được khởi tạo. Không trả stacktrace/exception nội bộ cho client.

### Status, response và lỗi

- Chuẩn hóa HTTP status với body; create thành công dùng hợp đồng đã chọn.
- Gom xử lý exception chung; phân biệt lỗi dữ liệu, quyền, không tồn tại, xung đột và lỗi ngoài dự kiến.
- Trả DTO/response thay entity ở những đường có nguy cơ lộ dữ liệu hoặc lazy serialization.
- Sửa trường hợp ID không tồn tại trước khi mapping.
- Phân trang có metadata nhất quán giữa endpoint và giữa cache hit/miss.

Điểm cụ thể để bắt đầu: CategoryController nhận page/limit nhưng lấy toàn bộ categories; createCategory trả HTTP 200 với body created. Chọn triển khai phân trang thật hoặc bỏ tham số khỏi hợp đồng, rồi kiểm tra client liên quan.

**Nghiệm thu:** danh sách rỗng, trang cuối, filter kết hợp và ID sai có expected response cố định. Không đổi schema response mà quên cập nhật ví dụ/test.

### OpenAPI và bộ request demo

- Dùng OpenApiConfig đã có; bổ sung mô tả request/response và lỗi quan trọng, JWT scheme.
- Ghi rõ endpoint public, USER, ADMIN và ownership.
- Tạo collection hoặc file HTTP request dùng biến baseUrl/token, không chứa token thật.
- Đối chiếu OpenAPI với response chạy thực tế, đặc biệt status và pagination.
- Swagger hiện tắt ở prod; nếu cần demo qua Swagger, dùng môi trường demo với phạm vi được kiểm soát.

**Nghiệm thu:** người khác chạy được đăng ký → login → xem catalog → tạo đơn → xem đơn của mình → hủy hợp lệ bằng tài liệu, không cần hỏi tác giả từng bước.

## F06 — Đo query/cache trước khi tối ưu

**Bộ dữ liệu:** ghi số category/product/order/detail, cách seed, index, cấu hình máy/container và cache nóng/lạnh. Dữ liệu phải đủ làm lộ N+1 nhưng không cần giả lập hàng triệu bản ghi để có giá trị học tập.

**Trình tự**

1. Chọn hai endpoint: danh sách sản phẩm kèm category và danh sách đơn kèm detail.
2. Đếm SQL/response; ghi query chính và EXPLAIN.
3. Đo latency bằng cùng tải, thời lượng, warm-up và môi trường; báo cả error rate.
4. Nếu N+1, thử projection/entity graph/fetch phù hợp. Không join-fetch collection có phân trang mà bỏ qua tác động tới kết quả.
5. Chỉ thêm index phục vụ query đã đo; ghi trade-off với ghi dữ liệu.
6. Với cache, đo DB query còn lại khi hit; kiểm tra metadata và lỗi Redis.
7. Test sau sửa để giữ nguyên kết quả nghiệp vụ.

**Bằng chứng hoàn thành**

| Chỉ số | Trước | Sau | Điều kiện đo |
| --- | --- | --- | --- |
| Query/request | Điền số thật | Điền số thật | Endpoint/dataset |
| p50/p95 và error rate | Điền số thật | Điền số thật | Warm-up/tải/thời lượng |
| Kết quả API | Snapshot mong đợi | Vẫn đúng | Filter/page/cache |

Không đặt sẵn “p95 < 500ms” hay tuyên bố nhanh hơn nếu chưa có phép đo. Screenshot Grafana minh họa không thay một phép thử có điều kiện tái lập.

## F07 — CI, vận hành và hồ sơ

Workflow/Docker/monitoring đã tồn tại; công việc là chạy xác minh và xử lý lỗi thực tế.

- Xác nhận verify/integration/smoke chạy trên đúng commit.
- Xác nhận GHCR publish nếu chọn dùng image; lưu tag theo commit.
- Thử checkout sạch theo hướng dẫn, ghi dependency/môi trường còn thiếu.
- Nếu triển khai public: TLS, persistence, quyền endpoint, secrets, health và cách khôi phục phiên bản trước phải có.
- Chọn hạ tầng sau khi đánh giá tài nguyên và ngân sách. Không mặc định nhà cung cấp nào đang miễn phí hoặc hỗ trợ đủ MySQL/Redis/Kafka.
- Không bắt buộc cloud nếu chưa có thời gian/ngân sách; chuẩn bị demo local/video trung thực.

Nghiệm thu chi tiết ở [file 03](03-nghiem-thu-va-ho-so.md).

## F08 — Chỉ chọn một bài mở rộng sau khi lõi ổn

### A. Idempotency tạo đơn

**Vấn đề:** timeout khiến client gửi lại, tạo hai đơn dù không oversell.

**Thiết kế:** key theo user, unique constraint, fingerprint payload và lưu kết quả; cùng key khác payload bị từ chối. Thời hạn giữ key phải được công bố.

**Test:** hai request đồng thời cùng key chỉ có một đơn; retry nhận kết quả cũ; lỗi giữa chừng không để trạng thái kẹt không xử lý được.

### B. Outbox cho email/sự kiện

**Vấn đề:** DB commit thành công nhưng process chết trước gửi email/Kafka.

**Thiết kế:** lưu outbox cùng transaction với order; worker đọc, retry có giới hạn/backoff, theo dõi lỗi; tác dụng phụ/consumer cần xử lý trùng.

**Test:** dừng sau commit rồi chạy worker lại không mất sự kiện; gửi lặp không nhân đôi tác dụng phụ ngoài quy tắc.

Không tuyên bố exactly-once chỉ vì có outbox; phải giải thích cửa sổ gửi thành công nhưng chưa đánh dấu đã gửi. Mục tiêu của F08 là hiểu một bài toán sâu, không thêm cả hai cho đủ công nghệ.

## Những việc chủ động hoãn

Microservices, Kubernetes, search engine riêng, CQRS/event sourcing và nhiều message broker. Chỉ đưa vào backlog khi có yêu cầu đo được mà thiết kế hiện tại chưa đáp ứng.
