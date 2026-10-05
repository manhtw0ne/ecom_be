# Refresh token và thu hồi phiên — 28/09/2026

[Kế hoạch](00-ke-hoach-fresher.md) · [P0](01-p0-tinh-dung-va-bao-mat.md)

## Những lỗi đã sửa

Luồng cũ đọc user từ access token trước khi refresh, nên access token hết hạn có thể làm refresh thất bại. Service chưa kiểm tra revoked/expired/tài khoản bị khóa đầy đủ, nhận User từ caller và chưa chống hai yêu cầu xoay cùng token. JWT cấp trong cùng một giây cũng có thể trùng nhau vì thiếu claim định danh riêng.

Luồng mới lấy owner từ bản ghi refresh trong DB, khóa user rồi khóa token trong một transaction, kiểm tra trạng thái và thay cả access/refresh token. Không còn tham số User do caller truyền vào. JWT có jti ngẫu nhiên, mỗi lần cấp có chuỗi token khác nhau.

## Hợp đồng sử dụng

POST /api/v1/users/refreshToken không yêu cầu access token. Gửi credential trong JSON body, không đưa token vào URL. Hỗ trợ cả refreshToken hiện có và alias refresh_token.

```http
POST /api/v1/users/refreshToken
Content-Type: application/json

{"refresh_token":"<refresh-token-nhan-khi-login>"}
```

Gửi request refresh không kèm Authorization đã hết hạn. Filter vẫn từ chối Bearer token hỏng nếu client gửi nó, dù endpoint có permitAll.

| Trường hợp | Kết quả |
|---|---|
| Access token hết hạn, refresh còn hợp lệ | 200, cặp token mới |
| Refresh không tồn tại, đã xoay hoặc hết hạn | 401 |
| Session revoked/expired; tài khoản khóa/xóa mềm | 401 |
| Body thiếu/rỗng/refresh dài quá 255 ký tự | 400 |
| Hai yêu cầu đồng thời dùng cùng refresh | Một thành công; yêu cầu còn lại bị từ chối |
| Dùng access token cũ sau xoay | 401 |

Client thay cặp token một lần và chỉ cho một request refresh chạy tại một thời điểm. Các request khác chờ kết quả đó; không tự gửi nhiều refresh song song rồi ghi đè token mới bằng kết quả cũ.

Refresh có thời hạn tuyệt đối tính từ lần đăng nhập: rotation không kéo dài refresh_expiration_date. Hết thời hạn này phải đăng nhập lại. Đây là thay đổi so với cách cũ kéo dài thời hạn sau mỗi lần refresh.

Login/register/refresh chia sẻ quota xác thực hiện có (10 request/phút/IP). Không dùng IP từ X-Forwarded-For do client tự gửi.

## Thu hồi và thứ tự khóa

Các thao tác tạo session, refresh, đổi/reset mật khẩu và block user dùng cùng thứ tự khóa user trước khi sửa token. Thứ tự này giúp các thao tác không xoay và thu hồi cùng phiên theo hai đường không phối hợp.

- Đổi mật khẩu hoặc admin reset: xóa các session của user.
- Block: xóa session; enable lại không hồi sinh token cũ.
- Access token được kiểm tra chữ ký/hạn JWT, trạng thái DB, owner, hạn access trong DB và trạng thái active của user.
- Token hết thời hạn access tự nhiên vẫn có thể refresh nếu session chưa bị đánh dấu expired/revoked và refresh còn hạn. Cờ expired ở bản ghi được xem là vô hiệu hóa cả session.

Đã sửa thêm phép so sánh owner ở cập nhật profile từ so sánh tham chiếu Long sang equals. Chưa coi đây là hoàn tất rà quyền user/comment/avatar/payment ở toàn bộ service.

## Migration V10

V10 tăng tokens.token lên VARCHAR(2048) để chứa JWT có jti; đổi refresh rỗng từ migration cũ thành NULL và tạo unique index cho refresh_token. Nhiều NULL vẫn được phép, nhưng cùng một credential không được thuộc hai session.

Trước khi chạy trên DB đang dùng: sao lưu, xác nhận Flyway ở V9, thử trên bản sao và kiểm tra credential trùng. Truy vấn sau chỉ trả số lượng nhóm trùng, không in token:

```sql
SELECT COUNT(*) AS duplicate_refresh_groups
FROM (
  SELECT refresh_token FROM tokens
  WHERE refresh_token IS NOT NULL AND refresh_token <> ''
  GROUP BY refresh_token HAVING COUNT(*) > 1
) duplicates;
```

Nếu có nhóm trùng khác rỗng, phải xử lý các phiên bất thường theo chính sách thu hồi trước migration; không tự chọn một owner cho credential đó. DDL MySQL có thể áp dụng từng bước, nên cần kiểm tra schema/history nếu migration bị gián đoạn. Không sửa V0–V9 đã áp dụng.

## Cách kiểm chứng

```powershell
mvn -B '-Dtest=RefreshTokenLifecycleTest,UserLifecycleTest,RefreshMigrationTest,RateLimitInterceptorTest' test
mvn -B clean verify
```

RefreshTokenLifecycleTest dùng filter chain, service transaction, JWT ký thật và DB H2; không mock TokenService. Có ca hết hạn access, rotation/replay, cạnh tranh, revoked/expired/blocked/deleted, thu hồi khi đổi/reset mật khẩu và block rồi enable lại. RefreshMigrationTest chạy SQL V10 trên fixture schema cũ để kiểm tra credential được giữ, giá trị rỗng được chuẩn hóa, unique index và cột access đủ dài.

Docker engine chưa chạy trong lần này; migration và khóa cạnh tranh chưa được chạy lại trên MySQL thật. Test H2 không thay thế bước đó. Chưa xác nhận CI/deploy, chưa chạy migration vào DB đang dùng.

## Những phần còn mở

F04 chưa hoàn tất: token vẫn lưu credential dạng rõ trong DB; chưa có refresh-token family để thu hồi toàn bộ chuỗi khi phát hiện replay, logout riêng hoặc giao diện quản lý phiên. Cần rà thêm liên kết social account, quyền service user/comment/avatar/payment và actuator/CORS trước demo public. Giới hạn này không làm thay đổi việc token cũ hiện đã bị từ chối sau rotation.

### Kết quả local

- Ngày 28/09/2026, Windows, Temurin Java 21.0.9, Maven 3.9.11, Spring Boot 3.3.5.
- mvn -B clean verify: BUILD SUCCESS; 150 test, 0 failures/errors/skipped.
- Service line coverage: 617/807 = 76.46%, đạt gate 70%.
- Log: target/refresh-verify.log; báo cáo: target/surefire-reports và target/site/jacoco/index.html.
- Chưa commit/push/deploy; không migrate DB đang dùng.
