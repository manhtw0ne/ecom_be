# 16 — Quyền ghi sản phẩm và danh mục tại service

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 02/10/2026

## Vì sao cần kiểm tra thêm?

Controller đã yêu cầu ADMIN khi tạo/sửa sản phẩm và tạo/sửa/xóa danh mục. Tuy nhiên gọi service trực tiếp từ một luồng nội bộ khác có thể bỏ qua annotation của controller. Nay các phương thức ghi này gọi `securityUtils.requireAdmin()` trước khi đọc hoặc sửa dữ liệu. Xóa sản phẩm đã có kiểm tra tương tự từ bài 13.

```java
public Category updateCategory(long id, CategoryDTO dto) {
    securityUtils.requireAdmin();
    // Chỉ sau khi xác nhận quyền mới tìm và sửa entity.
    ...
}
```

Ví dụ USER gọi `productService.updateProduct(...)` trực tiếp sẽ nhận AccessDeniedException, không đổi tên/giá/tồn kho. Anonymous, tài khoản không active và tài khoản đã xóa cũng bị từ chối, kể cả principal có role ADMIN. ADMIN active vẫn tạo/sửa sản phẩm và CRUD danh mục theo nghiệp vụ hiện có. Đường đọc public không bị thêm yêu cầu ADMIN.

Đây là bảo vệ tại service dựa trên principal đã xác thực. Nếu bổ sung scheduled job hoặc consumer ghi catalog, cần thiết kế identity/quyền riêng cho luồng đó; không dùng cách tự gắn ADMIN tùy ý vào request người dùng. Kiểm tra này không thay thế validation dữ liệu hoặc bảo vệ tài khoản quản trị.

## Kiểm thử

`CatalogServiceAuthorizationTest` dùng service Spring thật và H2, kiểm tra sáu thao tác ghi với bốn trạng thái bị cấm; xác nhận số bản ghi, tên, tồn kho không đổi. Có thêm luồng ADMIN thành công và đọc anonymous. Các unit test nghiệp vụ vẫn mock SecurityUtils; bằng chứng về quyền nằm ở integration test này.

Test cạnh tranh chọn/xóa thumbnail được cập nhật để thiết lập và dọn principal riêng trên worker thread: SecurityContext không tự truyền sang thread mới.

Xác minh local 02/10/2026: `mvn -B clean verify` BUILD SUCCESS, **218 tests, 0 failures, 0 errors, 0 skipped**. Coverage dòng service **710/884 = 80,32%**, vượt gate 70%. `git diff --check -- src docs/upgrades` không báo lỗi. Log: `target/catalog-auth-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

## Phạm vi còn mở

Chưa thay cơ chế Kafka của controller danh mục, lỗi/not-found của danh mục, phân trang danh mục hoặc xử lý xóa danh mục còn sản phẩm ẩn. Chưa kiểm chứng MySQL/Redis/Kafka thật. Thay đổi tập trung vào quyền ghi catalog; không tuyên bố toàn bộ service trong dự án đã được kiểm toán.

Tự học: giải thích khác biệt giữa quyền controller và service; vì sao unit test mock SecurityUtils không chứng minh phân quyền đúng; SecurityContext hoạt động thế nào khi chạy nhiều thread?
