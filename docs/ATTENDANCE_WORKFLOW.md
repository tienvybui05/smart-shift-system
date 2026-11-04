# Chấm công, điều chỉnh công và đối soát lương

## Luồng sử dụng

1. Nhân viên check-in/check-out bằng GPS và thiết bị như trước.
2. Nếu quên chấm công hoặc cần chỉnh giờ, vào **Chấm công → Yêu cầu bổ sung / điều chỉnh công → Gửi yêu cầu**.
3. Chọn ca trong khoảng ngày đang tra cứu, nhập giờ vào/ra đề xuất, phút nghỉ và lý do. Giờ nhập theo múi giờ thiết bị; ngày ca hiển thị theo chi nhánh. Ca qua đêm phải chọn đúng ngày ra.
4. Quản lý vào **Quản lý chấm công → Điều chỉnh công**, xem bản công trước khi gửi, giờ đề xuất và lý do; nhập lý do xét duyệt và chọn duyệt hoặc từ chối.
5. Chỉ khi được duyệt, giờ công mới thay đổi. Nhân viên nhận thông báo web; nếu Lark đã cấu hình thì dùng cùng kênh thông báo hiện có.
6. Nút **Lịch sử** cho biết người xử lý, thời điểm, lý do và bản công trước/sau. Nhân viên chỉ xem được công của mình; quản lý xem trong chi nhánh, Admin xem toàn hệ thống.
7. Nếu công thay đổi sau lần tính lương nháp, bảng lương hiển thị **Cần tính lại công**. Tính lại lương trước khi xác nhận.
8. Xuất bảng chấm công/bảng lương bằng **Xuất Excel (.xlsx)** theo bộ lọc đã áp dụng; xem [hướng dẫn báo cáo Excel](EXCEL_REPORTS.md).

## Quy tắc nghiệp vụ

- Chỉ nhân viên gửi yêu cầu cho phân công của chính mình, trạng thái ASSIGNED/CONFIRMED, ca không hủy và thuộc lịch PUBLISHED/LOCKED.
- Ca phải đã kết thúc, trong 30 ngày tính từ giờ kết thúc. Cửa sổ này cũng được kiểm tra lại khi duyệt; đơn quá hạn vẫn có thể bị từ chối hoặc hủy.
- Có tối đa một yêu cầu PENDING cho mỗi phân công. Sau hủy/từ chối có thể gửi lại nếu còn hợp lệ.
- Giờ ra phải sau giờ vào; giờ ra không ở tương lai; phút nghỉ không âm và nhỏ hơn thời gian làm. Giờ vào sớm nhất 2 giờ trước ca, giờ ra muộn nhất 12 giờ sau ca.
- Luồng: PENDING → APPROVED / REJECTED / CANCELLED. Chỉ người gửi được hủy PENDING; chỉ Admin/Manager đúng phạm vi được xét duyệt, không tự duyệt công/yêu cầu của mình.
- Lý do gửi và lý do xét duyệt bắt buộc, tối đa 1000 ký tự mỗi trường. Đối soát trực tiếp ở màn hình quản lý công cũng bắt buộc lý do.
- Nếu bản công thay đổi sau khi gửi yêu cầu, hệ thống không áp đè đề xuất cũ. Quản lý từ chối, giải thích và yêu cầu nhân viên gửi lại dựa trên dữ liệu mới. Việc tự sinh ABSENT sau khi gửi đơn cũng có thể làm baseline thay đổi.
- Bản công được bổ sung bằng phê duyệt thủ công không được gán GPS hay trạng thái thiết bị/mạng đã xác minh giả. Bản công có sẵn giữ chứng cứ GPS/mạng của lần chấm công gốc; lịch sử lưu bản trước/sau.
- Lịch sử mới được ghi cho check-in, check-out, đối soát trực tiếp và duyệt điều chỉnh. Các bản công trước V24 không được tạo lịch sử hồi tố. Hủy/từ chối đơn không sửa công; kết quả xử lý nằm trong đơn.

## Tính nhất quán với lương

