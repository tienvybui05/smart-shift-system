# Xuất Excel bảng chấm công và bảng lương

## Cách dùng

- **Quản lý chấm công → Bảng chấm công:** chọn ngày, chi nhánh, trạng thái rồi bấm **Áp dụng → Xuất Excel (.xlsx)**.
- **Tính công và lương dự tính:** chọn đúng kỳ, chi nhánh rồi bấm **Xem dữ liệu → Xuất Excel (.xlsx)**.
- Xuất theo **bộ lọc đã áp dụng** của bảng, bao gồm mọi trang dữ liệu. Sửa ô lọc nhưng chưa bấm Áp dụng/Xem dữ liệu không thay đổi phạm vi file tải.
- Admin xuất trong phạm vi được phép chọn; Manager chỉ xuất chi nhánh của mình. Employee không được gọi hai endpoint báo cáo quản trị này.
- Kết quả rỗng vẫn tải được một workbook có tiêu đề/thông tin bộ lọc; không tạo dòng công hoặc lương giả.

File `.xlsx` được tạo bằng [Apache POI 6.0.0](https://poi.apache.org/download.cgi), thay cho nút xuất CSV cũ trên màn hình quản lý chấm công. Không cần migration DB.

## Nội dung file

Mỗi workbook có ba sheet:

| Sheet | Nội dung |
|---|---|
| Thông tin | Người xuất, thời điểm UTC, khoảng ngày, bộ lọc, quy ước dữ liệu |
| Chấm công / Bảng lương | Các bản ghi chi tiết trong phạm vi tra cứu |
| Tổng hợp | Chấm công theo nhân viên + chi nhánh; bảng lương theo chi nhánh |

Tiêu đề tiếng Việt, cố định hàng đầu, bộ lọc Excel, số/tiền/giờ và ngày có định dạng phù hợp. Có thể dùng SUM/lọc dữ liệu với các cột số thông thường.

### Chấm công

- Mã công/phân công, nhân viên, chi nhánh, múi giờ và ngày ca.
- Giờ bắt đầu/kết thúc ca, check-in/out có đủ ngày để nhận diện ca qua đêm. Các giờ này theo múi giờ của từng chi nhánh; có thêm thời điểm check-in/out UTC để đối chiếu.
- Phút nghỉ, phút/giờ làm, đi trễ, về sớm, tăng ca, trạng thái, người/thời điểm duyệt.
- Cờ xác minh thiết bị/mạng, IP/SSID vào và ra, ghi chú. Không dùng SSID để kết luận mạng đã xác minh.
- Thiếu check-in/out hoặc chưa có số phút thực tế thì để ô trống. Tổng hợp giờ có thể bao gồm công chưa duyệt, không dùng thay cho dữ liệu tính lương đã duyệt.

### Bảng lương

- Xuất các snapshot lương **đã lưu**, giữ mức lương, hệ số, giờ, lương trước thưởng, thưởng và tổng lương của lần tính đó.
- Phân biệt cơ sở trả theo giờ/theo tháng, bản Nháp/Đã xác nhận và cờ **Cần tính lại công**.
- Không tính lại lương, cập nhật thưởng, xác nhận hay thay đổi snapshot khi tải file.
- Ngày đầu/cuối kỳ phải khớp đúng kỳ cần tra cứu như màn hình hiện tại; đây không phải phép cộng mọi kỳ giao nhau với khoảng ngày.
- Thời điểm xác nhận trong file là UTC. Tổng hợp tính cả bản nháp và bản xác nhận có trong dữ liệu, với số lượng từng trạng thái tách riêng; không đồng nghĩa với tiền đã chi trả.

## Giới hạn và độ chính xác

- Khoảng ngày tối đa 93 ngày theo validator hiện tại; tối đa 100.000 bản ghi/file. Vượt số dòng trả 400, cần thu hẹp bộ lọc.
- SXSSF giữ cửa sổ 100 dòng của workbook, tái sử dụng style và nén file tạm. Không autosize cột dựa trên font của server. File tạm được dọn khi workbook đóng, kể cả khi ghi thất bại.
- Backend vẫn lấy danh sách DTO và giữ file XLSX đã nén trong bộ nhớ trước khi gửi; đây chưa phải truy vấn DB theo cursor/streaming toàn tuyến.
- Tiền tổng hợp được cộng bằng BigDecimal ở backend. Số thập phân có hơn 15 chữ số có nghĩa được xuất dưới dạng văn bản để giữ giá trị trước giới hạn chính xác số của Excel; mã định danh cũng là văn bản.
- Ghi chú/tên bắt đầu bằng `=`, `+`, `-` hoặc `@` được ghi như chuỗi, không tạo công thức hay liên kết ngoài. File không chứa công thức tổng tiền; tổng được tính từ dữ liệu nguồn.
- Ô văn bản dài hơn giới hạn 32.767 ký tự được rút gọn và thêm `…`; quy tắc có ghi ở sheet Thông tin.
- File chứa thông tin nhân sự/lương trong phạm vi người tải và phản hồi có `Cache-Control: no-store`.

## API

```http
GET /api/attendances/export?startDate=2026-11-01&endDate=2026-11-30&locationId=1&status=LATE
GET /api/payroll/export?startDate=2026-11-01&endDate=2026-11-30&locationId=1
```

MIME: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; có `Content-Disposition: attachment` và tên file chứa khoảng ngày. Lỗi trả JSON theo định dạng chung; frontend đọc JSON từ phản hồi Blob để hiện đúng thông báo, không lưu file lỗi thành `.xlsx`.

## Kết quả kiểm tra ngày 04/11/2026

- **127 unit test Java đạt**; 7 test xuất file mới đọc workbook bằng POI thật để kiểm tra giờ ca đêm, ô trống, số tiền, tổng hợp, dữ liệu có dấu, chuỗi giống công thức, hơn 100 dòng, ghi chú dài và giới hạn xuất.
- **24 kiểm tra API đạt** qua PostgreSQL QA: xác thực/phân quyền, bộ lọc, MIME/tên file/cache, XLSX rỗng, snapshot lương, cờ cần tính lại và tổng tiền có phần lẻ. Đọc ZIP/XML bằng thư viện chuẩn Python độc lập với POI.
- **6 kiểm tra Chromium đạt**: tải hai file thật qua giao diện, giữ bộ lọc đã áp dụng, định dạng XLSX, báo lỗi HTTP khi tải bị từ chối và không có lỗi JavaScript.
- Frontend production build và ESLint các file thay đổi đạt.

```powershell
docker compose -f compose.attendance-test.yml up -d --build
python qa-automation/scripts/excel_e2e.py
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-excel-unit:local smart-shift-be
```

Script chỉ seed fixture mới vào DB `attendance_qa`, không xóa dữ liệu và không dùng DB chính. Kết quả, file mẫu và tài khoản ở `qa-automation/results/excel/` (Git ignore). Manager trong `result.json` dùng mật khẩu QA `Demo@123456`, mở http://localhost:23000 và áp dụng `startDate`/`endDate` trong manifest để thử tải trên giao diện.
