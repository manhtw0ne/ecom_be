# Quyền hồ sơ, bình luận và social identity — 28/09/2026

[Kế hoạch](00-ke-hoach-fresher.md) · [P0](01-p0-tinh-dung-va-bao-mat.md)

## Lỗi và hành vi mới

### Bình luận

Trước sửa, controller so sánh user_id trong body với người đăng nhập nhưng service tải comment bằng một ID khác mà không kiểm tra chủ sở hữu. Vì thế A có thể gửi user_id=A và commentId của B để sửa nội dung của B.

Service giờ kiểm tra owner từ bản ghi comment trong DB. Chủ bình luận hoặc ADMIN được sửa; tài khoản khác nhận 403. ID không có nhận 404. Khi tạo bình luận, service lấy tác giả từ principal, bỏ qua user_id do client gửi. Update chỉ đổi nội dung, không chuyển tác giả/sản phẩm theo body.

Service deleteComment cũng kiểm tra owner/admin và ID tồn tại trước xóa. Controller hiện chưa có DELETE route; không coi thay đổi service này là API mới. Service tạo dữ liệu giả yêu cầu ADMIN.

Response bình luận giữ user.id, user.fullname, user.profile_image. Đã bỏ phone_number, address, date_of_birth, role, social IDs và trạng thái tài khoản khỏi phần tác giả. Đây là thay đổi có chủ đích: client hiển thị bình luận không nên phụ thuộc hồ sơ riêng tư của tác giả.

### Hồ sơ và thao tác quản trị

SecurityUtils có các kiểm tra requireUser/requireSelf/requireOwnerOrAdmin/requireAdmin, dùng principal tài khoản active, không bị xóa và role USER/ADMIN.

| Thao tác service | Quyền |
|---|---|
| updateUser, changeProfileImage | Chính tài khoản đó |
| resetPassword, blockOrEnable, findAll | ADMIN |
| insertComment | USER/ADMIN, tác giả lấy từ principal |
| updateComment, deleteComment | Owner hoặc ADMIN |
| generateFakeComments | ADMIN |

Quyền ở service giúp đường gọi khác không bỏ qua controller. Admin không dùng updateUser để giả làm người khác; thao tác reset/block vẫn có endpoint quản trị riêng. PUT profile dùng principal đã được filter xác thực, không tự parse token lần nữa để quyết định owner.

### Social identity

API đăng ký thông thường từ chối google_account_id/facebook_account_id có nội dung và is_social_login=true. Không cho client dùng cờ social để bỏ qua hash mật khẩu. Đăng ký chỉ cho role USER; mọi mật khẩu của luồng này được encode trước khi lưu.

API cập nhật profile từ chối các social ID mới và giữ nguyên liên kết đã có. Chỉ luồng callback social login đã xác minh với nhà cung cấp mới được provision identity. Chưa thêm tính năng liên kết/đổi liên kết social account cho người đã đăng nhập; tính năng đó cần xác minh riêng với nhà cung cấp, không thể dùng ID client tự khai báo.

Không thay đổi tích hợp OAuth2 hiện có và không coi test nội bộ là bằng chứng đã chạy end-to-end với Google/Facebook.

## Ví dụ kiểm chứng

```http
PUT /api/v1/comments/<comment-cua-B>
Authorization: Bearer <token-cua-A>
Content-Type: application/json

{"user_id":<id-cua-A>,"product_id":1,"content":"Noi dung gia mao"}
```

Kết quả phải là 403 và nội dung cũ còn nguyên. Thay token thành chủ bình luận thì update thành công; đổi user_id trong body không đổi tác giả.

```http
PUT /api/v1/users/details/<id-cua-A>
Authorization: Bearer <token-cua-A>
Content-Type: application/json

{"google_account_id":"id-tu-khai-bao"}
```

Kết quả phải là 400, liên kết trước đó không thay đổi. Cập nhật fullname thông thường của chính A vẫn chạy.

## Kiểm thử

```powershell
mvn -B '-Dtest=ProfileCommentAuthorizationTest,CommentServiceTest,UserLifecycleTest,RefreshTokenLifecycleTest' test
mvn -B clean verify
```

ProfileCommentAuthorizationTest có 13 ca, dùng filter/service/H2 thật. Bao gồm giả owner, owner/admin hợp lệ, rút gọn tác giả, anonymous, sửa profile trái quyền, social ID giả, ID bình luận mất và gọi service trực tiếp để bypass controller.

Unit test nghiệp vụ giữ mock SecurityUtils để tách kiểm tra nghiệp vụ; bằng chứng phân quyền nằm ở suite HTTP dùng helper thật. Các test refresh gọi trực tiếp thao tác quản trị đã được bổ sung actor đúng quyền; khi kiểm tra token cũ phải xóa cả TestSecurityContextHolder để không vô tình giữ principal đã xác thực từ bước chuẩn bị test.

## Phần còn mở

Không có migration mới cho thay đổi này. Chưa đóng toàn bộ F01/F04: còn rà payment query/refund, đường lưu/đọc file avatar, liên kết social end-to-end, actuator/CORS và cấu hình public. Các bước kiểm chứng MySQL/Redis của nâng cấp tiền/refresh trước đó vẫn còn chờ Docker; không dùng test H2 để thay thế.

### Kết quả local

- Ngày 28/09/2026: mvn -B clean verify thành công, 163 test, không failures/errors/skipped.
- Service line coverage: 626/819 = 76.43%, đạt gate 70%.
- Log: target/privacy-verify.log; reports: target/surefire-reports và target/site/jacoco/index.html.
- Chưa commit/push/deploy hoặc thay đổi DB đang dùng.
