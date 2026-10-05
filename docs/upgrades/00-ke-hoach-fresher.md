# Nhóm 2 — Kế hoạch nâng cấp Ecom Backend phục vụ ứng tuyển fresher

## Mục tiêu và cách dùng

Hoàn thiện một dự án bạn hiểu, có nghiệp vụ đúng, kiểm thử tái lập và trình bày trung thực. Đây là kế hoạch cho repository hiện tại; nếu muốn tự xây từ đầu, theo [nhóm học](../learning/00-lo-trinh.md).

Không có danh sách công nghệ bảo đảm đỗ fresher. Ưu tiên khả năng giải thích Java/Spring/SQL, quyền truy cập, transaction, kiểm thử và cách xử lý lỗi. Không thêm microservices/Kubernetes chỉ để tăng số từ khóa CV.

Đọc theo thứ tự:

1. **File này:** hiện trạng, phạm vi, ưu tiên.
2. [01 — P0: tính đúng và bảo mật](01-p0-tinh-dung-va-bao-mat.md).
3. [02 — P1/P2: chất lượng và mở rộng](02-p1-chat-luong-va-mo-rong.md).
4. [03 — Nghiệm thu, demo và hồ sơ](03-nghiem-thu-va-ho-so.md).
5. [04 — Xác minh lịch sử](04-lich-su-xac-minh.md), chỉ dùng làm ngữ cảnh.

## Tiến độ ngày 03/10/2026

Đã đo và sửa N+1 của catalog trên fixture H2, có test ngân sách query tính cả JSON serialization. Xem [bài 20](20-do-query-catalog.md). Không coi đây là benchmark latency MySQL.

Đợt tổng hợp: thống nhất CORS, giới hạn catalog/tìm kiếm đơn, sửa cache metadata trang rỗng, sửa CI/smoke theo quyền metrics và chốt demo COD. Xem [bài 19 — Phạm vi và điểm dừng](19-chot-pham-vi-demo-fresher.md) cùng [bộ request demo](../demo/fresher.http). Hạ tầng thật và benchmark vẫn cần bằng chứng riêng.

Đã phân loại lỗi request thành 400/404/415, bổ sung Allow cho 405 và tránh trả thông tin parser ra client. Xem [bài 18](18-hop-dong-loi-request.md).

## Tiến độ ngày 02/10/2026

Đã phân trang danh mục tại database, chuẩn hóa 404/409 và chặn xóa danh mục còn sản phẩm ẩn. GET danh sách không còn gửi Kafka. Xem [bài 17](17-api-danh-muc.md) về header phân trang và tương thích client.

Đã bổ sung quyền ADMIN tại service tạo/sửa sản phẩm và ghi danh mục, kèm kiểm thử gọi service trực tiếp. Xem [bài 16](16-quyen-ghi-catalog.md).

Đã giới hạn Actuator/healthcheck vận hành cho ADMIN, public ba health probe với nội dung tổng quát và cấu hình Prometheus đọc token ngoài Git. Xem [bài 15](15-bao-ve-actuator.md), gồm bước cấp token cần làm khi chạy demo và giới hạn kiểm chứng.

## Tiến độ ngày 30/09/2026

Đã lưu snapshot tên/đường dẫn ảnh khi checkout và dùng trong DTO chi tiết đơn, kèm migration V11 backfill dữ liệu hiện có. Xem [bài 14](14-snapshot-san-pham-trong-don.md). Chưa tách hoàn toàn quan hệ Product hoặc lưu bản sao file ảnh lịch sử.

## Tiến độ ngày 29/09/2026

Đã sửa DELETE sản phẩm sang đánh dấu ẩn không cascade REMOVE, giữ dữ liệu ảnh và chặn xóa sản phẩm có chi tiết đơn hàng tham chiếu. Xem [bài 13](13-xoa-mem-san-pham.md). Snapshot lịch sử đơn hàng, trạng thái ngừng bán và xóa vĩnh viễn vẫn còn mở.

