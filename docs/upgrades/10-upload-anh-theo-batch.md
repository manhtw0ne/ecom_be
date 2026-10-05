# 10 — Upload ảnh sản phẩm theo batch

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 29/09/2026

## Bài toán đã sửa

Trước đây controller xử lý từng file: lưu file rồi gọi service tạo metadata. Nếu file thứ hai lỗi, file thứ nhất có thể đã commit dù request trả lỗi. Kiểm tra số ảnh cũng chưa khóa sản phẩm, nên hai request có thể cùng nhìn thấy còn chỗ.

Hiện tại `ProductImageUploadService.upload()` giữ một transaction cho cả batch và yêu cầu admin ngay tại service. Controller chuyển toàn bộ danh sách vào service. Phương thức `ProductService.createProductImage()` cũ đã bỏ vì không còn caller.

```java
// Cùng sản phẩm: request sau chờ request trước kết thúc transaction.
var product = products.findByIdForUpdate(productId).orElseThrow(...);
// Kiểm tra tổng số ảnh hiện có + số file mới dưới khóa.
// Ghi file, thêm metadata, cập nhật thumbnail trong cùng transaction.
// Nếu rollback: callback dọn tất cả file vừa tạo của batch.
```

Ví dụ sản phẩm đang có 4 ảnh, hai request cùng thêm 1 ảnh: một request thành công, request còn lại nhận 400 vì tổng đã đạt 5. Nếu batch gồm PNG hợp lệ và HTML giả đuôi PNG, response là 415; metadata, thumbnail và file mới của cả batch được hoàn tác.

## Hợp đồng API

`POST /api/v1/products/uploads/{id}` nhận multipart với nhiều trường cùng tên `files`.

| Trường hợp | Kết quả |
| --- | --- |
| Admin gửi batch hợp lệ | 200, danh sách ảnh đã tạo |
| Thiếu file, file rỗng, trên 5 file hoặc tổng vượt 5 | 400, không lưu batch |
| Nội dung không phải PNG/JPEG hợp lệ | 415, rollback batch |
| File quá 10 MiB | 413, rollback batch |
| Sản phẩm không tồn tại | 404 |

File rỗng nay bị từ chối thay vì bỏ qua. Bộ giải mã kiểm tra nội dung thật; MIME client gửi không quyết định định dạng hợp lệ. Quy tắc kích thước, tên file và đường đọc ảnh giữ theo [bài 09](09-an-toan-anh.md).

## Kiểm thử và giới hạn

`ProductImageBatchTest` kiểm tra upload HTTP, file thứ hai lỗi, rollback từ transaction ngoài, vượt sức chứa, batch rỗng, quyền service và hai batch cạnh tranh.

Xác minh local ngày 29/09/2026: `mvn -B clean verify` **BUILD SUCCESS**, **183 tests, 0 failures, 0 errors, 0 skipped**; gồm 7 test batch mới. Coverage dòng service **673/852 = 78,99%**, vượt gate 70%. Test database dùng H2. `git diff --check -- src docs/upgrades` không báo lỗi whitespace. Log: `target/batch-verify.log`; báo cáo: `target/surefire-reports` và `target/site/jacoco/index.html` (được tạo lại khi build).

Cập nhật tiếp ngày 29/09/2026: luồng xóa từng ảnh đã dùng cùng khóa sản phẩm với upload và dọn file sau commit, xem [bài 11](11-xoa-anh-va-transaction.md). Sửa thumbnail tùy ý và xóa toàn sản phẩm vẫn còn giới hạn. Callback không bảo đảm dọn file nếu process chết đột ngột hoặc filesystem báo lỗi; cần đối soát file sau crash. Chưa kiểm chứng trên MySQL/Redis thật.

Tự luyện: giải thích vì sao `@Transactional` không tự rollback file, vì sao kiểm tra `count < 5` ngoài khóa vẫn có race condition, và phân biệt rollback thông thường với process crash.
