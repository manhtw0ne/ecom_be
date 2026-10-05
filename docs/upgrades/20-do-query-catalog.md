# 20 — Đo và giảm query catalog

[Tổng kế hoạch](00-ke-hoach-fresher.md) · 03/10/2026

## Điều kiện đo

`CatalogQueryBudgetTest`: Spring Boot thật, H2 test profile, cache Redis tắt, một transaction test. Tạo 12 sản phẩm, mỗi sản phẩm thuộc category riêng và có một ảnh; không có comment/favorite. Flush và clear EntityManager trước đo; reset Hibernate Statistics, gọi service tìm theo marker riêng với page=0, limit=10, sort=id rồi serialize toàn bộ content thành JSON.

Đếm `prepareStatementCount` sau serialize, không chỉ query repository. Baseline **42 SQL**: 1 query sản phẩm + 1 count + 10 category + 10 ảnh + 10 collection comment rỗng + 10 collection favorite rỗng. Collection rỗng vẫn có thể gây N+1 vì Hibernate cần query để biết nó rỗng.

## Sửa như thế nào?

```java
@EntityGraph(attributePaths = "category")
Page<Product> searchProducts(...);

@BatchSize(size = 100)
private List<ProductImage> productImages;
```

EntityGraph tải quan hệ to-one category trong query sản phẩm. BatchSize áp dụng cho ba collection ảnh/comment/favorite để Hibernate tải các collection cùng loại theo lô. Không join-fetch collection trong query phân trang nên tránh nhân dòng làm sai page. Giữ nguyên schema response.

Test sau sửa yêu cầu không quá 5 SQL trên fixture này, đồng thời kiểm tra 10 kết quả, tổng 12 sản phẩm, 2 trang và ảnh có trong JSON. Chạy lại bằng `mvn -B -Dtest=CatalogQueryBudgetTest test`; xem dòng `CATALOG_QUERY_COUNT`.

## Giới hạn của bằng chứng

Đây là phép đo SQL, **không phải benchmark latency hoặc throughput**, và chưa có EXPLAIN MySQL. Không suy ra p95 hay tốc độ thực tế từ số câu SQL. Dữ liệu có nhiều comment/favorite có thể thêm query tải tác giả và tăng payload; ngưỡng 5 không được tuyên bố áp dụng cho mọi dataset. API vẫn trả các collection hiện có; tách list DTO gọn là lựa chọn tiếp theo nếu có nhu cầu đo được.

Docker vẫn không kết nối được ở lần kiểm tra 03/10/2026. Integration MySQL/Redis, smoke full stack và CI remote vẫn chưa được xác minh.
