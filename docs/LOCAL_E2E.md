# Database và dữ liệu kiểm thử hybrid trên Docker

Kịch bản thao tác bằng tay và bảng ghi PASS/FAIL: [HYBRID_MANUAL_TEST_SCENARIOS.md](../HYBRID_MANUAL_TEST_SCENARIOS.md).

## Môi trường riêng

Stack được định nghĩa trong `compose.test.yml`, project Docker `smart-shift-e2e`.
Không dùng container hay volume của ứng dụng hiện có tại cổng 3000/8080/5432.

| Thành phần | Địa chỉ |
|---|---|
| Giao diện | http://localhost:13000 |
| API | http://localhost:18080 |
| PostgreSQL | localhost:15432 |
| Database | smart_shift_e2e |
| DB user/password | smart_shift_test / local_e2e_only |
| Admin giao diện | admin / Demo@123456 |

Đây là tài khoản mẫu local, chỉ bind cổng vào loopback. Lark được tắt trong stack này.
Database tồn tại trong volume `smart-shift-e2e_e2e_postgres_data` sau khi dừng container.

## Khởi động và nạp dữ liệu

Chạy tại thư mục gốc project:

```powershell
docker compose -f compose.test.yml up -d --build
python qa-automation/scripts/hybrid_e2e.py
```

Đợi API khởi động trước khi chạy Python. Script chỉ dùng Python standard library.
Nó tạo dữ liệu bằng API thật: login, tạo tài khoản, lịch rảnh, nghỉ phép, kỳ lịch,
mẫu ca, sinh ca, nhu cầu, phân công thủ công, sinh lịch hybrid và công bố.
Flyway của backend tự tạo schema V1–V23 khi database trống.

Script lưu manifest và kết quả tại `qa-automation/results/hybrid-e2e.json`.
Không ghi JWT hoặc password vào manifest. Thư mục kết quả được gitignore.
Chạy lại dùng manifest hiện có, không xóa dữ liệu đã lưu.
Nếu ngắt giữa lúc seed một tình huống chưa hoàn thành, có thể dùng `--new-run`
để tạo bộ mới; bộ cũ được giữ nguyên, không tự động reset database.
`--seed-only` chỉ tạo dữ liệu, giữ các kỳ mới ở Nháp để thao tác bằng giao diện.

```powershell
python qa-automation/scripts/hybrid_e2e.py --new-run --seed-only
```

## Bộ dữ liệu

Dữ liệu hoàn toàn mô phỏng, seed 42, bắt đầu thứ Hai kế tiếp theo giờ Việt Nam.
Đây là fixture kiểm thử chức năng, chưa phải benchmark học thuật hoặc chứng minh quy mô lớn.

| Tình huống | Nhân viên | Ngày | Ca | Nhu cầu nhân sự |
|---|---:|---:|---:|---:|
| balanced | 24 | 14 | 42 | 126 vị trí |
| shortage | 6 | 7 | 21 | 63 vị trí |
| night | 8 | 7 | 14 | 28 vị trí |

Thêm 1 ca cố định đêm Chủ nhật trước kỳ night để kiểm tra giờ nghỉ qua ranh giới tuần.
Tổng cộng 38 nhân viên, 3 quản lý và 1 admin; có 78 ca gồm ca biên.
Tài khoản nhân viên/quản lý nằm trong manifest, cùng mật khẩu mẫu `Demo@123456`.

Các dữ liệu đặc biệt:
- Full-time/part-time có định mức và giới hạn giờ khác nhau.
- Ba vị trí: Thu ngân, Phục vụ, Bếp; tình huống night dùng hai vị trí đầu.
- AVAILABLE, PREFERRED và một ngày UNAVAILABLE.
- Một đơn nghỉ đã duyệt trong balanced.
- Một phân công MANUAL trong balanced phải giữ nguyên.
- Ngân sách thấp trong shortage để thử mục tiêu mềm mà không nới ràng buộc cứng.
- Ca qua đêm và ca cố định ngoài kỳ để kiểm tra snapshot.

## Kiểm tra dữ liệu độc lập

```powershell
docker cp qa-automation/sql/verify_hybrid_e2e.sql smart-shift-e2e-postgres:/tmp/verify_hybrid_e2e.sql
docker exec smart-shift-e2e-postgres psql -U smart_shift_test -d smart_shift_e2e -f /tmp/verify_hybrid_e2e.sql
```

SQL chỉ đọc, kiểm tra 11 nhóm vi phạm và trả lỗi nếu có số vi phạm khác 0:
vị trí/chi nhánh/trạng thái nhân viên, trùng ca, giờ nghỉ, giờ ngày/tuần,
ngày liên tiếp, nghỉ phép, độ phủ lịch rảnh, UNAVAILABLE, phân công trùng và vượt tối đa nhu cầu.

Script API còn kiểm tra:
- Sinh lại không tạo thêm phân công.
- Giữ phân công thủ công.
- Kiểm tra công bố không có phân công sai; lịch thiếu người bị chặn công bố.
- Nhân viên xem lịch đã công bố và nhận thông báo.
- Manager không truy cập ca của chi nhánh khác; Employee không được gọi sinh lịch.

## Kết quả lần chạy ngày 03/11/2026

Kỳ chính bắt đầu 09/11/2026:
- balanced: 125 AUTO mới + 1 MANUAL = 126, không thiếu; đã công bố.
- shortage: 28 AUTO, thiếu 35 vị trí; giữ Nháp và API từ chối công bố.
- night: 28 AUTO, không thiếu; đã công bố. Ca Chủ nhật trước kỳ vẫn được giữ nguyên.
- Cả 11 nhóm đối soát SQL: 0 vi phạm.
- Kiểm tra phân quyền và xem thông báo: đạt.
- Toàn bộ 74 test backend, gồm `SmartShiftBeApplicationTests` kết nối PostgreSQL: đạt.
- Đăng nhập qua frontend proxy ở cổng 13000: đạt. Chưa chạy tự động các thao tác trình duyệt.

Thời gian ghi trong manifest là số đo một lần trên máy local, không phải benchmark so sánh Greedy.

Chạy lại toàn bộ test backend với DB này trong PowerShell:

```powershell
cd smart-shift-be
./mvnw.cmd -B "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:15432/smart_shift_e2e" "-Dspring.datasource.username=smart_shift_test" "-Dspring.datasource.password=local_e2e_only" test
```

Máy cần Java 17+ trong JAVA_HOME. Các thông tin kết nối trên chỉ dành cho DB test local.

## Dừng và mở lại

```powershell
docker compose -f compose.test.yml stop
docker compose -f compose.test.yml start
```

Không dùng lệnh xóa volume nếu còn muốn giữ bộ dữ liệu.
