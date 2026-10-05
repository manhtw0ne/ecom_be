# 15 — Bảo vệ endpoint vận hành

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 02/10/2026

## Quyền truy cập

Trước đây `/api/v1/actuator/**` được permitAll và healthcheck cũ trả hostname. Hiện chỉ GET health tổng quát và hai probe được public; các endpoint vận hành còn lại yêu cầu ADMIN.

| Endpoint mặc định | Anonymous | USER | ADMIN |
| --- | --- | --- | --- |
| `/api/v1/actuator/health` | Status tổng quát | Status tổng quát | Có chi tiết |
| `/api/v1/actuator/health/liveness` | Status | Status | Có chi tiết nếu có |
| `/api/v1/actuator/health/readiness` | Status | Status | Có chi tiết |
| Actuator links, info, metrics, prometheus, health/db | 401 | 403 | Được phép |
| `/api/v1/healthcheck/health` cũ | 401 | 403 | Được phép |

Health dùng `show-details: when-authorized`, `show-components: when-authorized`, `roles: ADMIN`. Đăng nhập bằng user thường không đủ để xem hostname, thông tin Redis/Kafka hoặc database. Readiness DOWN vẫn trả 503 để hệ thống cân bằng tải ngừng chuyển request vào instance; nội dung nguyên nhân không công khai.

Security lấy base path từ `management.endpoints.web.base-path`, kết hợp EndpointRequest và bảo vệ toàn nhánh path. Endpoint không được expose không tự trở thành public. Cấu hình này chưa bao phủ triển khai management trên server/port riêng, reverse proxy hay các origin CORS.

## Prometheus sau thay đổi

Hai cấu hình Prometheus đọc Bearer token từ `/etc/prometheus/secrets/actuator.token`. Docker Compose mount `monitoring/secrets` read-only vào thư mục đó.

1. Đăng nhập tài khoản ADMIN hợp lệ để lấy access token theo luồng đăng nhập hiện có.
2. Lưu **chỉ chuỗi access token**, không có tiền tố `Bearer`, vào `monitoring/secrets/actuator.token` trên máy chạy demo. Không đưa token vào tài liệu, log hoặc Git; thư mục secret đã được ignore.
3. Với Prometheus chạy ngoài Compose, mount thư mục secret vào cùng đường dẫn hoặc chỉnh `credentials_file` phù hợp môi trường.
4. Kiểm tra target sau khi chạy; token hết hạn/thu hồi sẽ khiến scrape bị từ chối. Cần thay access token mới và kiểm tra target lại.

Chưa tạo token hay tài khoản tự động. Khi chưa cung cấp file/token hợp lệ, scrape không thành công. Đây là cấu hình demo sử dụng quyền ADMIN hiện có; chưa có tài khoản monitoring với quyền tối thiểu hoặc cơ chế tự làm mới credential. Không dùng token quản trị cá nhân lâu dài cho môi trường production.

## Kiểm thử

`ActuatorAuthorizationTest` kiểm tra anonymous/USER/ADMIN, health tổng quát/probes, details của ADMIN và readiness 503 không lộ nguyên nhân. Redis/Kafka health dùng contributor giả lập; HTTP security và Actuator chạy thật. Exporter được bật trong test. Chưa xác minh Docker scrape, Redis/Kafka/MySQL hoặc reverse proxy thật.

Kết quả 02/10/2026: `mvn -B clean verify` BUILD SUCCESS, **212 tests, 0 failures, 0 errors, 0 skipped**; gồm 12 ca Actuator mới. Coverage gate service pass. `docker compose config --quiet` pass; `git diff --check` không báo lỗi ở phạm vi thay đổi. `git check-ignore` xác nhận file token bị ignore. Log cuối: `target/actuator-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Tự học: phân biệt exposure (endpoint được tạo), authorization (ai truy cập), và show-details (nội dung trả về). Vì sao health 200 không đồng nghĩa mọi dependency đều hoạt động, và vì sao probe không nên phụ thuộc access token hết hạn?
