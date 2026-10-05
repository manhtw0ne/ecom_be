# 18 — Phân biệt lỗi request và lỗi server

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 03/10/2026

Handler Exception chung trước đây biến một số lỗi client thành 500. Đã thêm ánh xạ riêng, giữ response ApiResponse:

| Request | HTTP |
| --- | --- |
| JSON sai cú pháp, thiếu body bắt buộc | 400 |
| page=abc, ID không phải số, số vượt kiểu dữ liệu | 400 |
| Thiếu request parameter/header/part bắt buộc | 400 |
| Content-Type không hỗ trợ | 415 |
| Đường dẫn không tồn tại sau khi vượt qua security | 404 |
| HTTP method không hỗ trợ | 405 kèm header Allow |

Ví dụ `GET /api/v1/categories?page=abc` trả 400 thay vì 500. `POST /api/v1/users/login` với JSON hỏng trả thông báo cố định, không trả chuỗi lỗi parser có thể chứa password hoặc tên class Java.

Không đổi lỗi server thật thành 400: nhánh Exception chung vẫn giữ 500 và ghi log để điều tra. Security vẫn chạy trước controller; anonymous gọi đường dẫn được bảo vệ có thể nhận 401 trước khi Spring xác định route có tồn tại hay không. Chưa chuẩn hóa mọi response phát sinh từ servlet container, reverse proxy hoặc Accept negotiation.

RequestErrorContractTest dùng MockMvc với filter/controller/advice thật, kiểm tra các request sai kiểu số, JSON lỗi, body thiếu, media type, multipart thiếu file, đường dẫn thiếu và Allow header. Đây là kiểm tra hợp đồng HTTP local; chưa xác minh reverse proxy hay hạ tầng thật.

Xác minh 03/10/2026: `mvn -B clean verify` BUILD SUCCESS, **237 tests, 0 failures, 0 errors, 0 skipped**, gồm 10 ca mới. Coverage gate service pass; `git diff --check -- src docs/upgrades` không báo lỗi. Log: `target/request-errors-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Tự học: phân biệt lỗi deserialization với Bean Validation; tại sao sai JSON là 400; và client dùng header Allow khi nhận 405 như thế nào?
