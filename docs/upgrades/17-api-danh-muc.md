# 17 — Phân trang và lỗi API danh mục

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 02/10/2026

## Hành vi mới

Trước đây GET danh mục nhận page/limit nhưng gọi findAll(), rồi gửi toàn bộ danh sách sang Kafka. Nay query dùng PageRequest, sort ID tăng dần và không gửi sự kiện cho thao tác đọc.

```http
GET /api/v1/categories?page=1&limit=10
```

Page bắt đầu từ 0. Mặc định page=0, limit=10; page hợp lệ 0..100000, limit 1..100. Giá trị ngoài giới hạn trả 400. Trang vượt dữ liệu trả mảng rỗng. Response giữ `data` là mảng để giảm thay đổi client; tổng số bản ghi và số trang nằm trong header `X-Total-Count`, `X-Total-Pages`, đã expose qua CORS.

Client từng dựa vào API luôn trả toàn bộ danh mục cần chuyển sang tải từng trang hoặc lặp qua các trang. Method getAllCategories nội bộ/cache vẫn giữ nguyên cho caller cũ; endpoint HTTP danh sách dùng method phân trang mới.

## Lỗi và xóa danh mục

GET/PUT/DELETE với ID không tồn tại trả 404 theo ApiResponse thay vì lỗi 500 chung. Xóa danh mục có sản phẩm tham chiếu trả 409, kể cả sản phẩm đã ẩn. Truy vấn native đếm trực tiếp products để không bỏ qua `is_deleted=1` do SQLRestriction của entity.

Ví dụ danh mục A còn sản phẩm P đã ẩn: A vẫn được giữ vì P vẫn tồn tại trong database. Danh mục rỗng có thể xóa. Không cascade xóa sản phẩm để ép xóa danh mục.

Kiểm tra đếm không thay thế foreign key khi có request gán sản phẩm đồng thời; khóa ngoại vẫn là ràng buộc cuối cùng. Chưa có test cạnh tranh MySQL cho tình huống này. Luồng POST tạo danh mục vẫn có Kafka theo thiết kế cũ, chưa được đổi sang outbox/after-commit trong lần này.

## Kiểm thử và tự học

CategoryApiTest kiểm tra phân trang có thứ tự, default params, tham số sai, 404 cho đọc/sửa/xóa, 409 khi còn sản phẩm active/ẩn và xóa danh mục rỗng. Database test dùng H2; chưa kiểm chứng MySQL/Redis/Kafka thật.

Xác minh 02/10/2026: `mvn -B clean verify` BUILD SUCCESS, **227 tests, 0 failures, 0 errors, 0 skipped**, gồm 9 ca API danh mục mới; coverage gate service pass. `git diff --check -- src docs/upgrades` không báo lỗi. Log: `target/category-api-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Tự học: vì sao phải sort khi phân trang; vì sao xóa mềm không xóa liên kết khóa ngoại; và tại sao một request đọc không nên vô tình phát sinh sự kiện ghi?
