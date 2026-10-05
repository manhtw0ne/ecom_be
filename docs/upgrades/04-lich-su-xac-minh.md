# 04 — Hồ sơ xác minh lịch sử ngày 18/09/2026

> **Ghi nhận lịch sử, không phải kết quả kiểm chứng mới.** Nội dung bên dưới được giữ từ báo cáo cũ của dự án. Lần tổ chức tài liệu ngày 23/09/2026 không chạy lại các lệnh, không kiểm tra CI/GHCR từ xa và không xác nhận trạng thái dịch vụ hiện tại. Số test, coverage, log và trạng thái working tree bên dưới chỉ phản ánh báo cáo ngày 18/09/2026; các file target có thể không còn tồn tại.
>
> Các kết quả kiểm thử cũ không chứng minh đã hoàn thiện toàn bộ authorization hoặc thanh toán end-to-end. Xem [kế hoạch hiện tại](00-ke-hoach-fresher.md) và dùng [quy trình nghiệm thu](03-nghiem-thu-va-ho-so.md) để ghi bằng chứng mới.

Ngày kiểm chứng: **18/09/2026**, Windows, Java 21, Docker Desktop (Linux containers).

## Phạm vi đã hoàn tất local

Triển khai các nhiệm vụ sửa test và Phase 5/6 trong kế hoạch hoàn thiện cũ (đã hợp nhất vào nhóm upgrades): quota Redis/Bucket4j, quản lý tồn kho với Redisson và transaction, email đặt hàng sau commit, cache có phạm vi, Docker full stack, cấu hình môi trường, workflow CI/GHCR và README/Grafana. Đồng thời sửa kiểm tra mật khẩu, JWT nội bộ, tính giá đơn hàng và hoàn kho khi hủy.

| Kiểm chứng | Kết quả |
| --- | --- |
| `mvn verify -Pintegration` | BUILD SUCCESS; 88 unit/HTTP tests + 7 integration tests; 0 failures, errors, skipped |
| MySQL + Redis riêng cho integration test | Migration V0–V8 trên database mới; quota chia sẻ và cache roundtrip thành công |
| Đặt hàng đồng thời | 20 yêu cầu với tồn kho 5: đúng 5 đơn thành công, không tồn kho âm |
| Transaction đơn hàng | Rollback khi thiếu sản phẩm, gộp dòng trùng, tính giá server, hủy đồng thời hoàn kho một lần |
| Nghiệp vụ mở rộng | Đăng ký/vai trò, social login, đổi mật khẩu và thu hồi token, bình luận, chi tiết đơn và thumbnail được kiểm thử |
| VNPay local | HTTP server giả lập xác minh request/response, chữ ký URL, số tiền, hạn thanh toán và lỗi upstream; không gọi VNPay thật |
| Xác thực | Sai mật khẩu bị từ chối; JWT nội bộ truy cập endpoint bảo vệ thành công |
| Rate limit HTTP | Hết quota login/register trả 429 và Retry-After; spoof X-Forwarded-For không đổi quota |
| Cache | TTL sản phẩm 5 phút, danh mục 10 phút; invalidation sản phẩm không xóa key khác |
| Email | Unit test render/gửi qua mail sender giả lập; lỗi SMTP không làm hỏng đơn hàng |
| `docker compose build app` | Thành công; chạy verify trong builder; runtime không chạy root |
| Full stack | API, MySQL, Redis, Kafka, ZooKeeper, Prometheus và Grafana hoạt động |
| Health / Prometheus | HTTP 200, health UP, target scrape UP, business metrics xuất được |
| Smoke test HTTP | Đăng ký, đăng nhập, JWT, catalog, health, metrics và rate limit đều PASS |
| Dashboard | Sửa datasource, tên counter `ecom_orders_total`, bổ sung histogram tìm kiếm; có ảnh chụp thực tế |

Báo cáo máy sinh: `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`. Log xác minh local: `target/final-verify.log`, `target/final-docker-build.log`. Các thư mục target không commit.

## Coverage và các giới hạn còn lại

- **Coverage dòng toàn bộ service đạt 74,23% (602/811)** sau `mvn clean verify -Pintegration`, vượt mục tiêu ≥70%. JaCoCo `service-coverage` kiểm tra tổng các class trong `com/manh/ecom_be/services/**`, không loại bỏ service khó kiểm thử. Maven verify/Docker/CI sẽ thất bại nếu tỷ lệ xuống dưới 70%. Đây là coverage dòng, không phải coverage nhánh.
- Workflow CI và publish GHCR đã được viết, nhưng chưa có run GitHub xác minh các thay đổi này vì chưa commit/push. Badge xanh phải dựa trên run thực tế.
- SMTP thật, OAuth2 và VNPay cần thông tin môi trường riêng; chưa kiểm thử end-to-end với nhà cung cấp. Không cần cung cấp mật khẩu trong chat; cấu hình tại môi trường triển khai.
- Email sau commit là best-effort, chưa có outbox bền vững hay retry bảo đảm.
- Không migrate database production hiện hữu. Migration khởi tạo đã kiểm chứng trên database mới; database cũ cần đối chiếu Flyway history và sao lưu trước nâng cấp.

## Xem ứng dụng local

- API health: http://localhost:8080/api/v1/actuator/health
- Grafana: http://localhost:3000 — tài khoản demo local trong README.
- Prometheus: http://localhost:9090

![Dashboard thực tế](../images/grafana-dashboard.png)

Bản nâng cấp ở working tree; không tự tạo commit hoặc push lên remote.