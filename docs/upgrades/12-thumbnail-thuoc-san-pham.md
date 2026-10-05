# 12 — Thumbnail phải thuộc sản phẩm

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 29/09/2026

## Hợp đồng mới

Trước đây tạo/sửa sản phẩm nhận chuỗi thumbnail tùy ý: tên không tồn tại, URL ngoài hoặc ảnh của sản phẩm khác đều có thể được lưu. Nay:

- Tạo sản phẩm: thumbnail phải null hoặc blank. Giá trị được lưu là null. Sau đó upload ảnh qua endpoint batch; ảnh đầu được chọn làm thumbnail.
- Sửa sản phẩm: thumbnail null hoặc chuỗi rỗng giữ nguyên. Giá trị khác phải khớp image_url trong danh sách ảnh của chính sản phẩm; không khớp trả 400 và không đổi sản phẩm.
- Không thay đổi dữ liệu thumbnail legacy tự động. Sửa các trường khác với thumbnail null vẫn giữ giá trị cũ; muốn thay thumbnail phải upload/chọn ảnh hợp lệ.

Ví dụ sản phẩm A có `a.png`, sản phẩm B có `b.png`: cập nhật A với `b.png` bị từ chối dù file tồn tại. URL ngoài, đường dẫn traversal và ảnh đã xóa cũng bị từ chối nếu không nằm trong metadata ảnh của A. Quy tắc kiểm tra quan hệ database, không kiểm tra sự tồn tại của file trên đĩa mỗi lần sửa; file bị xóa ngoài ứng dụng cần đối soát riêng.

## Vì sao cần khóa?

```java
// Khóa sản phẩm giống upload/xóa ảnh, rồi refresh entity.
var product = productRepository.findByIdForUpdate(id).orElseThrow(...);
// Locking read danh sách ảnh để tránh kiểm tra trên snapshot cũ.
boolean belongs = productImageRepository.findAllForUpdate(id).stream()
    .anyMatch(image -> requestedThumbnail.equals(image.getImageUrl()));
```

Nếu chọn ảnh B trước, request xóa B chạy sau sẽ chọn ảnh còn lại. Nếu xóa B trước, request chọn B chạy sau bị từ chối. Không để thao tác chọn thumbnail lưu lại ảnh B sau khi B đã bị xóa thông qua luồng này.

## Kiểm thử và giới hạn

Test mới kiểm tra chọn ảnh đã upload, từ chối ảnh khác sản phẩm/ảnh thiếu/ảnh đã xóa, từ chối thumbnail tùy ý khi tạo, và chọn/xóa cùng ảnh đồng thời. Test tích hợp dùng H2; chưa xác minh MySQL thật.

Kết quả local: `mvn -B clean verify` BUILD SUCCESS; **192 tests, 0 failures, 0 errors, 0 skipped**. Coverage dòng service **692/871 = 79,45%**, vượt gate 70%. `git diff --check -- src docs/upgrades` không báo lỗi whitespace. Log: `target/thumbnail-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Thay đổi này yêu cầu client tạo sản phẩm trước khi upload; client dùng URL ảnh ngoài phải chuyển sang luồng upload. Dữ liệu ảnh legacy trùng tên giữa các sản phẩm chưa được tự sửa. Xóa mềm sản phẩm được xử lý tiếp ở [bài 13](13-xoa-mem-san-pham.md); xóa vĩnh viễn và đối soát filesystem sau crash vẫn còn mở.
