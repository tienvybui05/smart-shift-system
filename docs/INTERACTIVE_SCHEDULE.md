# Lịch làm việc trực quan và kéo thả nhân viên

## Cách dùng

Admin/Manager mở **Lịch làm việc**, chọn kỳ lịch và chế độ **Lịch trực quan**. Có thể đổi qua **Danh sách** để dùng bảng quản lý cũ.

- Lịch hỗ trợ **ngày, tuần, tháng** theo múi giờ chi nhánh; mặc định tuần trên máy tính, ngày trên điện thoại.
- Bốn thẻ thống kê cho biết số ca, số người đã xếp, tổng phân công và số vị trí còn thiếu trong toàn kỳ, không bị thay đổi bởi bộ lọc trạng thái ca.
- Bên trái là nhân viên đang hoạt động trong chi nhánh, có tìm theo tên/mã và lọc vị trí. Giờ của mỗi người tính trên khoảng lịch đang hiển thị, kể cả ca bị bộ lọc ẩn. Đây không phải tổng giờ làm ở các kỳ khác hay giới hạn giờ đã được kiểm tra đầy đủ; backend sẽ tính lại khi phân công.
- Thẻ ca hiển thị giờ, mẫu ca, nhu cầu và những người đang được phân công. Màu xanh biểu thị đủ nhu cầu tối thiểu, vàng biểu thị thiếu người. Ca hủy có màu xám. Bấm giờ ca để mở bảng chi tiết hiện có.
- Kéo người từ danh sách vào ca để thêm phân công. Kéo thẻ người đang ở một ca sang ca khác để chuyển; giữ nguyên ID phân công.
- Khi trỏ vào ca đích, khung xanh/đỏ và thông báo cho biết có hợp lệ hay không. Khi thả, server kiểm tra lại và lưu. Nếu không lưu được, giao diện khôi phục và tải dữ liệu thật để đối chiếu.
- Có thể **bấm chọn nhân viên, rồi bấm Phân công** trên ca; với người đã được xếp, bấm thẻ người rồi **Chuyển vào ca**. Đây cũng là cách thao tác bằng bàn phím hoặc trên điện thoại. Nút **Nhân viên** trên điện thoại mở danh sách trong ngăn riêng.

Kéo thả chỉ thay đổi **người được xếp vào các ca có sẵn**. Giờ bắt đầu/kết thúc, ngày và nhu cầu ca dùng luồng chỉnh sửa ca hiện có.

## Quy tắc nghiệp vụ

Chỉ ca `OPEN`/`FILLED` chưa bắt đầu trong kỳ `DRAFT` được sửa. Lịch `GENERATING`, `PUBLISHED`, `LOCKED`, ca hủy/quá khứ và phân công đã có công chỉ được xem. Admin/Manager phải có quyền với kỳ, ca đích và phân công nguồn. Nhân viên phải đang hoạt động, thuộc chi nhánh và có vị trí phù hợp.

Server kiểm tra nhu cầu tối đa theo vị trí, phân công trùng, lịch rảnh/nghỉ phép, tổng giờ ngày/tuần, số ca và thời gian nghỉ giữa ca theo chính sách chung, cùng số ngày làm liên tiếp tối đa. Khi chuyển, loại trừ phân công nguồn khỏi việc tính giờ và trùng ca, tránh từ chối nhầm chính ca đang chuyển.

Nếu chuyển đi làm ca nguồn thiếu nhu cầu tối thiểu, hệ thống cho lưu ở kỳ Nháp và cảnh báo phải bổ sung trước công bố. Quy trình kiểm tra công bố vẫn áp dụng bình thường. Đổi lịch đã công bố dùng quy trình đổi/nhường ca hiện có.

## Lưu an toàn khi nhiều quản lý thao tác

- Migration `V25__add_assignment_version.sql` thêm version mặc định `0` vào phân công cũ. JPA quản lý version khi cập nhật.
- Request chuyển phải gửi ID ca nguồn và version đang thấy trên lịch. Nguồn/version đã đổi trả `409`, yêu cầu tải lại.
- Trong một giao dịch, khóa kỳ, các ca theo thứ tự ID và nhân viên, rồi tải lại dữ liệu để kiểm tra. Hai request cùng chuyển một phân công chỉ một request thành công; hai người vào chỗ cuối không vượt nhu cầu tối đa.
- Không xóa rồi tạo lại khi chuyển. Phân công chuyển sang `MANUAL`/`ASSIGNED`, bỏ điểm tối ưu cũ; ghi người thực hiện, lý do và dữ liệu trước/sau vào lịch sử ở cả ca nguồn lẫn ca đích.
- Thông báo preview chỉ để tham khảo tại thời điểm kiểm tra. Kết quả lưu cuối cùng dựa trên dữ liệu server trong giao dịch.

