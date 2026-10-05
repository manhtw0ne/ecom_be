# 03 — Nghiệm thu, demo và chuẩn bị hồ sơ

[Tổng kế hoạch](00-ke-hoach-fresher.md) · [P0](01-p0-tinh-dung-va-bao-mat.md) · [P1/P2](02-p1-chat-luong-va-mo-rong.md) · [Lịch sử](04-lich-su-xac-minh.md)

## 1. Ghi kết quả mới, không kế thừa dấu hoàn tất cũ

Mẫu một lần xác minh:

```text
Ngày:
Commit và thay đổi chưa commit:
Java/Maven/Docker, OS:
Profile và dependency đang chạy:
Lệnh:
Kết quả/exit code:
Report hoặc URL CI run:
Ca chưa chạy và lý do:
Giới hạn còn lại:
```

Có thể ghi bản cập nhật vào cuối file này hoặc thêm hồ sơ có ngày trong nhóm upgrades. Không dùng số liệu ngày 18/09/2026 làm kết quả của commit mới.

## 2. Các lớp kiểm tra

> Cập nhật 03/10/2026: dùng [bài 19](19-chot-pham-vi-demo-fresher.md) cho phạm vi COD, CORS, smoke metrics có phân quyền và bộ request mới. Metrics authenticated cần `-AdminTokenFile`; smoke không có file sẽ báo SKIP phần scrape thay vì giả định endpoint public.

