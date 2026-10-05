# 19 — Đợt tổng hợp và điểm dừng cho bản demo fresher

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 03/10/2026

## Phạm vi bản demo

Java 21/Spring Boot monolith: tài khoản/JWT, catalog, ảnh, đơn COD, tồn kho và hủy đơn, cache, migration, kiểm thử, CI và monitoring có phân quyền. Đây là phạm vi có thể trình bày và tự bảo vệ ở mức fresher. Không cần thêm microservices, Kubernetes hoặc thêm broker để đủ điều kiện bắt đầu ứng tuyển.

VNPay mặc định không đăng ký controller. Muốn nghiên cứu lại cần bật `app.payments.vnpay.enabled=true`; các endpoint khi bật yêu cầu ADMIN, nhưng **chưa được coi là payment hoàn chỉnh**. Checkout chỉ hỗ trợ COD, thiếu/blank payment_method mặc định cod; phương thức khác và vnp_txn_ref client tự gửi bị từ chối trước khi trừ kho. Bật controller không mở checkout VNPay. Không đưa “thanh toán VNPay end-to-end” vào CV.

## Những phần hoàn tất trong đợt này

1. **CORS:** bỏ filter trả wildcard và cấu hình MVC trùng. Spring Security dùng một policy, xử lý preflight trước authentication, gồm PATCH và header phân trang/rate-limit. Origin không nằm trong allowlist bị từ chối; request không có Origin vẫn theo security bình thường. CORS không thay authentication.
2. **Môi trường:** profile prod đọc `CORS_ALLOWED_ORIGINS`, mặc định rỗng để không tự mở cross-origin. Compose local cấp ba origin localhost phổ biến; deployment thực phải cấu hình origin frontend chính xác. Wildcard bị từ chối khi khởi tạo policy.
3. **Phân trang:** API sản phẩm và tìm kiếm đơn admin giới hạn page 0..100000, limit 1..100, keyword tối đa 200 ký tự; category_id của catalog không âm. Giới hạn trước khi gọi cache/DB.
4. **Cache:** chuyển value từ mảng sang ProductListResponse. Cache trang rỗng vẫn giữ totalPages. Prefix `products:page-v3:` tránh đọc schema cache cũ; key cũ tự hết TTL. Cache hỏng được coi là miss; Redis lỗi không chặn catalog. TTL vẫn 5 phút, invalidation vẫn sau commit.
5. **CI/smoke:** bỏ curl anonymous metrics vốn không còn hợp lệ. Smoke kiểm tra anonymous 401, buyer 403, refresh rotation và chặn replay. Scrape metrics có token chỉ chạy khi truyền `-AdminTokenFile`; nếu thiếu sẽ in SKIP rõ ràng.
6. **Demo:** [bộ HTTP request](../demo/fresher.http) gồm health, login, catalog, tạo/hủy đơn, snapshot, refresh và negative cases. Thay biến bằng dữ liệu local riêng; không commit token thật. Role USER=1 là giả định seed hiện có; kiểm tra trước nếu database tùy biến.

## Cách nghiệm thu còn lại

Chạy theo thứ tự ở root repository, PowerShell 7, Docker engine đang chạy:

```powershell
mvn -B clean verify
docker compose -f docker-compose.test.yml up -d --wait --wait-timeout 180
mvn -B verify -Pintegration
docker compose up -d --build --wait --wait-timeout 300
pwsh -File scripts/smoke-test.ps1
# Kiểm chứng thêm metrics thật nếu có token ADMIN đã đăng nhập hợp lệ:
pwsh -File scripts/smoke-test.ps1 -AdminTokenFile monitoring/secrets/actuator.token
```

Smoke tạo tài khoản và tiêu hao quota login: dùng stack test riêng, đợi quota hồi trước khi chạy lại. Không chạy hai dòng smoke liên tiếp trên cùng quota nếu chưa đợi. Sau demo có thể dùng `docker compose down` và `docker compose -f docker-compose.test.yml down`; không xóa volume dữ liệu thật.

Docker engine local hiện không kết nối được (pipe dockerDesktopLinuxEngine không tồn tại). Vì vậy chưa có bằng chứng migration/lock MySQL, Redis thật, startup full stack hay scrape; không gọi những mục này là pass. CI đã được cập nhật nhưng chưa có run remote của thay đổi này.

## Kết quả xác minh đợt này

- `mvn -B verify`: **253 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS; tăng 16 ca so với baseline 237. Đã chạy clean ở đầu đợt; lỗi khởi tạo do tên bean CORS đã sửa trước lần verify thành công.
- Coverage dòng service **725/894 = 81,10%**, vượt gate 70%.
- Sau cùng cập nhật câu mô tả OpenAPI về COD và chạy `mvn -B -DskipTests package` để cập nhật artifact; không thay logic đã kiểm thử.
- `docker compose config --quiet`, PowerShell AST parser cho smoke và `git diff --check` đều pass. Đây là kiểm tra cấu hình/cú pháp, không thay thế chạy full stack.
- Log: `target/fresher-batch-verify.log`, `target/fresher-package.log`; chi tiết ở `target/surefire-reports` và `target/site/jacoco/index.html`.

## Điểm dừng và việc không thể giao hết cho code

| Mục | Điều kiện để chốt |
| --- | --- |
| Local correctness | Toàn bộ verify pass; giữ các regression về quyền, tiền, tồn kho và HTTP |
| Hạ tầng | Một lần integration MySQL/Redis + full-stack smoke pass, lưu log/commit |
| Demo | Tự chạy bộ request, giải thích checkout/rollback/refresh/caching trong 10–15 phút |
| Hiệu năng | Đã có phép đo SQL trước/sau catalog ở [bài 20](20-do-query-catalog.md); latency/EXPLAIN MySQL chưa đo |
| Hồ sơ | CV mô tả đúng COD và phần đã kiểm chứng, không dùng test count để khẳng định production-ready |

Sau các mục này, ưu tiên luyện SQL/Java/Spring và tự giải thích code thay vì tiếp tục thêm tính năng. Outbox, idempotency, bản sao ảnh lịch sử, account monitoring riêng và payment online là backlog nâng cao. Code đã có nhiều phần để học; khả năng tự sửa một lỗi và giải thích trade-off là phần bạn cần trực tiếp luyện.