Đã chặn thumbnail tùy ý khi tạo và kiểm tra ảnh thuộc đúng sản phẩm khi sửa, cùng khóa với upload/xóa ảnh. Xem [bài 12](12-thumbnail-thuoc-san-pham.md). Dữ liệu legacy và đối soát file vẫn cần xử lý riêng.

Đã đồng bộ xóa ảnh với upload bằng khóa sản phẩm, kiểm tra admin tại service và dọn file sau commit. Xem [báo cáo xóa ảnh](11-xoa-anh-va-transaction.md). Dọn file sau crash và xóa toàn sản phẩm còn mở; thumbnail được xử lý tiếp ở bài 12.

Đã chuyển upload ảnh sản phẩm sang transaction cho cả batch, dọn file khi rollback và khóa sản phẩm để kiểm tra giới hạn 5 ảnh giữa các batch đồng thời. Xem [báo cáo batch ảnh](10-upload-anh-theo-batch.md); tiến độ đồng bộ với xóa ảnh nằm ở bài 11 phía trên.

## Tiến độ ngày 28/09/2026

Đã kiểm tra nội dung PNG/JPEG, giới hạn đường dẫn đọc/xóa và xử lý commit/rollback khi thay avatar. Xem [báo cáo an toàn ảnh](09-an-toan-anh.md), gồm thay đổi tương thích và giới hạn batch ảnh sản phẩm.

Đã bổ sung quyền service cho profile/comment, rút gọn dữ liệu tác giả và chặn social ID tự khai báo. Xem [báo cáo hồ sơ và bình luận](08-quyen-ho-so-binh-luan.md).

Đã nâng cấp rotation/thu hồi refresh token trong F04, giữ thời hạn phiên tuyệt đối và thêm migration V10. Xem [báo cáo refresh token](07-refresh-token.md). Rà quyền các nhóm dữ liệu khác và kiểm chứng MySQL vẫn còn mở.

## Tiến độ ngày 27/09/2026

Đã triển khai phần quyền đơn hàng của F01 và chặn đường ghi chi tiết đơn bỏ qua tồn kho. Xem [báo cáo và hợp đồng API](05-phan-quyen-don-hang.md). F00 có baseline local; integration MySQL/Redis còn chờ Docker. Các mục còn lại chưa được coi là hoàn tất.

Phần tiền của F02 đã chuyển sang BigDecimal/DECIMAL(19,2), có migration V9 và test tiền/coupon/cache. Xem [quy tắc và kiểm chứng](06-tien-thap-phan.md). Migration MySQL thực tế vẫn chờ xác minh.

## Hiện trạng đối chiếu ngày 23/09/2026

“Có code” khác “đã chạy lại”, “đã chạy local” khác “CI/deploy đã xác minh”. Lần chỉnh tài liệu này chỉ đọc code và tài liệu sẵn có.

| Hạng mục | Bằng chứng trong repo | Cách xử lý tiếp |
| --- | --- | --- |
| Java 21/Spring Boot 3.3.5 | [pom.xml](../../pom.xml) | Giữ nền tảng để học; đánh giá cập nhật dependency riêng |
| Profile dev/prod/test | [resources](../../src/main/resources), [test profile](../../src/test/resources/application-test.yml) | Đã có, không lập việc “tạo mới”; kiểm tra cấu hình triển khai |
| Flyway V0–V8 | [migrations](../../src/main/resources/db/migration) | Test trên DB mới; DB cũ cần kế hoạch riêng |
| Catalog, validation, lỗi | Controller/DTO/GlobalExceptionHandler | Đã có một phần; chuẩn hóa hợp đồng và ca biên |
| JWT, refresh, social login | User/Token/Auth services | Rà ownership, refresh lifecycle và test HTTP |
| Tồn kho, row lock, Redisson | OrderService/ProductRepository | Giữ regression test; rà mọi đường ghi |
| Redis/Bucket4j/email | Config, service và test tương ứng | Đã có; kiểm chứng lỗi phụ thuộc, không viết lại theo checklist cũ |
| Docker, CI/GHCR | Dockerfile/Compose/workflow | Có cấu hình; chưa xác nhận run remote mới |
| Prometheus/Grafana | [monitoring](../../monitoring) | Kiểm tra ý nghĩa metric và quyền truy cập |
| VNPay | PaymentController/VNPayService | Có tạo URL/query/refund; thiếu luồng xác nhận gắn order trong phần code đã đọc |
| Test/coverage | pom và src/test | Báo cáo cũ ngày 18/09; phải chạy mới để tuyên bố kết quả hiện tại |

