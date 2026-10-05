# Nâng cấp tiền thập phân — 27/09/2026

[Kế hoạch](00-ke-hoach-fresher.md) · [P0](01-p0-tinh-dung-va-bao-mat.md)

## Vấn đề đã sửa

DB trước đây đã có DECIMAL, nhưng entity/DTO/response dùng Float; OrderService tính BigDecimal rồi gọi floatValue, coupon đi qua double. Vì vậy lưu DECIMAL chưa đủ bảo đảm giá trị chính xác trên toàn luồng.

Giờ giá, tổng dòng, tổng đơn, DTO/response, kết quả coupon và email event đều dùng BigDecimal. Entity ghi rõ precision=19, scale=2; V9 mở rộng bốn cột tiền thành DECIMAL(19,2). Giá trị JSON vẫn là number, tên trường không đổi.

Ví dụ được kiểm thử: 9.999.999,99 × 3 = 29.999.999,97. Giá dòng đơn là snapshot; đổi giá sản phẩm sau đó không sửa tổng đơn đã tạo.

## Quy tắc tính tiền

- Tiền không âm, tối đa hai chữ số thập phân. Input có phần lẻ không biểu diễn được ở scale 2 bị từ chối, không tự làm tròn giá nhập.
- Giữ giới hạn giá sản phẩm hiện có: 0 đến 10.000.000. DTO yêu cầu price; service kiểm tra cả caller không qua HTTP.
- Tổng dòng = giá server × số lượng nguyên. Tổng trước coupon = tổng các dòng. Client total_money không quyết định số tiền.
- Coupon tính trực tiếp bằng BigDecimal, phần trăm nằm trong [0,100]. Làm tròn số phải trả HALF_UP về scale 2 sau mỗi điều kiện áp dụng.
- Điều kiện coupon áp dụng theo ID tăng dần; minimum_amount vẫn dùng dấu > và so với số tiền còn lại. Đây là chính sách giảm tuần tự, không phải cộng gộp phần trăm.
- Ví dụ: 19,99 × 3 = 59,97; giảm 12,5% → 52,47. Ba lần giảm 10% từ 100 → 90 → 81 → 72,90.
- Email hiển thị hai chữ số thập phân. Chính sách scale 2 giữ tương thích dữ liệu DECIMAL hiện có; không đồng nghĩa đã hoàn thiện quy tắc thu tiền VND qua cổng thanh toán.
- Dòng giỏ trùng sản phẩm được cộng bằng addExact; tràn số lượng trả lỗi đầu vào trước khi ghi tồn kho.

Money là nơi tập trung range/scale/rounding. Không dùng new BigDecimal(double); khởi tạo từ chuỗi hoặc giá trị nguyên. Khi so sánh giá trị trong test dùng isEqualByComparingTo để không nhầm khác scale với khác số tiền.

## Cache và triển khai

Cache sản phẩm dùng prefix products:money-v2:page: để không đọc lại JSON đã từng được tạo từ Float. Key cũ hết hạn theo TTL, không cần xóa toàn Redis. Invalidation vẫn giới hạn products:*.

### Trước khi migrate DB đang có dữ liệu

1. Sao lưu DB và thử trên bản sao. Không chạy migration vào production chỉ dựa trên test H2.
2. Xác nhận Flyway history ở V8 và schema thực tế khớp các migration V0–V8. Không sửa checksum migration cũ.
3. Kiểm tra precision/scale và dữ liệu bất thường bằng các truy vấn đọc dưới đây.
4. Chạy V9 trên bản sao, so sánh số dòng, giá trị mẫu/biên và tổng tiền trước/sau.
5. Sau đó mới triển khai app mới với cửa sổ bảo trì phù hợp. MySQL DDL không có rollback nguyên khối như transaction nghiệp vụ; nếu lỗi giữa các ALTER phải kiểm tra schema/history trước khi xử lý tiếp.

```sql
SELECT table_name, column_name, data_type, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'products' AND column_name = 'price')
    OR (table_name = 'orders' AND column_name = 'total_money')
    OR (table_name = 'order_details' AND column_name IN ('price', 'total_money')));

SELECT id, price FROM products WHERE price IS NULL OR price < 0 OR price > 10000000;
SELECT id, total_money FROM orders WHERE total_money IS NULL OR total_money < 0;
SELECT id, price, total_money FROM order_details
WHERE price IS NULL OR total_money IS NULL OR price < 0 OR total_money < 0;
```

V9 chỉ mở rộng precision, giữ scale, nullability và default theo V8. Nó không tự sửa tiền âm/null, không tính lại đơn lịch sử và không khôi phục độ chính xác đã mất từ Float. Nếu DB thực tế dùng FLOAT/DOUBLE thay vì schema versioned, cần kế hoạch đối soát/chuyển đổi riêng trước khi áp dụng.

Không tự hạ cột về precision cũ sau khi có dữ liệu mới vượt phạm vi cũ. Rollback app cũng cần tránh bản cũ đọc lại số lớn qua Float.

## Kiểm chứng và giới hạn

```powershell
mvn -B clean verify
# Khi MySQL/Redis test qua Docker đã sẵn sàng:
mvn -B verify -Pintegration
```

Các ca mới bao gồm tiền lẻ, giới hạn giá HTTP, scale sai, coupon tại ngưỡng, làm tròn, cache JSON, snapshot giá, rollback coupon lỗi và tràn số lượng. Test migration chạy SQL V9 trên fixture cột tiền V8 bằng H2 MySQL mode, kiểm tra giữ dữ liệu/NULL và phạm vi mới; đây không phải toàn bộ chuỗi Flyway trên MySQL.

OrderConcurrencyTest có các ca tiền mới; InfrastructureIT kế thừa chúng để chạy trên MySQL/Redis khi bật integration. Docker engine chưa hoạt động ở lần thực hiện này, nên chưa xác minh V0–V9 trên DB MySQL mới hoặc nâng cấp bản sao V8 bằng MySQL thật.

F02 đã tiến thêm phần tiền và regression test, chưa đóng toàn bộ: còn rà soft delete/cancel/refund và kiểm chứng migration/cạnh tranh trên MySQL. VNPay hiện vẫn là luồng thử nghiệm nhận amount riêng, chưa dùng payment attempt gắn order; việc hoàn thiện thuộc F03.

### Kết quả local sau thay đổi

- Temurin Java 21.0.9, Maven 3.9.11, Spring Boot 3.3.5, Windows; working tree chưa commit.
- mvn -B clean verify: BUILD SUCCESS; 134 test, 0 failures/errors/skipped.
- Service line coverage: 598/801 = 74.66%, đạt gate 70%.
- Log: target/money-final-verify.log; reports: target/surefire-reports và target/site/jacoco/index.html.
- Không chạy migrate DB đang dùng; không commit/push/deploy.
