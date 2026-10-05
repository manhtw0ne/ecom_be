# 14 — Lưu tên và thumbnail tại thời điểm đặt hàng

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 30/09/2026

## Bài toán

Chi tiết đơn đã giữ giá tại lúc mua nhưng tên và thumbnail trong `OrderDetailResponse` vẫn đọc từ Product hiện tại. Đổi tên sản phẩm khiến đơn cũ đổi tên theo. Nay checkout ghi thêm `product_name_snapshot` và `product_thumbnail_snapshot`, lấy từ sản phẩm server đọc dưới khóa, cùng transaction trừ tồn kho.

```java
orderDetail.setProductNameSnapshot(product.getName());
orderDetail.setProductThumbnailSnapshot(product.getThumbnail());
```

Ví dụ mua “Áo xanh” giá 100; sau đó sản phẩm đổi thành “Áo mùa hè” giá 150. Response chi tiết đơn vẫn hiển thị “Áo xanh”, giá 100. Nếu lúc mua chưa có ảnh, thumbnail của đơn vẫn null dù sản phẩm có ảnh sau này. Hai cột đánh dấu `updatable = false` để Hibernate không cập nhật snapshot qua UPDATE entity; đây không phải ràng buộc chống mọi sửa đổi bằng SQL trực tiếp.

## Migration V11 và tương thích

V11 thêm hai cột nullable rồi backfill bằng tên/thumbnail hiện có trong bảng products, kể cả sản phẩm đã xóa mềm. Đây là thông tin tốt nhất hiện có khi chạy migration, **không phải phục dựng lịch sử mua hàng**. Dòng không còn sản phẩm tham chiếu không thể backfill.

`OrderDetailResponse` ưu tiên snapshot; nếu tên snapshot null thì dùng dữ liệu sản phẩm theo hành vi legacy. Dùng tên snapshot làm dấu hiệu phân biệt để thumbnail null đã chụp không bị thay bằng ảnh mới. API chi tiết đơn giữ nguyên các trường `product_name`, `thumbnail`, `price`. Các response trả raw entity vẫn có thể chứa quan hệ Product hiện tại; client cần dùng DTO chi tiết đơn để hiển thị thông tin lịch sử.

## Phạm vi và giới hạn

- Snapshot lưu **đường dẫn ảnh**, không sao chép nội dung file. Xóa file nguồn có thể khiến ảnh lịch sử không tải được; cần chính sách lưu trữ ảnh riêng nếu muốn bảo toàn ảnh lâu dài.
- Chưa bỏ chặn xóa sản phẩm đã phát sinh đơn: quan hệ Product vẫn được dùng ở các luồng đọc/hủy đơn. Snapshot này chưa làm đơn hàng hoàn toàn độc lập với sản phẩm.
- Không thay giá, số lượng, tổng tiền hoặc tồn kho của đơn cũ trong migration.
- Chưa chạy migration trên MySQL thật. Nên thử trên bản sao dữ liệu trước triển khai; backfill nhiều dòng có thể giữ khóa lâu.

## Kiểm thử và tự học

Test checkout qua HTTP rồi đổi tên/giá/ảnh, clear persistence context và đọc lại chi tiết đơn; kiểm tra cả trường hợp lúc mua có ảnh và chưa có ảnh. Test legacy kiểm tra fallback. Migration test chạy SQL V11 trên H2 MySQL mode, gồm sản phẩm active/ẩn, ảnh null và bảo toàn giá.

Xác minh local 30/09/2026: `mvn -B clean verify` BUILD SUCCESS, **200 tests, 0 failures, 0 errors, 0 skipped**. Coverage dòng service **700/879 = 79,64%**, vượt gate 70%. `git diff --check -- src docs/upgrades` không báo lỗi. Log: `target/snapshot-verify.log`; báo cáo ở `target/surefire-reports` và `target/site/jacoco/index.html`.

Tự giải thích: vì sao dữ liệu giao dịch cần snapshot; vì sao thumbnail null không đồng nghĩa thiếu snapshot; và khác biệt giữa lưu đường dẫn ảnh với lưu bản sao ảnh?
