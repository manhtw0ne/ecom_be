# Interview Preparation — Ecom Backend

> Dùng tài liệu này để chuẩn bị trả lời kỹ thuật khi phỏng vấn Fresher.
> Mỗi câu hỏi gồm: câu trả lời ngắn + điểm demo trong code.

---

## 1. Tổng quan dự án (30 giây elevator pitch)

> "Tôi xây dựng REST API backend cho hệ thống thương mại điện tử bằng Spring Boot 3 và Java 21.
> Hệ thống hỗ trợ xác thực JWT + OAuth2, quản lý sản phẩm với Redis cache, đặt hàng an toàn đồng thời
> bằng Redisson distributed lock, thanh toán VNPay, và monitoring bằng Prometheus/Grafana.
> Có 254 unit test, coverage 81%, và CI/CD pipeline trên GitHub Actions."

---

## 2. Câu hỏi thường gặp

### Q: Tại sao dùng distributed lock cho đặt hàng?
**A:** Khi nhiều user đặt hàng cùng lúc cho cùng sản phẩm, nếu không có lock thì có thể xảy ra race condition —
tất cả đều thấy còn hàng, nhưng thực ra chỉ còn 1 cái (overselling).
Tôi dùng Redisson `RLock` per product ID: lock trước khi đọc tồn kho, trừ kho, release sau commit.
Demo: `OrderService.java` → phần `redissonClient.getLock("order:stock:" + productId)`.

### Q: Redis được dùng vào việc gì trong project?
**A:** Ba mục đích:
1. **Cache sản phẩm** — TTL 5 phút, key prefix `products:page-v3:`, invalidate sau khi admin update
2. **Distributed lock** — Redisson watchdog cho đặt hàng đồng thời
3. **Rate limiting** — Bucket4j lưu quota theo IP trong Redis, giới hạn 10 req/min cho login

### Q: Flyway migration là gì, tại sao dùng?
**A:** Flyway là database migration tool — mỗi thay đổi schema được viết thành file SQL có version (V1, V2...).
Khi app khởi động, Flyway tự apply các migration chưa chạy theo thứ tự.
Lợi ích: schema luôn đồng bộ với code, có lịch sử rollout, không cần import SQL thủ công.
Project có 11 migration từ V0 (schema gốc) đến V11 (order product snapshots).

### Q: JWT refresh token hoạt động như thế nào?
**A:** Sau khi login, user nhận 2 token: access token (ngắn hạn, 30 ngày) và refresh token (dài hạn, 1 năm).
Khi access token hết hạn, client gửi refresh token để lấy token mới.
Rotation: mỗi lần refresh, token cũ bị thu hồi, token mới được cấp — tránh bị replay nếu token bị lộ.
Demo: `RefreshTokenLifecycleTest.java`

### Q: Tại sao dùng @Async cho gửi email?
**A:** Gửi email qua SMTP có thể mất 2-5 giây — nếu để trong transaction thì làm chậm toàn bộ request đặt hàng.
`@Async` với executor riêng chạy email trên thread khác, không block response.
Quan trọng: email chỉ gửi **sau khi transaction commit** (dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`)
— nếu đặt hàng rollback thì email không gửi. Demo: `EmailService.java` + `OrderPlaced.java`

### Q: Rate limiting hoạt động như thế nào?
**A:** Dùng Bucket4j với token bucket algorithm. Mỗi IP có một bucket riêng lưu trong Redis.
- Login/register: 10 requests/phút → trả 429 Too Many Requests khi hết quota
- Public API: 60 requests/phút
- Header trả về `X-RateLimit-Remaining` và `Retry-After` để client biết cần chờ bao lâu
Demo: `RateLimitInterceptor.java`

### Q: Làm thế nào để test mà không cần MySQL/Redis/Kafka thật?
**A:** Dùng 3 cách:
1. **H2 in-memory** cho unit test (profile `test`)
2. **@MockBean** cho các bean Redis/Kafka trong context test
3. **Mockito** mock các service dependencies
Chỉ integration test mới cần infrastructure thật (chạy qua `docker-compose.test.yml`)

### Q: Tại sao coverage đạt 81%? Bạn test những gì?
**A:** Test tập trung vào business logic quan trọng nhất:
- Service layer: order creation, stock deduction, user lifecycle
- Security: JWT validation, authorization per role
- Concurrency: `OrderConcurrencyTest` với 5 threads đặt cùng lúc
- Migration: verify schema đúng sau mỗi Flyway migration
- HTTP contract: `RequestErrorContractTest` verify status code 400/404/415/405

### Q: Kafka được dùng vào việc gì?
**A:** Broadcast category events sau khi tạo/update danh mục,
để các service khác (nếu có) có thể subscribe và xử lý async.
Trong monolith hiện tại, Kafka chủ yếu dùng để demo integration pattern.
Trong môi trường cloud demo, Kafka bị disable gracefully — health check bypass khi không có broker.

### Q: Giải thích BigDecimal vs Float cho tiền tệ?
**A:** Float có lỗi làm tròn nhị phân — ví dụ 0.1 + 0.2 ≠ 0.3 với float.
Với tiền tệ, cần chính xác tuyệt đối → dùng BigDecimal với DECIMAL(19,2) trong MySQL.
Project đã migrate từ Float sang BigDecimal (V9 migration) và dùng class Money wrapper.
Demo: `Money.java` + `MoneyTest.java`

---

## 3. Câu hỏi về quyết định thiết kế

### Q: Tại sao chọn Modular Monolith thay vì Microservices?
**A:** Với team 1 người ở level fresher, microservices tạo ra overhead không cần thiết:
distributed tracing, service discovery, network latency giữa services, complex deployment.
Monolith dễ debug, deploy, và đủ để demonstrate kiến thức Spring Boot.
Nếu cần scale sau này, có thể tách module thành service riêng theo từng domain boundary.

### Q: Tại sao Swagger bị tắt ở prod?
**A:** Swagger tiết lộ toàn bộ API surface — các kẻ tấn công có thể dùng để fuzz test API.
Trong production thật, chỉ dev team cần xem API docs.
Nhưng với cloud demo, có thể bật lại bằng env var `SPRINGDOC_SWAGGER_UI_ENABLED=true`.

---

## 4. Điểm yếu cần thành thật thừa nhận

- VNPay chỉ hỗ trợ COD trong demo, chưa có payment flow end-to-end
- Kafka chưa có durable outbox pattern — email là best-effort, không có retry
- Image storage local (disk) — production cần S3/GCS
- Chưa có EXPLAIN ANALYZE trên MySQL thật (chỉ đo trên H2)

---

## 5. Câu hỏi ngược lại cho interviewer

- Tech stack của team backend hiện tại là gì?
- Junior/fresher thường được assign loại task nào trong sprint đầu?
- Mentoring culture ở team như thế nào?