# Nhóm 1 — Tự xây dựng lại Ecom Backend

## Đích đến

Bạn tự viết được một backend bán hàng nhỏ, truy vết được request từ HTTP đến SQL, kiểm thử được nghiệp vụ và giải thích được lựa chọn của mình. Không cần thuộc toàn bộ mã nguồn hay dùng hết thư viện ngay từ đầu.

Repository hiện tại là **tài liệu tham chiếu**, không phải đáp án luôn đúng. Những việc cần sửa trong dự án được tách sang [nhóm 2 — nâng cấp fresher](../upgrades/00-ke-hoach-fresher.md). Tài liệu được đối chiếu code ngày 23/09/2026; lần biên soạn này không chạy lại ứng dụng hay bộ test.

## Thứ tự học

| Bài | Nội dung | Sản phẩm tự làm được | Thời lượng gợi ý |
| --- | --- | --- | --- |
| [01](01-nen-tang-va-du-lieu.md) | Java, HTTP, SQL, Spring, migration | Project khởi động, schema do bạn giải thích được | 6–10 buổi |
| [02](02-catalog-va-hop-dong-api.md) | Catalog, validation, lỗi, upload | CRUD đầy đủ từ request đến DB và test | 5–8 buổi |
| [03](03-xac-thuc-va-phan-quyen.md) | JWT, refresh, role, ownership | Người dùng không đọc/sửa dữ liệu riêng của nhau | 5–8 buổi |
| [04](04-don-hang-va-ton-kho.md) | Tiền, transaction, tồn kho | Không oversell; rollback/hủy đơn đúng | 6–10 buổi |
| [05](05-cache-va-tich-hop.md) | Redis, email, Kafka, OAuth, VNPay | Hiểu mục đích và giới hạn từng tích hợp | 6–10 buổi |
| [06](06-kiem-thu-va-van-hanh.md) | Test, Docker, CI, log, metrics | Clone và chạy lại được, có bằng chứng test | 4–6 buổi |
| [07](07-ban-do-ma-nguon.md) | Bản đồ code và luồng hiện tại | Tự lần theo mã nguồn | Tra cứu xuyên suốt |

Một buổi khoảng 60–120 phút; đây là ước lượng để chia việc, không phải hạn chót. Nếu Java/SQL còn yếu, kéo dài bài 01. Viết test ngay trong mỗi bài; bài 06 ghép chúng thành quy trình hoàn chỉnh.

## Cách học để tự làm được

1. Tạo **repository học tập riêng**, giữ dự án hiện tại làm bản tham khảo.
2. Trước khi code, ghi đầu vào, đầu ra, ai được gọi và ba trường hợp lỗi.
3. Làm một chức năng xuyên suốt: DTO → controller → service → repository → DB → response. Đừng tạo tất cả controller rồi mới viết service.
4. Tự viết bản đơn giản trước. Khi bí, hỏi AI về một khái niệm hoặc một lỗi cụ thể; ghi giả thuyết của mình trước khi hỏi.
5. Đọc code tham chiếu, đóng lại và tự viết lại. Chỉ giữ đoạn code bạn giải thích được.
6. Gây lỗi có chủ đích: dữ liệu sai, tài khoản khác, tắt Redis, hai request đồng thời. Viết test cho hành vi cần giữ.
7. Commit theo chức năng đã kiểm chứng và ghi nhật ký ngắn.

Mẫu nhật ký:

```text
Chức năng / quy tắc nghiệp vụ:
Luồng request và câu SQL dự kiến:
Giả thuyết khi gặp lỗi:
Thay đổi đã thử và kết quả:
Test chứng minh:
Điều chưa hiểu / giới hạn còn lại:
```

## Xây theo vòng

- **Vòng A — lõi:** Category, Product, User, đăng nhập, ownership, Order, OrderDetail, tồn kho và test. Chỉ cần MySQL.
- **Vòng B — nghiệp vụ bổ sung:** ảnh, coupon, bình luận; favorite nếu bạn cần.
- **Vòng C — tích hợp:** Redis/email trước; Kafka/OAuth/VNPay sau. Mỗi tích hợp cần một lý do và một kịch bản lỗi.
- **Vòng D — tái lập và trình bày:** Docker, CI, log, metrics, demo, hồ sơ dự án.

Trong project học, chỉ thêm dependency của bài đang làm. Repo tham chiếu đã có nhiều bean phụ thuộc Redis/Kafka; không giả định xóa vài cấu hình là chạy được chỉ với MySQL.

## Điều kiện chuyển bài

- [ ] Viết lại phần chính mà không nhìn bản tham chiếu.
- [ ] Giải thích request đi qua lớp nào, transaction nằm ở đâu.
- [ ] Có test ca đúng, dữ liệu sai và nghiệp vụ thất bại.
- [ ] Tự sửa được một lỗi và giải thích nguyên nhân.
- [ ] Nói được một hạn chế của thiết kế.

Nếu chưa đạt, thu nhỏ chức năng và làm lại. Số công nghệ không thay thế khả năng giải thích.

## Cấu trúc và tài liệu cũ

- `docs/learning/`: nhóm 1, học và xây lại theo thứ tự 00–07.
- `docs/upgrades/`: nhóm 2, sửa/nâng cấp/nghiệm thu cho dự án hiện tại.
- `docs/images/`: ảnh dùng chung.
- `docs/architecture/application_flows.md`: trang chuyển hướng để mục architecture trong README vẫn hữu ích.

Nội dung cũ `backend_learning_roadmap.md` và `application_flows.md` đã được viết lại thành nhóm học. Ba kế hoạch `upgrade_plan.md`, `completion_plan.md`, `fresher_upgrade_plan.md` được hợp nhất thành nhóm nâng cấp. `upgrade_verification.md` chuyển sang [hồ sơ lịch sử](../upgrades/04-lich-su-xac-minh.md). README giữ nguyên.
