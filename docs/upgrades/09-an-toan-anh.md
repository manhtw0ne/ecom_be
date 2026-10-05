# 09 — Kiểm tra ảnh và vòng đời avatar

[Tổng kế hoạch](00-ke-hoach-fresher.md) · Ngày thực hiện: 28/09/2026

## Lỗi và thay đổi

Trước đây `isImageFile()` luôn trả `true`. File HTML đổi tên thành `.jpg` có thể được lưu; đường đọc/xóa file chưa ràng buộc rõ trong thư mục upload. Khi đổi avatar, code xóa file cũ ngay sau lời gọi cập nhật database, chưa quản lý rollback của toàn bộ transaction.

Hiện tại nội dung phải giải mã được thành PNG hoặc JPEG. Không tin tên file hay MIME client gửi. Giới hạn 10 MiB được kiểm tra cả trên số byte thực đọc; chiều ảnh tối đa 4096 pixel mỗi cạnh và tổng tối đa 16 triệu pixel. Sau giải mã, ứng dụng ghi lại pixel thành ảnh mới, bỏ metadata và nội dung nối thêm. Tên do server tạo bằng UUID.

Ví dụ: `avatar.html` chứa PNG hợp lệ được lưu thành `avatar-<userId>-<uuid>.png`. Ngược lại, `avatar.jpg` chứa HTML bị trả `415`. Upload avatar rỗng trả `400`, quá 10 MiB trả `413`, chưa đăng nhập trả `401`.

Đường đọc và xóa chỉ nhận basename hợp lệ trong thư mục `uploads`; từ chối đường dẫn tương đối vượt thư mục, đường dẫn tuyệt đối và file symlink. GET ảnh trả MIME theo nội dung với `X-Content-Type-Options: nosniff`; ảnh không tồn tại hoặc không hợp lệ trả `404`.

## Transaction không tự rollback file

Database và filesystem là hai nơi lưu trữ độc lập. `@Transactional` rollback dữ liệu SQL nhưng không tự xóa file vừa ghi. `ProfileImageService` bù lại bằng callback:

```java
// Transaction đang giữ khóa hàng user.
String created = FileUtils.storeAvatar(file, userId);
// afterCommit: xóa avatar cũ do chính user này tạo.
// afterCompletion(ROLLED_BACK): xóa file created.
user.setProfileImage(created);
users.saveAndFlush(user);
```

Khóa hàng user tuần tự hóa hai lần thay avatar đồng thời. Sau khi lấy khóa, refresh entity để không dùng profile_image đã đọc trước khi chờ khóa. Dọn ảnh cũ chỉ áp dụng tên `avatar-<đúng userId>-<uuid>.png/jpg`; giữ nguyên ảnh mặc định, tên legacy và URL OAuth vì chúng có thể được dùng chung.

## Phạm vi tương thích và giới hạn

- Bộ kiểm tra/lưu ảnh dùng chung cũng áp dụng cho upload ảnh sản phẩm. Chỉ PNG/JPEG được hỗ trợ; GIF/WebP/SVG bị từ chối. Route sản phẩm vẫn yêu cầu MIME đầu vào thuộc `image/*`.
- GET ảnh sản phẩm và avatar không còn trả ảnh fallback với HTTP 200 khi thiếu file. Frontend cần hiển thị placeholder khi nhận 404.
- Callback avatar xử lý commit/rollback thông thường, chưa bảo đảm khôi phục khi process bị kill giữa ghi file và commit. Lỗi dọn file được ghi log; cần job đối soát nếu triển khai dài hạn.
- Cập nhật 29/09/2026: đã bổ sung transaction toàn batch và dọn file khi rollback trong [bài 10](10-upload-anh-theo-batch.md), đồng bộ xóa từng ảnh trong [bài 11](11-xoa-anh-va-transaction.md). Đối soát sau crash vẫn còn mở.
- Chưa kiểm chứng trên MySQL/Redis thật do Docker engine chưa khả dụng ở lần rà môi trường trước. Test transaction và cạnh tranh ở đây dùng H2.

## Tự học và demo phỏng vấn

1. Upload một file HTML đổi đuôi PNG và giải thích vì sao extension/MIME không đủ.
2. Đặt breakpoint trước commit: ảnh cũ vẫn tồn tại. Rollback rồi kiểm tra ảnh mới đã được dọn.
3. Chạy hai lần thay avatar đồng thời: database và file còn lại phải cùng trỏ đến lần ghi cuối.
4. Giải thích tại sao khóa database không biến filesystem thành một phần của transaction, và vì sao cần đối soát sau crash.

Kiểm thử chính: `ImageStorageTest` và `ProfileImageLifecycleTest` có 13 ca mới. Chạy `mvn -B clean verify` ngày 28/09/2026: **176 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Coverage dòng ở service: **645/839 = 76,88%**, vượt gate 70%. Test cạnh tranh avatar pass trên H2; không thay thế kiểm chứng khóa MySQL. `git diff --check -- src docs/upgrades` không báo lỗi whitespace.

Log lần chạy nằm tại `target/image-verify.log`; báo cáo chi tiết tại `target/surefire-reports` và `target/site/jacoco/index.html`. Các file target được tạo lại khi chạy build, không phải bằng chứng CI đã chạy trên remote.
