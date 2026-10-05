# 11 — Xóa ảnh sau commit và đồng bộ với upload

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 29/09/2026

## Thay đổi

Trước đây controller xóa file sau lời gọi service, còn service xóa metadata không dùng khóa chung với upload. Nay quyền admin được kiểm tra ngay tại service; xóa metadata và đổi thumbnail nằm trong một transaction giữ khóa sản phẩm giống upload. File chỉ được dọn sau commit. Xóa ID không tồn tại trả 404.

```java
// 1. Xác định sản phẩm của ảnh.
// 2. Khóa sản phẩm, rồi khóa và đọc lại ảnh cần xóa.
// 3. Xóa metadata; nếu đây là thumbnail, chọn ảnh còn lại có ID nhỏ nhất.
// 4. Đăng ký callback xóa file sau commit.
TransactionCallbacks.afterCommit(() -> deleteGeneratedFile(filename));
```

Ví dụ chỉ có ảnh A, một request xóa A và request khác upload B. Hai thao tác cùng khóa hàng sản phẩm: dù bên nào chạy trước, sau khi cả hai thành công, B còn tồn tại và thumbnail trỏ B. Danh sách ảnh thay thế dùng locking read để đọc dữ liệu hiện tại thay vì dựa vào snapshot cũ sau khi chờ khóa.

Nếu transaction ngoài rollback sau lời gọi xóa, metadata và thumbnail được phục hồi; callback không chạy nên file A vẫn còn. Nếu xóa ảnh cuối cùng thành công, thumbnail trở thành null.

Phải bỏ ảnh khỏi `product.productImages` trước khi delete entity: quan hệ có `CascadeType.ALL`, nên giữ child trong collection có thể làm metadata được persist lại lúc flush. Test tích hợp đã tái hiện lỗi này; chỉ mock repository không phát hiện được.

## Quyền sở hữu file và giới hạn

Chỉ tự dọn tên UUID `.png`/`.jpg` do luồng upload sản phẩm hiện tại tạo. Giữ file legacy, đường dẫn ngoài và ảnh avatar để tránh xóa file có thể dùng chung. Không cung cấp API tái sử dụng tên file UUID đã xóa; dữ liệu legacy chia sẻ cùng tên UUID cần đối soát trước khi áp dụng tự dọn.

Lỗi filesystem sau commit được ghi log, không biến thao tác database đã thành công thành lỗi HTTP. Process crash hoặc lỗi xóa file vẫn có thể để lại file mồ côi; cần job đối soát riêng. Cập nhật tiếp: [bài 12](12-thumbnail-thuoc-san-pham.md) đã kiểm soát thumbnail khi tạo/sửa sản phẩm. Xóa toàn sản phẩm chưa được đưa vào cơ chế dọn file này.

## Kiểm thử và bài tập

Test tích hợp kiểm tra xóa qua HTTP, xóa lặp trả 404, rollback giữ file, quyền admin ở service, xóa ảnh cuối và upload/xóa đồng thời. Test database dùng H2; chưa thay thế xác minh trên MySQL thật.

Xác minh local: `mvn -B clean verify` BUILD SUCCESS, **188 tests, 0 failures, 0 errors, 0 skipped**; coverage dòng service **685/864 = 79,28%**, vượt gate 70%. `git diff --check -- src docs/upgrades` không báo lỗi. Log: `target/image-delete-verify.log`; báo cáo chi tiết ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Tự giải thích: tại sao phải khóa sản phẩm trước hàng ảnh; vì sao callback chỉ chạy sau commit; nếu xóa file thất bại sau commit thì nên trả lỗi HTTP hay ghi log để đối soát?
