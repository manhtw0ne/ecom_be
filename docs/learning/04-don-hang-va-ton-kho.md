# 04 — Đơn hàng, tiền, transaction và tồn kho

[Lộ trình](00-lo-trinh.md) · Trước: [Xác thực](03-xac-thuc-va-phan-quyen.md) · Tiếp: [Tích hợp](05-cache-va-tich-hop.md)

## Mục tiêu

Giải thích tính đúng đắn khi thất bại giữa chừng hoặc nhiều khách mua đồng thời. Bắt đầu bằng MySQL transaction/khóa dòng, thêm Redis lock sau khi hiểu lý do.

Đọc [OrderService](../../src/main/java/com/manh/ecom_be/services/orders/OrderService.java), [ProductRepository](../../src/main/java/com/manh/ecom_be/repositories/ProductRepository.java), [OrderRepository](../../src/main/java/com/manh/ecom_be/repositories/OrderRepository.java), [OrderConcurrencyTest](../../src/test/java/com/manh/ecom_be/services/orders/OrderConcurrencyTest.java).

## 1. Điều kiện luôn phải đúng

- Owner lấy từ authentication; giỏ không rỗng, quantity dương.
- Giá lấy từ DB; server tính tổng, lưu giá/số lượng tại thời điểm mua.
- Kho không âm; đơn và trừ kho cùng thành công hoặc cùng rollback.
- Hủy hợp lệ hoàn kho một lần.
- Tạo đơn, thanh toán và giao hàng là ba khái niệm khác nhau.

Ví dụ: kho 5, đặt 2 × 100000, client gửi tổng 1. Server phải tính 200000, kho còn 3. Nếu giỏ có thêm sản phẩm không tồn tại, phần trừ kho trước đó phải rollback.

## 2. Xây một transaction

1. DTO chỉ nhận trường client được quyết định; tránh dùng một DTO cho mọi kiểu cập nhật.
2. Gộp dòng cùng productId, kiểm tra tràn số khi cộng quantity.
3. Load theo thứ tự ID ổn định; kiểm tra và trừ kho.
4. Tính tiền BigDecimal, áp coupon ở server theo điều kiện nghiệp vụ.
5. Lưu Order/OrderDetail cùng transaction, chọn rollback rule phù hợp.
6. Tác dụng phụ cần dữ liệu đã lưu chỉ chạy sau commit.

Đặt breakpoint để phân biệt save, flush và commit. Tìm hiểu Spring proxy: lời gọi nội bộ trong cùng object không tự tạo ranh giới transaction mới như có thể tưởng.

Repo dùng BigDecimal ở một phần phép tính nhưng chuyển về Float trong entity/detail/tổng tiền. Bản học nên dùng kiểu tiền nhất quán; chuyển toàn repo là việc riêng trong kế hoạch nâng cấp.

## 3. Tái hiện rồi sửa cạnh tranh

Tạo test hai luồng cùng mua hàng tồn 1, dùng latch để bắt đầu gần cùng lúc. Nếu cả hai đọc tồn 1 trước khi ghi, chỉ đặt @Transactional chưa đủ chứng minh không oversell.

Chọn một cách rõ ràng:

- Khóa dòng khi đọc để cập nhật trong transaction; hoặc
- UPDATE có điều kiện stock_quantity >= quantity, kiểm tra số hàng cập nhật.

Test lại trên MySQL. Sau đó đọc cách repo kết hợp findByIdForUpdate, Redisson, sắp ID và giữ Redis lock đến transaction kết thúc. Giải thích vì sao mở khóa trước commit có thể cho request khác vào quá sớm, và synchronized không khóa được process khác.

Không coi distributed lock là bảo đảm tuyệt đối: phải xét timeout, kết nối, DB transaction và mọi đường ghi khác.

## 4. Hủy/chuyển trạng thái

Luồng hiện có trong OrderService:

```text
pending -> processing -> shipped -> delivered
pending -> cancelled
processing -> cancelled
```

1. Khóa dòng order trước quyết định chuyển trạng thái.
2. Từ chối chuyển ngược/hủy sau shipped hoặc delivered theo quy tắc hiện tại.
3. Hoàn kho trong cùng transaction, theo thứ tự ổn định.
4. Gửi hai lệnh hủy đồng thời; kho chỉ tăng một lần.

Service hiện trả sớm khi trạng thái mới giống cũ; controller hủy có thể từ chối yêu cầu lặp. Idempotency về dữ liệu không đồng nghĩa HTTP response luôn giống nhau.

## 5. Bài nâng cao

Thiết kế idempotency key cho tạo đơn: request gửi lại không tạo đơn thứ hai. Quy định key theo user, unique constraint, kiểm tra payload và cách trả kết quả cũ. Khóa sản phẩm không tự giải quyết việc bấm đặt hàng hai lần.

Đọc CouponService; test mã không tồn tại/vô hiệu/không thỏa điều kiện. Nếu muốn giới hạn lượt dùng, bổ sung thiết kế/test cạnh tranh, không chỉ kiểm tra một request.

## Nghiệm thu và tự vấn

- [ ] Test giỏ rỗng, quantity sai, dòng trùng, tổng giả.
- [ ] Lỗi ở sản phẩm thứ hai rollback cả đơn và phần kho đã trừ.
- [ ] 20 request mua một đơn vị, kho 5: đúng 5 đơn thành công, tồn 0.
- [ ] Hủy đồng thời hoàn kho một lần; chuyển trạng thái sai bị chặn.
- [ ] Test MySQL thật và giải thích vì sao Mockito không chứng minh row lock.

**Câu hỏi:** ACID trong ví dụ này? Deadlock xảy ra thế nào? Khóa bi quan khác optimistic locking? Vì sao email ngoài transaction DB? Giá snapshot dùng để làm gì?
