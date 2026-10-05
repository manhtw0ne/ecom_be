# 01 — Nền tảng Java, HTTP, Spring và dữ liệu

[Lộ trình](00-lo-trinh.md) · Tiếp: [Catalog](02-catalog-va-hop-dong-api.md)

## Mục tiêu và chuẩn bị

Dùng được terminal, Git và đọc lỗi biên dịch. Repo khai báo Java 21, Spring Boot 3.3.5 trong [pom.xml](../../pom.xml): đây là phiên bản đang dùng, không phải tuyên bố phiên bản mới nhất.

## 1. Học Java bằng giỏ hàng console

Chưa dùng Spring, tự tạo `Product`, `CartItem`, `CartCalculator`:

1. Nhận `List<CartItem>`; dùng `Map<Long, Integer>` gộp số lượng theo productId.
2. Từ chối số lượng âm/0, thiếu sản phẩm và tràn số khi cộng.
3. Tính tiền bằng `BigDecimal` tạo từ chuỗi; định nghĩa quy tắc làm tròn.
4. Viết interface kho sản phẩm, implementation dùng bộ nhớ; truyền dependency qua constructor.
5. Ném exception khi không tìm thấy sản phẩm và viết JUnit kiểm tra.

Bạn đang học class/interface, collection, generic, exception và dependency injection qua bài toán thật. Viết vòng lặp trước khi đổi sang Stream.

**Bài kiểm tra:** giá 199000 × 2 cộng 35000 × 3 phải là 503000. Vì sao giá trong chi tiết đơn không nên thay đổi khi giá catalog đổi?

## 2. Hiểu HTTP trước annotation

Mô tả request gồm method, URL, header, body; response gồm status, header, body. Phân biệt JSON truyền trên mạng với object Java sau deserialize.

Viết hợp đồng dự kiến cho `POST /api/v1/categories`, body:

```json
{"name":"Bàn phím"}
```

Quy ước của project học: tạo thành công trả 201, tên rỗng trả 400, ID không tồn tại trả 404. Sau bài 03, API ghi phải có quyền admin. Đây là hợp đồng sẽ xây, không khẳng định mọi endpoint hiện tại đã đúng như vậy.

## 3. Thiết kế database trước entity

| Bảng | Trách nhiệm | Quan hệ |
| --- | --- | --- |
| roles, users | Tài khoản/vai trò | Nhiều user thuộc một role |
| categories, products | Catalog | Nhiều product thuộc một category |
| orders, order_details | Đơn và giá/số lượng lúc mua | Order thuộc user; detail thuộc order/product |
| tokens | Phiên/token | Nhiều token thuộc user |

Chỉ tạo category/product ở bài đầu; thêm bảng khi học chức năng tương ứng. Với mỗi cột, ghi rõ null, unique, độ dài và miền giá trị. FK ngăn dữ liệu mồ côi. Unique constraint vẫn cần khi đã kiểm tra trùng ở service vì hai request có thể đến đồng thời.

Tự viết SQL:

- JOIN sản phẩm với tên danh mục.
- Lọc theo danh mục, sort giá rồi ID và phân trang.
- Sau bài 04: tổng tiền theo đơn, số đơn theo user, sản phẩm chưa bán.
- Dùng `EXPLAIN` đọc một truy vấn; chỉ thêm index khi biết truy vấn cần tối ưu.

## 4. Khởi tạo Spring tối thiểu

1. Tạo project Maven dùng Java 21 và bộ phiên bản nhất quán; có thể theo pom tham chiếu để tái lập.
2. Thêm Web, Validation, Data JPA, MySQL, Flyway, Test. Hoãn Security đến bài 03.
3. Tạo package controllers, dtos, services, repositories, models, responses, exceptions.
4. Viết endpoint đơn giản, đặt breakpoint ở controller để thấy request được xử lý.
5. Kết nối database học tập riêng, đọc thông tin kết nối từ biến môi trường.

Controller xử lý HTTP; service giữ nghiệp vụ; repository truy cập DB; entity biểu diễn dữ liệu lưu; DTO/response giới hạn dữ liệu nhận/trả. Không cần tạo interface cho mọi class chỉ để giống repo.

## 5. Migration

Schema tham chiếu bắt đầu tại [V0__initial_schema.sql](../../src/main/resources/db/migration/V0__initial_schema.sql), tiếp theo là V1–V8 trong [thư mục migration](../../src/main/resources/db/migration). Đọc theo thứ tự và ghi mỗi file thay đổi gì.

Trong project học:

1. Viết migration đầu cho category/product, chạy trên DB mới.
2. Kiểm tra `flyway_schema_history` và schema thật.
3. Đến bài 04, bổ sung tồn kho bằng migration mới.
4. Khởi động lại, xác nhận migration cũ không chạy lại.
5. Không sửa migration đã áp dụng/chia sẻ; thêm file mới. Database cũ cần đối chiếu lịch sử trước khi đặt baseline.

Không dùng `ddl-auto=update` để che lỗi migration. Test H2 không chứng minh SQL MySQL tương thích.

## Nghiệm thu và tự vấn

- [ ] Bộ tính tiền console có test.
- [ ] DB mới được tạo bằng migration; khởi động lại không lỗi.
- [ ] Giải thích PK, FK, unique, index bằng schema của mình.
- [ ] Biết profile đang chạy và cấu hình lấy từ đâu.
- [ ] Trace được request từ controller đến SQL.

**Câu hỏi:** DI giúp test thế nào? Interface khác class ra sao? `equals/hashCode` liên quan gì đến Map? Vì sao cần DTO? JPA có thay kiến thức SQL không? Migration hơn sửa DB bằng tay ở đâu?