- Nhân viên, phân công và bản công được khóa theo thứ tự thống nhất khi thay đổi công. Tính/xác nhận lương cũng khóa cùng nhân viên bằng truy vấn scalar `SELECT id FROM users WHERE id = ? FOR UPDATE`, tránh phụ thuộc vào cơ chế khóa entity có fetch graph và tránh đọc một ảnh chụp cũ trong lúc duyệt công.
- Tham chiếu đơn/bảng lương được đọc bằng projection trước khi chờ khóa; entity được tải sau khóa. Phân công đã có trong persistence context được refresh sau khóa. Điều này tránh hai lượt duyệt cùng giữ trạng thái PENDING cũ.
- Xác nhận lương bị chặn khi còn đơn PENDING trong khoảng kỳ hoặc khi `sourceChanged=true`. Tính lại lương xóa cờ này sau khi lấy công mới.
- Sau khi lương đã CONFIRMED, chặn check-in/check-out, duyệt trực tiếp và gửi/duyệt điều chỉnh của các ca giao với kỳ đó. Tính lại vẫn giữ nguyên bản lương đã xác nhận.
- Ca qua ngày được xét theo ngày địa phương của chi nhánh và khoảng giao với kỳ lương, không lấy ngày theo máy của người duyệt.

## Xác minh mạng và proxy

`wifiRequired=true` yêu cầu kiểm tra mạng theo địa chỉ IP quan sát được ở server. `allowedIpAddress` của chi nhánh hỗ trợ IP cụ thể, CIDR IPv4/IPv6, danh sách phân cách bằng dấu phẩy/chấm phẩy và mẫu IPv4 cuối `.*` cũ.

SSID/BSSID từ trình duyệt là dữ liệu tự khai, không đủ để đánh dấu mạng đã xác minh. SSID vẫn được lưu để đối soát. Nếu không khớp IP hoặc chưa có cấu hình đủ tin cậy, bản công có `unverifiedWifi=true`; hệ thống vẫn ghi công kèm cờ để quản lý đối soát như luồng cũ. Đây là kiểm tra mạng, không chứng minh được thiết bị đang dùng Wi-Fi thay vì kết nối mạng khác có cùng IP ra ngoài.

Backend mặc định `ATTENDANCE_TRUSTED_PROXIES=` rỗng: bỏ qua header IP do khách gửi và dùng địa chỉ kết nối trực tiếp. Chỉ khi peer nằm trong danh sách proxy tin cậy, backend mới đi qua chuỗi X-Forwarded-For từ phải sang trái và dừng ở hop đầu không được tin cậy. Header sai định dạng, quá dài hoặc quá nhiều hop bị bỏ qua. Giá trị được phân tích là IP số, không phân giải hostname do khách nhập.

Nginx frontend ghi đè X-Forwarded-For bằng địa chỉ khách kết nối với nó. Cấu hình backend `forward-headers-strategy: none` giữ peer gốc để resolver kiểm tra.

Với Docker thông thường:

```powershell
docker inspect smart-shift-frontend --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
```

Đặt `ATTENDANCE_TRUSTED_PROXIES` trong `.env` bằng IP chính xác/CIDR hẹp của proxy vừa xác định (IPv4 thường `/32`), rồi recreate backend để áp dụng. IP container có thể thay đổi khi tạo lại network; cập nhật cấu hình tương ứng. Không dùng toàn bộ mạng của người dùng làm danh sách proxy tin cậy. Nếu có proxy khác trước Nginx, cần cấu hình nguồn IP tin cậy của tầng đó riêng; cấu hình hiện tại xem địa chỉ kết nối trực tiếp tới Nginx là khách.

Chính sách mạng không thay thế GPS, quản lý thiết bị và phê duyệt nghiệp vụ. Dấu vân tay trình duyệt là định danh ứng dụng, không phải chứng nhận phần cứng chống giả tuyệt đối.

## API mới

| Phương thức | Endpoint | Quyền |
|---|---|---|
| GET | `/api/attendance-corrections/me?startDate=...&endDate=...` | Employee, đơn của mình |
| POST | `/api/attendance-corrections/me` | Employee |
| PATCH | `/api/attendance-corrections/me/{id}/cancel` | Employee, đơn của mình |
| GET | `/api/attendance-corrections?startDate=...&endDate=...&locationId=...&status=...` | Admin/Manager theo chi nhánh |
| PATCH | `/api/attendance-corrections/{id}/review` | Admin/Manager theo chi nhánh |
| GET | `/api/attendance-corrections/me/history?shiftAssignmentId=...` | Employee, công của mình |
| GET | `/api/attendance-corrections/history?shiftAssignmentId=...` | Admin/Manager theo chi nhánh |

Tra cứu tối đa 93 ngày, lọc theo ngày ca tại chi nhánh. Body gửi: `shiftAssignmentId`, `checkInAt`, `checkOutAt` (ISO 8601 có offset/UTC), `breakMinutes`, `reason`. Body duyệt: `approved` boolean, `note`. Mã phản hồi: 201 khi tạo; 200 khi đọc/xử lý; 400 sai quy tắc/đã xử lý/dữ liệu cũ; 403 sai quyền/chi nhánh; 404 không thấy hoặc không sở hữu; 409 có đơn PENDING trùng.

