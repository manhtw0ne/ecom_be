# 03 — Xác thực, token và quyền sở hữu

[Lộ trình](00-lo-trinh.md) · Trước: [Catalog](02-catalog-va-hop-dong-api.md) · Tiếp: [Đơn hàng](04-don-hang-va-ton-kho.md)

## Cần hiểu

Authentication xác định bạn là ai; authorization quyết định bạn được làm gì với tài nguyên cụ thể. JWT hợp lệ và role USER không có nghĩa được xem mọi đơn hàng. BCrypt hash mật khẩu; chữ ký JWT phát hiện sửa đổi, không mặc nhiên mã hóa payload.

Đọc theo thứ tự: [SecurityConfig](../../src/main/java/com/manh/ecom_be/configurations/SecurityConfig.java), [UserService](../../src/main/java/com/manh/ecom_be/services/user/UserService.java), [JwtTokenUtils](../../src/main/java/com/manh/ecom_be/components/JwtTokenUtils.java), [JwtTokenFilter](../../src/main/java/com/manh/ecom_be/filters/JwtTokenFilter.java), [WebSecurityConfig](../../src/main/java/com/manh/ecom_be/configurations/WebSecurityConfig.java), [TokenService](../../src/main/java/com/manh/ecom_be/services/token/TokenService.java).

## 1. Đăng ký/đăng nhập trước JWT

1. Chọn một định danh đăng nhập cho bản đầu: email hoặc số điện thoại.
2. Server gán USER khi đăng ký, không cho client tự cấp ADMIN.
3. Hash mật khẩu khi lưu; dùng PasswordEncoder.matches khi kiểm tra, không so hai hash mới tạo.
4. Kiểm tra trạng thái tài khoản và định danh trùng, có constraint DB tương ứng.
5. Test sai mật khẩu/user không tồn tại; response không chứa mật khẩu/hash.

## 2. Access token

1. Quy định subject, hạn dùng, khóa ký đọc từ môi trường. Bản học chọn thời hạn phù hợp và ghi rõ đơn vị.
2. Login thành công mới cấp token; không đưa dữ liệu nhạy cảm không cần thiết vào claim.
3. Filter đọc Bearer, xác minh chữ ký/hạn rồi thiết lập authentication.
4. Cấu hình public/protected theo cả method và path; debug cả filter lẫn security chain.
5. Test thiếu token, token hỏng, hết hạn, user bị khóa và token đúng.

Hiện `jwt.expiration` là 2592000 giây, refresh là 31536000 giây. Không mô tả access token hiện tại là một giờ; đó sẽ là thay đổi nếu bạn chọn áp dụng.

## 3. Role và ownership

Hợp đồng cần xây:

| Hành động | Chưa login | USER | ADMIN |
| --- | --- | --- | --- |
| Xem catalog | Cho phép | Cho phép | Cho phép |
| Ghi catalog | 401 | 403 | Cho phép |
| Đọc đơn/chi tiết | 401 | Chỉ đơn của mình | Theo phạm vi quản trị |
| Tạo đơn | 401 | Owner từ authentication | Theo quy tắc đã định |
| Đổi trạng thái giao | 401 | 403 | Chuyển trạng thái hợp lệ |

Với API nhận ID, kiểm tra quyền trên đối tượng đó. Truy vấn ID cùng owner hoặc kiểm tra ở service trước khi trả dữ liệu. Không tin userId client gửi.

**Bài tập bắt buộc:** tạo A, B và một đơn của B. Token A đọc đơn/chi tiết của B phải bị từ chối theo hợp đồng 403 hoặc 404 đã chọn. Kiểm tra cả danh sách theo user và tìm kiếm để tránh lọt dữ liệu qua đường khác.

Repo có GET orders/order_details được permitAll trong security config, các luồng đọc cần rà ownership. Đây là [mục nâng cấp P0](../upgrades/01-p0-tinh-dung-va-bao-mat.md), không phải thiết kế để sao chép.

## 4. Refresh và vòng đời tài khoản

1. Tạo refresh ngẫu nhiên, lưu user/hạn; cân nhắc lưu dạng hash ở bản nâng cấp.
2. Khi refresh, kiểm tra tồn tại, hạn, thu hồi và owner; rotation phải làm token cũ không dùng lại được theo chính sách.
3. Định nghĩa logout/đổi mật khẩu/khóa tài khoản thu hồi token nào và có hiệu lực khi nào.
4. Test hai request refresh cùng token. Nếu chỉ một lần được thành công, phải xử lý nguyên tử và kiểm thử cạnh tranh.

TokenService có rotation UUID và giới hạn số token lưu; hai cơ chế này chưa chứng minh toàn bộ vòng đời đã an toàn. Đọc cả controller gọi service và filter xác thực.

## Nghiệm thu và tự vấn

- [ ] HTTP test thiếu login, sai role, sai owner.
- [ ] Client không chọn được role đặc quyền/owner tùy ý.
- [ ] Sai mật khẩu thất bại; đổi mật khẩu có test thu hồi.
- [ ] Thử dùng lại refresh token cũ và refresh đồng thời.
- [ ] Debug được SecurityContext và giải thích 401/403.

**Câu hỏi:** stateless nghĩa là gì khi vẫn lưu token DB? CORS có thay authorization không? Vì sao thay ID trong URL là ca test quan trọng? Vì sao không log token? Nếu đổi sang cookie, quyết định tắt CSRF cần xem lại thế nào?
