# 02 — Catalog, hợp đồng API, lỗi và ảnh

[Lộ trình](00-lo-trinh.md) · Trước: [Nền tảng](01-nen-tang-va-du-lieu.md) · Tiếp: [Xác thực](03-xac-thuc-va-phan-quyen.md)

## Mục tiêu

Làm một chức năng từ đầu đến cuối. Phân biệt validation dữ liệu (tên không rỗng) với nghiệp vụ (danh mục có tồn tại, sản phẩm đã bán có được xóa).

Đọc [CategoryController](../../src/main/java/com/manh/ecom_be/controllers/CategoryController.java), [ProductService](../../src/main/java/com/manh/ecom_be/services/product/ProductService.java), [ProductRepository](../../src/main/java/com/manh/ecom_be/repositories/ProductRepository.java), [GlobalExceptionHandler](../../src/main/java/com/manh/ecom_be/exceptions/GlobalExceptionHandler.java). Chưa sao chép Redis/Kafka.

## 1. Hoàn thành Category

1. Tạo DTO chỉ có dữ liệu client được gửi; gắn validation, gọi `@Valid` tại HTTP.
2. Viết entity/repository và service create/get/update/delete.
3. Viết controller với status/body thống nhất; dùng response riêng khi entity có trường không nên lộ.
4. Tạo exception nghiệp vụ và xử lý tập trung. Không bắt mọi Exception rồi trả 500 ở từng controller.
5. Test service bằng repository giả lập và test HTTP để biết validation thực sự chạy.

Hợp đồng **đề xuất cho bản học**:

| Ca | HTTP | Điều kiện |
| --- | --- | --- |
| Tạo hợp lệ | 201 | DB có bản ghi và ID |
| Tên rỗng/quá dài | 400 | Không ghi DB |
| ID không tồn tại | 404 | Không NullPointerException |
| Xóa danh mục đang có sản phẩm | 409 nếu chọn cấm xóa | Giữ dữ liệu liên quan |

Ghi quy tắc xóa trước khi code. Repo hiện có chỗ body báo created nhưng HTTP trả 200; cần đối chiếu chứ không sao chép nguyên trạng.

## 2. Product và phân trang

1. DTO có tên, giá, mô tả, categoryId; bản học dùng BigDecimal/DECIMAL cho tiền.
2. Service kiểm tra category khi tạo/đổi danh mục.
3. Viết lấy chi tiết và danh sách: page bắt đầu từ 0, limit có giới hạn trên; thêm ID vào sort để ổn định.
4. Thêm keyword/category filter và test khi dùng cùng lúc; whitelist trường sort được hỗ trợ.
5. Trả metadata page, size, totalElements, totalPages, kể cả khi rỗng.

**Bài tập:** seed 23 sản phẩm. `page=2&limit=10` cho 3 phần tử, tổng 23 và 3 trang. Page âm, limit 0 hoặc quá lớn phải theo hợp đồng đã định, không thành 500.

## 3. Hiểu JPA

Đặt breakpoint trong transaction; thay entity đã load và quan sát SQL khi flush/commit. Giải thích dirty checking, lazy loading và vì sao serialize entity có thể tải thêm dữ liệu/tạo vòng lặp.

Với danh sách kèm category, đếm query khi trả 1 và 20 sản phẩm. Nếu có N+1, thử projection hoặc fetch có chủ đích rồi đo lại; không đổi mọi quan hệ thành EAGER.

## 4. Upload ảnh sau khi CRUD ổn

Tham chiếu [FileUtils](../../src/main/java/com/manh/ecom_be/utils/FileUtils.java) và [ProductImageService](../../src/main/java/com/manh/ecom_be/services/product/image/ProductImageService.java).

1. Định nghĩa dung lượng, loại ảnh, số ảnh tối đa và quyền ghi/xóa.
2. Kiểm tra nội dung/MIME và kích thước; đuôi file không đủ chứng minh là ảnh hợp lệ.
3. Sinh tên lưu trữ ở server; không ghép nguyên đường dẫn client gửi vào filesystem.
4. Lưu metadata; xử lý trường hợp ghi file thành công nhưng DB thất bại.
5. Chọn 404 hoặc ảnh thay thế khi file mất, rồi test đúng hợp đồng.

Sau bài 03, khóa API ghi catalog/upload bằng quyền admin. CRUD chưa Security chỉ là bài học local.

## 5. Bổ sung nghiệp vụ sau bài 03–04

Làm Comment, Coupon rồi Favorite nếu cần. Với mỗi tính năng, viết quy tắc: ai sửa bình luận; coupon vô hiệu/không đạt điều kiện xử lý ra sao; user có được favorite hai lần? Có entity/repository chưa có nghĩa đã có tính năng HTTP hoàn chỉnh.

## Nghiệm thu và tự vấn

- [ ] Test CRUD, ID không tồn tại, quan hệ không hợp lệ.
- [ ] HTTP test chứng minh dữ liệu sai không đến bước ghi DB.
- [ ] Filter/phân trang đúng trên dữ liệu biết trước.
- [ ] Upload có test rỗng, quá lớn, sai loại, lỗi lưu metadata.
- [ ] Tự phân biệt lỗi mapping, service và SQL.

**Câu hỏi:** `@Valid` làm gì? Vì sao vẫn cần constraint? PUT khác PATCH? 400 khác 404/409? Vì sao unit test service không chứng minh HTTP status đúng?
