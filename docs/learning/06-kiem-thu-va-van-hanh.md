# 06 — Kiểm thử, Docker, CI và vận hành

[Lộ trình](00-lo-trinh.md) · Trước: [Tích hợp](05-cache-va-tich-hop.md) · Tra cứu: [Bản đồ code](07-ban-do-ma-nguon.md)

## 1. Chọn test theo điều cần chứng minh

| Loại | Dùng cho | Không chứng minh được |
| --- | --- | --- |
| Unit, JUnit/Mockito | Tính tiền, nhánh lỗi, quy tắc trạng thái | SQL, transaction/lock thật |
| HTTP/controller | Binding, validation, status, authorization | Toàn bộ hạ tầng nếu đang mock |
| Integration MySQL/Redis | Migration, row lock, cache, quota chia sẻ | Nhà cung cấp bên ngoài chưa kết nối |
| Smoke toàn stack | App khởi động và luồng chính hoạt động | Mọi ca biên hoặc độ chịu tải |

Đọc [ProductControllerTest](../../src/test/java/com/manh/ecom_be/controllers/ProductControllerTest.java), [OrderConcurrencyTest](../../src/test/java/com/manh/ecom_be/services/orders/OrderConcurrencyTest.java), [InfrastructureIT](../../src/test/java/com/manh/ecom_be/services/orders/InfrastructureIT.java). Test tên Test có thể vẫn khởi tạo Spring context/H2; đừng gọi toàn bộ nhóm đó là unit thuần.

Viết Given/When/Then rõ ràng. Test kết quả và trạng thái dữ liệu; tránh chỉ kiểm tra method được gọi nếu hành vi nghiệp vụ mới là điều cần chứng minh. Sửa code gây lỗi thử để chắc test bắt được lỗi đó.

## 2. Chạy kiểm tra repository tham chiếu

Chạy từ root repo trong PowerShell, cần JDK 21; Maven wrapper có thể cần tải dependency lần đầu.

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Đọc `target/surefire-reports` và `target/site/jacoco/index.html`. Repo cấu hình JaCoCo gate 70% line coverage nhóm service trong pom. Coverage đo code được đi qua, không chứng minh assertion đủ mạnh; không xóa test/loại class khó để làm đẹp tỷ lệ.

Integration cần Docker Desktop chạy Linux containers và cổng 13306/16379 còn trống:

```powershell
docker compose -f docker-compose.test.yml up -d --wait --wait-timeout 180
.\mvnw.cmd verify -Pintegration
docker compose -f docker-compose.test.yml down
```

Xem thêm `target/failsafe-reports`. Các bước là tuần tự: nếu bước khởi tạo hạ tầng lỗi, đọc log trước khi chạy test. Dùng DB test riêng; không trỏ integration vào dữ liệu sử dụng thật.

## 3. Đóng gói và chạy toàn stack

Đọc [Dockerfile](../../Dockerfile), [docker-compose.yml](../../docker-compose.yml), [cấu hình prod](../../src/main/resources/application-prod.yml). Hiểu multi-stage build, non-root runtime, network giữa service, volume và healthcheck.

```powershell
docker compose up -d --build --wait --wait-timeout 300
Invoke-RestMethod http://localhost:8080/api/v1/actuator/health
powershell -File scripts/smoke-test.ps1
docker compose down
```

Smoke test tạo dữ liệu và request demo; dùng môi trường local riêng. `down` ở đây không yêu cầu xóa volume. Không dùng `down -v` trên môi trường cần giữ dữ liệu.

**Bài tập:** từ checkout sạch, ghi cấu hình cần có và thời gian tới khi API sẵn sàng. Cố tình đặt sai host DB và dùng log tìm nguyên nhân. Phân biệt localhost trên máy và localhost trong container.

## 4. CI và phiên bản triển khai

Workflow [ci.yml](../../.github/workflows/ci.yml) hiện định nghĩa verify, integration, Compose, smoke và publish GHCR trên push main sau verify. Có file workflow chưa có nghĩa đã chạy xanh trên GitHub; cần URL run gắn với đúng commit.

Trong project học, lần lượt thêm: test → verify coverage → integration → build image. Chỉ thêm publish khi đã hiểu image tag, registry và quyền truy cập. CI không đồng nghĩa đã triển khai cloud; image được đẩy lên registry cũng chưa có nghĩa app đang chạy.

## 5. Log, health, metrics

Đọc [RequestIdFilter](../../src/main/java/com/manh/ecom_be/filters/RequestIdFilter.java), [BusinessMetrics](../../src/main/java/com/manh/ecom_be/components/metrics/BusinessMetrics.java), [monitoring](../../monitoring).

1. Gửi một request, tìm log theo requestId; không log password/token.
2. Phân biệt process còn sống với readiness có đủ dependency để phục vụ.
3. Tạo vài đơn, đọc counter và đối chiếu với dữ liệu; rollback không được đếm như đơn thành công.
4. Đọc latency cùng số request/lỗi; ghi môi trường và dữ liệu khi đo.
5. Kiểm tra ý nghĩa metric: hiện PaymentController tăng paymentsSuccess khi tạo URL, chưa chứng minh giao dịch paid.

Đừng công khai mọi actuator/DB/Redis/Grafana khi đưa demo ra mạng. Kế hoạch siết quyền và phạm vi endpoint nằm trong nhóm nâng cấp.

## Nghiệm thu và tự vấn

- [ ] Clone/chạy lại được bằng hướng dẫn và cấu hình đã ghi.
- [ ] Phân biệt mock/H2/MySQL thật và giới hạn từng báo cáo.
- [ ] Tìm được một lỗi startup qua log.
- [ ] CI run có commit và artifact kiểm tra.
- [ ] Demo 5–10 phút gồm luồng thành công và một lỗi có chủ đích.

**Câu hỏi:** test chạy local nhưng CI lỗi có thể do gì? Docker image khác container? Liveness khác readiness? p95 có ý nghĩa gì? Coverage 80% có chắc không có lỗi nghiệp vụ không?
