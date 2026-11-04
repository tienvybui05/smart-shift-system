# Ngân sách tuần và dự toán chi phí lịch

## Sử dụng

1. Vào **Kỳ xếp lịch**, chọn một chi nhánh. Manager chỉ thao tác chi nhánh của mình; Admin chọn chi nhánh cần quản lý.
2. Tại **Ngân sách xếp lịch theo tuần**, bật **Thiết lập ngân sách**, nhập VND và bấm **Lưu ngân sách**.
3. Bỏ chọn để xóa cấu hình. Ngân sách `0` là một cấu hình hợp lệ và khác với chưa cấu hình.
4. Nút **Ngân sách** của mỗi kỳ mở dự toán theo tuần. Cùng bảng này xuất hiện trước/sau chạy xếp lịch tự động và trong hộp kiểm tra công bố.
5. Nếu vượt, quản lý xem phần chi phí kỳ này/kỳ khác, điều chỉnh phân công hoặc ngân sách rồi kiểm tra lại. Lưu cấu hình không tự sửa các phân công đã có; lần chạy Hybrid tiếp theo dùng cấu hình mới.

## Quy tắc tính

- Tuần đầy đủ từ thứ Hai đến Chủ nhật theo múi giờ chi nhánh. Kỳ chỉ có vài ngày vẫn so với ngân sách cả tuần, không chia tỷ lệ theo ngày.
- Tổng tuần tính mọi phân công `ASSIGNED`/`CONFIRMED` của chi nhánh, bao gồm kỳ Nháp, đã Công bố hoặc đã Khóa khác trong cùng tuần. Bỏ ca hủy và phân công đã hủy.
- Bảng tách **Kỳ này**, **Kỳ khác**, **Tổng tuần**, **Vượt ngân sách** và **Còn lại**. Chi phí riêng kỳ là tổng cột Kỳ này của các tuần, không cộng lẫn chi phí ngoài kỳ.
- Mỗi phân công: `mức lương hiện tại × hệ số × (phút ca − phút nghỉ) / 60`; làm tròn tiền từng phân công đến 2 chữ số thập phân rồi cộng. Không dùng số thực nhị phân để tính ở backend.
- Ca qua đêm được tính toàn bộ vào ngày/tuần bắt đầu ca, thống nhất quy ước lịch hiện tại.
- Phần vượt = `max(0, tổng tuần − ngân sách)`; còn lại = `max(0, ngân sách − tổng tuần)`.
- Chưa cấu hình: vẫn tính chi phí nhưng không cảnh báo vượt, còn lại và tỷ lệ sử dụng là `null`. Ngân sách 0: có cảnh báo nếu phát sinh chi phí; tỷ lệ là `null` để tránh chia 0.
- Dự toán dùng mức lương/cấu hình **hiện tại**, không phải snapshot kế toán tại thời điểm công bố. Thay đổi lương hoặc ngân sách có thể thay đổi dự toán của cả kỳ cũ. Không thay đổi bảng lương đã xác nhận.
- Đây là chi phí **các ca đã phân công**, không phải dự báo chi phí lấp đầy những vị trí chưa được xếp; chưa gồm thưởng và hệ số trả thêm cho tăng ca. Lương thực tế vẫn lấy công đã duyệt.

## Ngân sách mềm

Hybrid ưu tiên giảm thiếu người, sau đó tối ưu các mục tiêu mềm gồm chi phí và phần vượt ngân sách. Hệ thống **không bảo đảm tổng chi phí luôn dưới ngân sách**. Cảnh báo không ngăn công bố khi các ràng buộc và định biên đã hợp lệ.

Bộ giải hiện lượng hóa chi phí/ngân sách theo nghìn đồng để tối ưu; bảng dự toán tính bằng VND với 2 chữ số thập phân. Vì vậy số tiền hiển thị có thể khác phép tính xấp xỉ trong mục tiêu bộ giải. Dùng bảng dự toán để kiểm tra mức vượt thực tế theo các ca đã xếp.

Ngân sách không phải cơ chế giữ tiền hoặc khóa chi tiêu. Hai kỳ cùng tuần đều được cộng vào báo cáo; màn hình hiển thị kết quả tại lần tải, cần mở lại hoặc tra cứu lại khi người khác thay đổi lịch/cấu hình.

## API

| Phương thức | Đường dẫn | Chức năng |
|---|---|---|
| GET | `/api/scheduling-budgets/locations/{id}` | Đọc ngân sách tuần |
| PATCH | `/api/scheduling-budgets/locations/{id}` | Cập nhật riêng ngân sách, không ghi đè GPS/Wi-Fi/cấu hình khác |
| GET | `/api/scheduling-budgets/periods/{id}` | Dự toán từng tuần giao với kỳ, gồm chi phí các kỳ khác |

Tất cả endpoint yêu cầu Admin/Manager và kiểm tra chi nhánh ở backend. Employee không được truy cập. PATCH body: `{"weeklyBudget": 3000000}` hoặc `{"weeklyBudget": null}`. Số tiền không âm, tối đa 13 chữ số nguyên và 2 chữ số thập phân. Phản hồi lỗi 400 khi số tiền không hợp lệ, 403 khi sai quyền/phạm vi, 404 khi không thấy đối tượng. Không cần migration mới vì dùng cột `locations.weekly_budget` từ V20.

## Kiểm tra

- **120 unit test Java đạt**, gồm 6 test ngân sách: trừ nghỉ/hệ số lương, gộp kỳ, tuần qua năm, ca đêm, làm tròn tiền, tuần rỗng, ngân sách 0/null và phạm vi truy cập.
- **24 kiểm tra API đạt** trên DB QA riêng: phân quyền, precision tiền, bảo toàn cấu hình khác, các trường hợp tổng hợp chi phí, công bố vượt ngân sách mềm và Hybrid chọn người có chi phí thấp hơn khi điều kiện khác tương đương.
- Frontend production build và ESLint các file thay đổi đạt.
- **7 kiểm tra Chromium đạt**: lưu cấu hình, mở dự toán, cập nhật chi phí/cảnh báo sau Hybrid,
  cảnh báo trước công bố, công bố lịch vượt ngân sách mềm, bỏ cấu hình và tải lại,
  không có lỗi JavaScript trong các thao tác đã kiểm tra.

```powershell
docker compose -f compose.attendance-test.yml up -d --build
python qa-automation/scripts/budget_e2e.py
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-budget-unit:local smart-shift-be
```

Script chỉ tạo/thao tác fixture mới trong DB `attendance_qa`, không xóa dữ liệu và không dùng DB ứng dụng chính. Kết quả và tài khoản ở `qa-automation/results/budget.json` (Git ignore), mật khẩu QA `Demo@123456`; frontend QA http://localhost:23000. Kỳ `periodId` được tạo chưa xếp người để thử luồng trên trình duyệt. Mỗi lần chạy script tạo bộ dữ liệu mới.
