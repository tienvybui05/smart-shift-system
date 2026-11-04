# Kết quả kiểm tra nghiệp vụ chấm công — 04/11/2026

Đã kiểm tra trên Docker QA riêng bằng PostgreSQL 17, backend Spring Boot và frontend Nginx. Flyway V24 áp dụng thành công, Hibernate kiểm tra schema thành công. Không dùng database/volume ứng dụng chính hoặc bộ benchmark thuật toán.

| Hạng mục | Kết quả |
|---|---|
| Backend unit test (`*Test`) | 101 passed, 0 failures/errors/skipped, chạy Linux Docker để nạp OR-Tools native |
| API end-to-end | 30/30 passed qua API thật, PostgreSQL thật và đường proxy Nginx |
| Frontend | Production build thành công; ESLint các file thay đổi không có lỗi |
| Trình duyệt Chromium | Gửi yêu cầu → quản lý duyệt → mở lịch sử trước/sau → nhân viên tải lại thấy kết quả; không có page error |
| Tài liệu/cấu hình | OpenAPI và Compose QA parse YAML thành công, không trùng key và không có `$ref` nội bộ bị thiếu |

API E2E bao gồm quyền nhân viên/quản lý theo chi nhánh; chặn giả mạo X-Forwarded-For và SSID tự khai; proxy hợp lệ; không sửa công khi gửi đơn; đơn trùng; lý do 1000 ký tự; duyệt/từ chối/hủy; bản công thay đổi sau khi gửi; không tự tạo chứng cứ GPS/thiết bị/mạng đã xác minh; cờ lương cần tính lại; và khóa công thuộc kỳ lương đã xác nhận.

Các phép thử đồng thời đã qua:

- Hai lần gửi cho cùng ca: một 201 và một 409.
- Hai người/lượt xét duyệt cùng đơn: một 200 và một 400, tối đa một thay đổi công.
- Duyệt công cùng lúc xác nhận lương: công được cập nhật; xác nhận ảnh chụp lương cũ bị chặn.
- Gửi đơn cùng lúc xác nhận lương: hoặc lương xác nhận và đơn bị chặn, hoặc đơn được tạo và xác nhận lương bị chặn.

Nhân viên được khóa bằng truy vấn scalar `SELECT ... FOR UPDATE` dùng chung giữa công và lương. Tham chiếu đơn/bảng lương được lấy bằng projection, entity được đọc sau khóa để tránh trạng thái cũ trong persistence context. Kiểm thử đã phát hiện và được dùng để sửa cả lỗi duyệt hai lần lẫn lỗi gửi đơn trùng thời điểm chốt lương.

Chạy lại:

```powershell
docker compose -f compose.attendance-test.yml up -d --build
python qa-automation/scripts/attendance_workflow_e2e.py
Get-Content -Raw qa-automation/sql/verify_attendance_workflow.sql | docker exec -i smart-shift-attendance-db psql -U attendance_qa -d attendance_qa -v ON_ERROR_STOP=1
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-attendance-unit:local smart-shift-be
```

Các dòng `invalid_count` trong truy vấn đối chiếu phải bằng 0. Báo cáo chi tiết của lần API gần nhất nằm trong `qa-automation/results/attendance-workflow.json` (được Git ignore vì là dữ liệu chạy QA, không lưu token).

Mở http://localhost:23000 để thử. Tạo dữ liệu chưa chốt lương bằng `python qa-automation/scripts/attendance_workflow_e2e.py --seed-only`; xem tài khoản trong `qa-automation/results/attendance-demo.json`, mật khẩu QA `Demo@123456`.

Hướng dẫn và kịch bản bằng tay: [ATTENDANCE_WORKFLOW.md](ATTENDANCE_WORKFLOW.md). Kiểm thử dùng dữ liệu sinh; chưa thay thế UAT với dữ liệu nhân sự thực, thiết bị di động và hạ tầng proxy khi triển khai. Mạng được xác minh bằng IP server quan sát; SSID/BSSID trình duyệt không chứng minh được Wi-Fi vật lý.