## Backlog và thứ tự phụ thuộc

Bảng dưới là backlog gốc. Tiến độ mới nhất nằm ở mục cập nhật 27/09/2026 và báo cáo liên kết; F01 mới hoàn tất phần đơn hàng, chưa đóng toàn bộ mục.

| ID | Ưu tiên | Việc | Phụ thuộc | Kết quả cần có |
| --- | --- | --- | --- | --- |
| F00 | P0 | Chạy baseline, lưu môi trường/commit/báo cáo | Không | Phân biệt lỗi sẵn có với lỗi mới |
| F01 | P0 | Quyền đọc/ghi theo owner và role | F00 | Ma trận quyền, HTTP regression tests |
| F02 | P0 | Tiền và vòng đời đơn hàng | F00 | Kiểu tiền nhất quán, test rollback/cạnh tranh |
| F03 | P0 nếu demo thanh toán online | Hoàn thiện payment hoặc giới hạn demo COD | F01, F02 | Không nhận nhầm “tạo URL” là “đã trả tiền” |
| F04 | P0 trước demo public | Refresh lifecycle, endpoint nội bộ, môi trường | F01 | Test token/role và cấu hình công khai có kiểm soát |
| F05 | P1 | Validation, lỗi, phân trang, OpenAPI | F01–F04 theo phạm vi | Hợp đồng API nhất quán và ca biên |
| F06 | P1 | Đo query/cache và sửa điểm nghẽn có bằng chứng | F02, F05 | Số liệu trước/sau tái lập |
| F07 | P1 | CI, demo sạch, hồ sơ dự án | F00–F06 | Run thực tế, hướng dẫn/demo và giới hạn |
| F08 | P2, chọn một | Outbox hoặc idempotency tạo đơn | Lõi ổn định | Một bài toán nâng cao hiểu sâu |

**Điểm dừng hợp lý:** F00–F05 cùng F07 ổn định, F06 có ít nhất một phép đo hữu ích. F08 là tùy chọn. Cloud demo không bắt buộc để bắt đầu ứng tuyển; local demo tái lập được vẫn là đầu ra có giá trị.

## Nhịp thực hiện gợi ý

- **Chặng 1, khoảng 1 tuần bán thời gian:** baseline và F01; học lại bài 03 song song.
- **Chặng 2, 1–2 tuần:** F02, chốt phạm vi F03, F04; học bài 04.
- **Chặng 3, khoảng 1 tuần:** F05/F06 với test và đo trước/sau.
- **Chặng 4, khoảng 1 tuần:** F07, luyện demo/phỏng vấn; chỉ chọn F08 nếu còn thời gian.

Nếu chưa giải thích được một phần, dành thêm thời gian học thay vì ép mốc. Không dùng lịch 3–5 ngày như cam kết cho người đang học lại nền tảng.

## Quy tắc đóng một mục

1. Mô tả bug/yêu cầu bằng ví dụ tái hiện.
2. Viết tiêu chí chấp nhận và test phù hợp.
3. Sửa trong phạm vi nhỏ; migration dữ liệu phải có kế hoạch.
4. Chạy kiểm tra, ghi commit/môi trường/kết quả/giới hạn.
5. Tự giải thích thiết kế và ít nhất một phương án thay thế.

Mục chỉ có code hoặc test chưa chạy vẫn chưa hoàn tất. Không dùng số test, coverage hoặc nhãn “production-ready” thay bằng chứng.