## API

```http
GET  /api/schedule-periods/{periodId}/board
POST /api/schedule-periods/{periodId}/board/preview
POST /api/schedule-periods/{periodId}/board/place
```

Thêm nhân viên:

```json
{"userId": 12, "targetWorkShiftId": 40, "reason": "Bổ sung ca chiều"}
```

Chuyển phân công hiện có:

```json
{"userId": 12, "targetWorkShiftId": 41, "sourceAssignmentId": 75, "sourceWorkShiftId": 40, "expectedVersion": 0, "reason": "Điều chỉnh phân công"}
```

Preview trả `eligible`, `violations`, `warnings`, `projectedDailyHours`, `projectedWeeklyHours`. Lưu thành công trả ID phân công/ca nguồn/ca đích, version và cảnh báo. Board trả ca, nhu cầu, phân công và hồ sơ công việc tối thiểu của nhân viên; không trả lương, email hay số điện thoại. Xem [OpenAPI](openapi.yaml).

## Kiểm tra và dữ liệu demo

```powershell
docker compose -f compose.attendance-test.yml up -d --build backend frontend
python qa-automation/scripts/calendar_e2e.py
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-calendar-unit:local smart-shift-be
```

Chờ backend sẵn sàng trước khi chạy script. Script chỉ seed dữ liệu mới vào DB QA `attendance_qa`, không xóa dữ liệu và không dùng DB chính. Manifest `qa-automation/results/calendar.json` chứa manager, periodId và các ca demo; mật khẩu QA `Demo@123456`. Mở `http://localhost:23000/admin/work-shifts?periodId=<periodId>` bằng tài khoản manager trong manifest.

Kiểm tra Chromium sau khi seed (cần cài Playwright hoặc playwright-core và Chromium):

```powershell
# Đặt đường dẫn module nếu Playwright không ở node_modules mặc định.
$env:PLAYWRIGHT_MODULE = (Resolve-Path '.tmp/attendance-browser/node_modules/playwright-core').Path
$env:CHROME_PATH = 'C:/duong-dan/chrome.exe'
node qa-automation/scripts/calendar_browser.cjs
```

Browser script sửa một số phân công trong fixture demo để kiểm tra lưu thật. Muốn chạy lại từ đầu, seed fixture mới trước. Kết quả và ảnh chụp lưu ở `qa-automation/results/`, không đưa vào Git.

## Kết quả kiểm tra ngày 04/11/2026

- **134 unit test Java đạt**, gồm 7 test mới cho preview, version cũ, chuyển nguyên tử, audit, lịch công bố, dữ liệu công và nhân viên ngoài chi nhánh.
- **25 kiểm tra API lịch đạt** qua PostgreSQL QA, gồm request đồng thời, không vượt số chỗ, không mất/trùng phân công, quyền truy cập và ràng buộc lịch.
- **18 kiểm tra hồi quy đổi/nhường ca đạt** sau khi thêm version vào phân công.
- **11 kiểm tra Chromium đạt**: thêm/chuyển người, ca đầy và rollback, cách bấm chọn thay kéo, xem tháng/tuần, bố cục mobile không tràn, thẻ ca đủ rộng, phân công qua ngăn nhân viên và lịch công bố chỉ xem. Kéo thả được kiểm tra bằng DragEvent/DataTransfer trong Chromium với API thật; cử chỉ chuột/cảm ứng của hệ điều hành cần kiểm tra tay khi nghiệm thu.
- Frontend production build và ESLint các file JavaScript thay đổi đạt. Ảnh desktop/mobile và JSON kết quả ở thư mục kết quả QA.

Frontend dùng FullCalendar React 7 với DayGrid và theme Monarch, cùng HTML drag/drop cho thẻ nhân viên. Tham khảo [tài liệu React chính thức](https://fullcalendar.io/docs/react).