Chạy tuần tự từ root repo, PowerShell/JDK 21:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
docker compose -f docker-compose.test.yml up -d --wait --wait-timeout 180
.\mvnw.cmd verify -Pintegration
docker compose -f docker-compose.test.yml down
```

Nếu bước trước lỗi, chẩn đoán trước khi coi bước sau có ý nghĩa. MySQL test/Redis test dùng cổng 13306/16379. Wrapper cần truy cập dependency ở lần đầu.

Đọc surefire-reports, failsafe-reports và site/jacoco dưới target. Yêu cầu: không có test fail/error và không skip các ca bắt buộc; gate service line coverage trong pom hiện là 70%. Không dùng “80% test pass” làm tiêu chí đạt: test bắt buộc phải pass hết.

Toàn stack local riêng:

```powershell
docker compose up -d --build --wait --wait-timeout 300
Invoke-RestMethod http://localhost:8080/api/v1/actuator/health
powershell -File scripts/smoke-test.ps1
docker compose down
```

Smoke tạo tài khoản/dữ liệu và request demo. Các lệnh down trên không xóa volume. Không thử lên môi trường chứa dữ liệu thật.

## 3. Ma trận cần chứng minh trước khi nộp hồ sơ

| Phạm vi | Ca bắt buộc | Bằng chứng |
| --- | --- | --- |
| Catalog | CRUD, validation, phân trang/filter, ID sai | HTTP + service tests |
| Auth/quyền | Sai mật khẩu, token lỗi, user A không đọc đơn B | HTTP qua security filters |
| Order | Giá server, quantity sai, rollback, hủy | Service/integration |
| Cạnh tranh | Kho 5 với 20 request, hủy đồng thời | MySQL integration |
| Cache | Hit/miss/invalidation, giữ key khác, Redis lỗi | Redis integration + fallback test |
| Payment | COD công bố rõ hoặc callback/amount/chữ ký/lặp | Test theo phạm vi đã chọn |
| Migration | DB mới, nâng schema bản trước nếu có thay đổi | Log Flyway và dữ liệu kiểm thử |
| CI | Đúng commit, test/artifact đầy đủ | URL run thật |
| Vận hành | Startup/health/smoke, cấu hình thiếu dễ chẩn đoán | Log và hướng dẫn tái lập |

Không bắt buộc gửi SMTP thật/OAuth/VNPay thật nếu không demo chúng; phải ghi rõ giới hạn. Nếu quảng bá tính năng end-to-end, cần bằng chứng tương ứng ngoài test mock.

## 4. Kịch bản demo 8–10 phút

1. **1 phút:** bài toán, phạm vi, kiến trúc controller/service/repository và các dependency.
2. **2 phút:** login, catalog, đặt hàng; cho thấy giá lấy từ server.
3. **2 phút:** thử user khác đọc đơn và request dữ liệu sai; giải thích response.
4. **2 phút:** trình bày test rollback/concurrency, xem assertion và trạng thái DB.
5. **1 phút:** log theo requestId, health/metric có ý nghĩa.
6. **1–2 phút:** một quyết định thiết kế, phương án thay thế và một hạn chế còn lại.

Dùng tài khoản/dữ liệu demo, chuẩn bị reset dữ liệu trong môi trường riêng. Có thể trình bày report test cạnh tranh thay vì gây tải trực tiếp lúc demo.

## 5. Hồ sơ dự án và CV

Chuẩn bị:

- [ ] Repo có cách chạy tái lập và bộ request mẫu.
- [ ] Bản đồ kiến trúc/luồng khớp code, không ghi tính năng chưa hoàn tất.
- [ ] Một báo cáo kiểm thử gắn commit; badge chỉ dùng run thực tế.
- [ ] Một câu chuyện sửa bug: triệu chứng → giả thuyết → test → sửa → bằng chứng.
- [ ] Một phép đo trước/sau nếu ghi thành tích tối ưu.
- [ ] Nêu rõ phần đã dùng AI hỗ trợ và phần mình tự kiểm chứng khi được hỏi.

Mẫu mô tả CV, **chỉ dùng sau khi đúng với việc bạn đã làm**:

> Xây dựng backend bán hàng Java/Spring Boot với phân quyền theo chủ sở hữu, xử lý đặt hàng trong transaction và kiểm thử cạnh tranh trên MySQL; đóng gói Docker và xác minh bằng CI.

Nếu có con số, thay bằng số đo thật cùng điều kiện. Không tự nhận “chịu hàng nghìn người dùng”, “production-ready” hay “thanh toán hoàn chỉnh” từ sự tồn tại của dependency/config.

Việc chỉnh README cho số liệu/demo sau này thuộc F07, không nằm trong lần tổ chức tài liệu này; README hiện được giữ nguyên theo phạm vi yêu cầu.

## 6. Câu hỏi tự kiểm tra trước phỏng vấn

- Java: interface, collection, equals/hashCode, exception, BigDecimal giải quyết vấn đề gì trong dự án?
- Spring: bean/DI/proxy, transaction boundary, validation và security filter phối hợp thế nào?
- SQL/JPA: JOIN, index, N+1, dirty checking, row lock và migration có ví dụ gì?
- Nghiệp vụ: vì sao không tin total/userId từ client; hủy hai lần có chuyện gì?
- Tích hợp: Redis tắt thì phần nào tiếp tục được; email/Kafka có thể mất/trùng không?
- Test: test nào bắt bug ownership, test nào chứng minh row lock, mock chưa chứng minh điều gì?
- Vận hành: CI lỗi thì đọc ở đâu; image đã publish khác app đã deploy thế nào?

Cách tự chấm: 0 = chưa hiểu; 1 = giải thích được; 2 = chỉ ra code và test; 3 = tự thay đổi và bảo vệ trade-off. Dùng để tìm lỗ hổng kiến thức, không coi tổng điểm là chuẩn tuyển dụng.

## 7. Điểm kết thúc

- [ ] P0 hoàn tất theo phạm vi đã chọn, không còn lỗi quyền/tính tiền đã biết chưa xử lý.
- [ ] API lõi có hợp đồng/test, dự án chạy lại được.
- [ ] Demo và hồ sơ phản ánh chính xác bằng chứng.
- [ ] Tự trình bày được ba quyết định và một hạn chế mà không đọc kịch bản.

Sau đó có thể bắt đầu ứng tuyển và tiếp tục học theo phản hồi, không phải chờ thêm mọi công nghệ nâng cao.
