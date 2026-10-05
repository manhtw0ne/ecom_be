# 13 — Xóa mềm sản phẩm có kiểm soát

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 29/09/2026

## Vấn đề và cách sửa

`Product` đã có `@SQLDelete`, nhưng quan hệ tới ảnh/bình luận/lượt thích dùng `CascadeType.ALL`. Gọi repository.delete() vẫn có thể cascade REMOVE xuống các quan hệ trước khi câu SQL xóa mềm sản phẩm chạy. Điều đó không phù hợp với mong muốn chỉ ẩn sản phẩm.

Luồng DELETE hiện kiểm tra admin tại service, khóa hàng sản phẩm giống upload/sửa/xóa ảnh, rồi cập nhật `deleted = true` bằng saveAndFlush. Không gọi remove, không xóa file và không xóa metadata ảnh. File được giữ theo chính sách lưu trữ của sản phẩm ẩn, không được coi là file mồ côi để dọn tự động.

```java
var product = productRepository.findByIdForUpdate(id).orElseThrow(...);
// Refresh dưới khóa, kiểm tra tham chiếu đơn hàng.
product.setDeleted(true);
productRepository.saveAndFlush(product); // không cascade REMOVE
```

Repository có `@SQLRestriction("is_deleted = 0")`, nên sản phẩm đã ẩn không được tìm thấy qua đường truy vấn thông thường. Upload ảnh vào sản phẩm này bị từ chối. Rollback transaction giữ sản phẩm còn hiển thị. Đây chưa phải API khôi phục hoặc xóa vĩnh viễn.

## Bảo vệ đơn hàng

OrderDetailResponse hiện đọc tên/thumbnail trực tiếp từ Product. Ẩn sản phẩm đã có trong order_details có thể khiến truy vấn lịch sử không tải được quan hệ. Vì vậy DELETE trả 400 nếu sản phẩm có bất kỳ chi tiết đơn hàng nào tham chiếu, kể cả đơn đã hủy; sản phẩm giữ nguyên.

Đây là ràng buộc nghiệp vụ bảo thủ trước khi tách lịch sử đơn khỏi quan hệ Product và bổ sung trạng thái ngừng bán riêng. Cập nhật 30/09: [bài 14](14-snapshot-san-pham-trong-don.md) đã lưu snapshot tên/đường dẫn ảnh cho checkout và DTO chi tiết đơn; chưa tách mọi luồng đọc khỏi Product nên vẫn giữ chặn xóa. Dữ liệu sản phẩm đã bị ẩn từ trước chưa được khôi phục tự động.

## Hợp đồng và kiểm thử

- Admin xóa sản phẩm chưa phát sinh đơn: 200; dữ liệu và ảnh được giữ, sản phẩm bị ẩn.
- ID thiếu hoặc đã ẩn: 404.
- Sản phẩm được order_details tham chiếu: 400.
- Gọi service bằng user thường: từ chối quyền.

Test tích hợp kiểm tra API xóa, giữ bản ghi/file ảnh, xóa lặp, upload sau xóa, rollback, tham chiếu đơn hàng và quyền service. Test database dùng H2; chưa xác minh MySQL thật hoặc toàn bộ race giữa checkout và DELETE.

Xác minh local: `mvn -B verify` BUILD SUCCESS, **196 tests, 0 failures, 0 errors, 0 skipped**. Coverage dòng service **698/877 = 79,59%**, vượt gate 70%. Lần clean verify trước đó phát hiện lỗi fixture principal; đã sửa và chạy lại toàn bộ test. `git diff --check -- src docs/upgrades` không báo lỗi. Log: `target/product-delete-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Tự học: phân biệt SQLDelete và cascade REMOVE; giải thích tại sao ẩn sản phẩm không đồng nghĩa xóa file; thiết kế snapshot đơn hàng cần lưu những trường nào?