Payroll response thêm `sourceChanged`. Endpoint đối soát công cũ giữ nguyên nhưng bắt buộc `note`.

## Môi trường kiểm thử riêng

```powershell
docker compose -f compose.attendance-test.yml up -d --build
python qa-automation/scripts/attendance_workflow_e2e.py
```

- Frontend: http://localhost:23000
- Backend: http://localhost:28080
- Admin: `admin` / `Demo@123456` (chỉ môi trường QA cục bộ).
- Tài khoản nhân viên/quản lý sinh riêng mỗi lượt, cùng mật khẩu QA trên; xem `qa-automation/results/attendance-workflow.json`. Không lưu token vào báo cáo.
- DB `attendance_qa`, volume `smart-shift-attendance_attendance_qa_data`, network riêng 172.31.238.0/24; frontend là proxy tin cậy 172.31.238.10. Không dùng volume ứng dụng hay môi trường hybrid cũ. Nếu máy đã dùng subnet này, đổi cùng subnet/IP frontend/trusted-proxies trong compose QA trước khi chạy.
- Script từ chối DB khác tên, chỉ seed/thao tác fixture QA, không xóa dữ liệu; tạo ca quá khứ bằng SQL fixture và kiểm tra nghiệp vụ bằng API thực. Tác vụ tự tạo ABSENT tắt riêng ở QA để tránh làm thay đổi baseline trong kiểm thử.
- Dừng môi trường bằng `docker compose -f compose.attendance-test.yml down`; lệnh này giữ volume. Không cần xóa DB để chạy lại script.

Kiểm thử tự động gồm phân quyền theo chủ sở hữu/chi nhánh, header IP giả, proxy hợp lệ, đơn trùng, công chưa thay đổi lúc gửi, duyệt/hủy/từ chối, baseline cũ, hai lượt submit/review đồng thời, tính lại công và hai trường hợp chạy đua với xác nhận lương.

## Kịch bản kiểm tra bằng tay

| Bước | Thao tác | Kết quả mong đợi |
|---|---|---|
| 1 | Nhân viên mở Chấm công, tra cứu ca đã kết thúc trong 30 ngày | Thấy ca hợp lệ để đề nghị bổ sung |
| 2 | Gửi giờ vào/ra và lý do | Đơn PENDING; công hiện tại không đổi |
| 3 | Gửi thêm đơn cho cùng ca | Bị chặn đơn trùng |
| 4 | Quản lý chi nhánh khác xem/duyệt | Không được truy cập |
| 5 | Quản lý đúng chi nhánh duyệt kèm lý do | Công cập nhật và có người/thời gian duyệt, nhân viên nhận thông báo |
| 6 | Mở Lịch sử ở cả tài khoản nhân viên và quản lý | Thấy giá trị trước/sau và người xử lý đúng |
| 7 | Đối chiếu bảng lương nháp đã tính trước bước 5 | Có cờ Cần tính lại công, không xác nhận được |
| 8 | Tính lại, xử lý hết đơn chờ rồi xác nhận lương | Lương dùng công đã duyệt; xác nhận thành công |
| 9 | Đề nghị/chỉnh trực tiếp công thuộc kỳ đã xác nhận | Bị chặn |
| 10 | Một người chỉnh công sau khi nhân viên gửi đơn | Đơn cũ không được áp đè; phải từ chối và gửi lại |
| 11 | Từ chối hoặc nhân viên hủy đơn PENDING | Giờ công giữ nguyên, không duyệt lại được đơn đó |

Fixture tự động kết thúc với lương đã xác nhận để kiểm chứng khóa tài chính. Để tạo bộ dữ liệu mới chưa chốt lương và thực hành gửi/duyệt trên giao diện:

```powershell
python qa-automation/scripts/attendance_workflow_e2e.py --seed-only
```

Tài khoản demo nằm trong `qa-automation/results/attendance-demo.json`, cùng mật khẩu `Demo@123456`. Lệnh tạo bộ tài khoản/chi nhánh/ca mới mỗi lần, không sửa/xóa bản lương đã xác nhận và không ghi đè kết quả E2E đầy đủ.

Kết quả kiểm tra tự động và lệnh đối chiếu SQL: [ATTENDANCE_VALIDATION.md](ATTENDANCE_VALIDATION.md).
