# Smart Shift System

Smart Shift là hệ thống quản lý nhân sự làm việc theo ca, hỗ trợ lập lịch thủ công và tự động, đăng ký ca trống, đổi/nhường ca, chấm công GPS, tính công/lương dự tính, thông báo realtime trên web và gửi thông báo qua Lark.

Hệ thống phù hợp với cửa hàng, nhà hàng hoặc doanh nghiệp vận hành theo nhiều ca. Smart Shift là nơi lưu dữ liệu và xử lý nghiệp vụ chính; Lark chỉ đóng vai trò kênh thông báo bên ngoài.

## Mục lục

- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Phân quyền](#phân-quyền)
- [Chức năng đã hoàn thành](#chức-năng-đã-hoàn-thành)
- [Quy trình sử dụng tổng thể](#quy-trình-sử-dụng-tổng-thể)
- [Khởi động bằng Docker](#khởi-động-bằng-docker)
- [Dữ liệu và tài khoản mặc định](#dữ-liệu-và-tài-khoản-mặc-định)
- [Hướng dẫn sử dụng theo chức năng](#hướng-dẫn-sử-dụng-theo-chức-năng)
- [Cấu hình chấm công GPS](#cấu-hình-chấm-công-gps)
- [Cấu hình Lark](#cấu-hình-lark)
- [API chính](#api-chính)
- [Cấu trúc project](#cấu-trúc-project)
- [Kiểm tra và xử lý lỗi](#kiểm-tra-và-xử-lý-lỗi)
- [Phạm vi hiện tại](#phạm-vi-hiện-tại)

## Công nghệ sử dụng

### Backend

- Java 17.
- Spring Boot 4.1.
- Spring MVC REST API.
- Spring Security, JWT HS256 và BCrypt.
- Spring Data JPA/Hibernate.
- Flyway Migration.
- Java HTTP Client để gọi Lark Open API và webhook.

### Frontend

- React 19.
- Vite 8.
- React Router.
- Ant Design và Ant Design Icons.
- FullCalendar.
- Lịch trực quan ngày/tuần/tháng, kéo thả phân công và kiểm tra ràng buộc khi lưu. Xem [hướng dẫn lịch trực quan](docs/INTERACTIVE_SCHEDULE.md).
- Axios.
- Server-Sent Events (SSE) cho thông báo realtime trên web.

### Hạ tầng

- PostgreSQL 17.
- Docker và Docker Compose.
- Nginx phục vụ frontend, proxy REST API và luồng SSE đến backend.

## Phân quyền

| Vai trò | Phạm vi chính |
|---|---|
| `ROLE_ADMIN` | Quản trị toàn hệ thống, tài khoản, danh mục, mọi chi nhánh và tích hợp Lark |
| `ROLE_MANAGER` | Quản lý lịch, duyệt yêu cầu, chấm công và lương trong chi nhánh được giao |
| `ROLE_EMPLOYEE` | Xem lịch cá nhân, khai báo lịch rảnh, gửi yêu cầu, nhận ca, đổi ca, chấm công và xem lương của mình |

Một số nguyên tắc phân quyền quan trọng:

- Không có đăng ký tài khoản công khai. Admin tạo tài khoản cho người dùng.
- Mã tài khoản được backend tự sinh: Admin dùng `AD000001...`; Manager và Employee dùng `NV000001...`. Mã được giữ nguyên khi đổi vai trò.
- Manager chỉ thao tác trên dữ liệu thuộc chi nhánh của mình.
- Nhân viên chỉ thao tác trên dữ liệu cá nhân hoặc yêu cầu mà họ tham gia.
- Tài khoản bị khóa không thể đăng nhập; JWT cũ cũng bị từ chối.
- Nhân viên không bị xóa cứng để bảo toàn lịch sử lịch làm, chấm công và lương.

## Chức năng đã hoàn thành

### 1. Xác thực và tài khoản

- Đăng nhập bằng username/password và nhận JWT.
- Mật khẩu được mã hóa bằng BCrypt.
- Phân quyền API và giao diện theo vai trò.
- Admin tạo, cập nhật, tìm kiếm, lọc, khóa/mở tài khoản và đặt lại mật khẩu.
- Kiểm tra trùng mã nhân viên, username và email.
- Hồ sơ gồm chi nhánh, loại hợp đồng, định mức giờ, giới hạn giờ, thời gian nghỉ tối thiểu, đơn giá giờ và hệ số lương. Vị trí làm việc chỉ bắt buộc với Employee; Admin và Manager không thuộc vị trí dùng để xếp ca.
- Backend hỗ trợ người dùng tự đổi mật khẩu bằng mật khẩu hiện tại.

### 2. Dashboard theo vai trò

Dashboard tự thay đổi theo tài khoản đăng nhập.

Nhân viên xem được:

- Số ca và tổng giờ dự kiến trong tuần.
- Giờ công và lương tạm tính trong tháng.
- Tỷ lệ chấm công đúng giờ.
- Yêu cầu đang chờ, ca trống phù hợp và thông báo chưa đọc.
- Ca gần nhất và tối đa 5 ca sắp tới.

Admin/Manager xem được:

- Số nhân viên đang hoạt động và ngừng hoạt động.
- Số ca hôm nay, ca đủ người và ca thiếu người.
- Số người đã check-in, đi trễ, vắng và đến giờ nhưng chưa check-in.
- Đơn nghỉ, yêu cầu nhận ca và đổi ca đang chờ duyệt.
- Giờ được xếp, giờ thực tế trong tuần và chi phí nhân công dự tính trong tháng.
- Lịch làm việc trong 7 ngày tới.

### 3. Danh mục tổ chức

- Vai trò: Admin, Manager và Employee.
- Chi nhánh: mã, tên, địa chỉ, múi giờ, tọa độ GPS, bán kính chấm công và trạng thái.
- Vị trí công việc: mã, tên, mô tả và trạng thái.
- Backend có API quản lý chi nhánh và vị trí; dữ liệu này được dùng trong màn hình quản lý nhân viên và xếp lịch.

### 4. Mẫu ca

- Tạo và cập nhật mẫu ca theo chi nhánh.
- Khai báo giờ bắt đầu, giờ kết thúc, thời gian nghỉ và màu hiển thị.
- Hỗ trợ ca qua ngày, ví dụ `22:00–06:00`.
- Kích hoạt hoặc ngừng sử dụng mẫu ca mà không làm mất dữ liệu lịch cũ.

### 5. Lịch rảnh

Người dùng có thể khai báo theo từng ngày và khung giờ:

- `AVAILABLE`: có thể làm.
- `PREFERRED`: ưu tiên được xếp.
- `UNAVAILABLE`: không thể làm.

Có thể thêm, sửa, xóa và xem lịch rảnh theo khoảng ngày. Khi xếp lịch, khoảng rảnh phải bao phủ toàn bộ ca và không được giao với khoảng `UNAVAILABLE`.

### 6. Đơn xin nghỉ

- Gửi đơn nghỉ phép năm, nghỉ bệnh, nghỉ không lương hoặc lý do khác.
- Theo dõi trạng thái `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`.
- Người gửi được hủy đơn còn hợp lệ.
- Admin/Manager duyệt hoặc từ chối và nhập ghi chú.
- Đơn đã duyệt trở thành ràng buộc cứng khi phân công nhân viên.
- Kết quả xử lý được gửi qua thông báo web và Lark nếu đã cấu hình.

### 7. Kỳ xếp lịch

- Tạo kỳ lịch theo chi nhánh và khoảng ngày.
- Cập nhật kỳ khi còn ở trạng thái Nháp.
- Trạng thái thực tế: `DRAFT` → `PUBLISHED` → `LOCKED`.
- Kiểm tra điều kiện trước khi công bố.
- Chỉ công bố khi có ít nhất một ca hoạt động, mọi ca có nhu cầu nhân sự, phân công hợp lệ và không thiếu/thừa so với giới hạn.
- Sau khi công bố, nhân viên được thông báo để kiểm tra lịch.
- Kỳ đã khóa không còn được chỉnh sửa theo luồng lập lịch thông thường.

### 8. Ca làm thực tế và nhu cầu nhân sự

- Tạo ca riêng hoặc sinh hàng loạt từ mẫu ca trong kỳ lịch.
- Chỉnh ngày, giờ, thời gian nghỉ và ghi chú khi kỳ còn Nháp.
- Hủy hoặc mở lại ca; dữ liệu cũ vẫn được giữ.
- Khai báo nhu cầu nhân sự cho từng vị trí với số lượng tối thiểu, tối đa và độ ưu tiên.
- Theo dõi số người đã phân công, số còn thiếu và trạng thái ca.

### 9. Phân công thủ công

- Xem danh sách ứng viên cho từng vị trí trong ca.
- Hiển thị lịch rảnh, tổng giờ dự kiến và kết quả đánh giá của từng ứng viên.
- Phân công hoặc gỡ nhân viên khỏi ca khi kỳ còn Nháp.
- Tự cập nhật trạng thái ca theo mức độ đáp ứng nhu cầu.

Một nhân viên chỉ được xếp khi đáp ứng toàn bộ ràng buộc sau:

- Tài khoản và chi nhánh đang hoạt động.
- Có vai trò Employee, đúng chi nhánh và đúng vị trí yêu cầu.
- Có lịch rảnh bao phủ toàn bộ thời gian ca.
- Không có đơn nghỉ đã được duyệt.
- Không trùng với ca đã được phân công.
- Đủ thời gian nghỉ tối thiểu giữa các ca khác ngày hoặc có ca qua đêm; ca gãy nằm trọn cùng ngày được phép khi không trùng giờ và không vượt giờ ngày/tuần. Xem [quy tắc ca gãy/Clopening](docs/SPLIT_SHIFT_POLICY.md).
- Không vượt số giờ tối đa trong ngày hoặc tuần.
- Ca nằm trong kỳ lịch và có thời gian nghỉ hợp lệ.

### 10. Xếp lịch tự động

- Thuật toán lai **ALNS–CP-SAT**, thay thế Weighted Greedy trong runtime.
- CP-SAT tạo lịch ban đầu; ALNS lựa chọn vùng phân công và CP-SAT giải lại vùng đó.
- Ưu tiên giảm thiếu nhân sự, rồi ưu tiên nhu cầu quan trọng, sau đó tối ưu định mức giờ,
  công bằng, nguyện vọng và chi phí/ngân sách tuần.
- Admin/Manager cấu hình ngân sách tuần tại Kỳ xếp lịch; dự toán tách chi phí kỳ này/kỳ khác cùng tuần và cảnh báo vượt trước công bố. Ngân sách là mục tiêu mềm. Xem [hướng dẫn ngân sách](docs/WEEKLY_SCHEDULING_BUDGET.md).
- Kiểm tra lịch rảnh, nghỉ phép, vị trí, trùng ca, giờ nghỉ, giới hạn giờ ngày/tuần và ngày liên tiếp.
- Giữ nguyên mọi phân công đã lưu; chỉ bổ sung đến số lượng tối thiểu còn thiếu trong kỳ Nháp.
- Kết quả trả thêm thuật toán, trạng thái bộ giải, điểm phạt toàn lịch, số vòng ALNS, thời gian và seed.
- Không gán điểm Greedy cho từng người; trường score của phân công mới là null.

Bảng chấm công và bảng lương hỗ trợ **xuất Excel `.xlsx`** theo bộ lọc và quyền chi nhánh, có sheet chi tiết/tổng hợp, định dạng tiền/giờ và cảnh báo nguồn công cần tính lại. Xem [hướng dẫn xuất Excel](docs/EXCEL_REPORTS.md).
- OPTIMAL chỉ được báo khi CP-SAT chứng minh tối ưu trên toàn mô hình với phân công cũ cố định.
- Chỉ số qualityScore vẫn mô tả độ phủ/nguyện vọng, không phải hàm mục tiêu hay optimality gap.

Cấu hình mặc định: 8 giây tìm kiếm, tối đa 20 vòng ALNS, seed 42.
Tải native lần đầu, chuẩn bị dữ liệu và lưu kết quả có thể làm tổng request dài hơn.
Xem mô hình, thang điểm, cấu hình, kiểm thử và giới hạn tại
[docs/HYBRID_SCHEDULING.md](docs/HYBRID_SCHEDULING.md).

Bộ dữ liệu công khai, dữ liệu mô phỏng và đối chứng Greedy/CP-SAT/hybrid:
[Kế hoạch thực nghiệm](qa-automation/benchmarks/EXPERIMENT_PLAN.md).

Kết quả sau sửa ngân sách khởi tạo và dừng sớm:
[Báo cáo benchmark v2](qa-automation/benchmarks/reports/2026-11-04-budget-v2/README.md).

Phiên bản mới nhất về mô hình gọn hơn, tìm nghiệm bài lớn và vùng ALNS có giới hạn:
[Báo cáo benchmark v3](qa-automation/benchmarks/reports/2026-11-04-scale-v3/README.md).

### 11. Ca trống và đăng ký nhận ca

- Nhân viên xem các vị trí còn thiếu trong kỳ lịch đang ở trạng thái Nháp.
- Chỉ hiển thị ca đúng chi nhánh, đúng vị trí và thỏa ràng buộc của nhân viên.
- Nhân viên gửi hoặc hủy yêu cầu nhận ca.
- Admin/Manager duyệt hoặc từ chối.
- Khi duyệt, hệ thống tạo phân công với nguồn `CLAIM`, cập nhật trạng thái ca và ghi audit.
- Nếu vị trí đã đủ người, các yêu cầu đang chờ còn lại được từ chối tự động.

### 12. Đổi ca và nhường ca

Hỗ trợ hai loại yêu cầu:

- `SWAP`: đổi hai phân công giữa hai nhân viên.
- `GIVEAWAY`: nhường một ca cho nhân viên phù hợp khác.

Quy trình:

```text
Nhân viên A tạo yêu cầu
→ Nhân viên B chấp nhận hoặc từ chối
→ Hệ thống kiểm tra lại toàn bộ ràng buộc
→ Admin/Manager duyệt hoặc từ chối
→ Phân công được chuyển và ghi lịch sử
```

Chỉ ca tương lai thuộc lịch đã công bố nhưng chưa khóa, chưa có dữ liệu chấm công và chưa nằm trong yêu cầu khác mới được đổi/nhường. Người tham gia yêu cầu không được tự xét duyệt.

### 13. Chấm công GPS

- Nhân viên check-in và check-out bằng vị trí thiết bị.
- Hệ thống dùng thời gian máy chủ, không dùng giờ do trình duyệt gửi lên.
- Kiểm tra độ chính xác GPS tối đa 200 m.
- Kiểm tra khoảng cách với tọa độ chi nhánh bằng công thức Haversine.
- Chỉ được check-in trong vòng 2 giờ trước ca và trước khi ca kết thúc.
- Cho check-out tối đa 12 giờ sau khi ca kết thúc.
- Có dung sai 5 phút cho đi trễ và về sớm.
- Tự tính phút đi trễ, về sớm, tăng ca và tổng thời gian làm thực tế.
- Tự tạo bản ghi `ABSENT` cho phân công đã kết thúc nhưng không check-in; tác vụ mặc định chạy mỗi 15 phút.
- Admin/Manager xem theo ngày, chi nhánh, nhân viên, trạng thái và có thể điều chỉnh/duyệt bản công kèm ghi chú.

Nhân viên có thể gửi đơn bổ sung/điều chỉnh công cho ca đã kết thúc trong 30 ngày;
quản lý xét duyệt trong tab **Điều chỉnh công**. Mọi thay đổi công mới có lịch sử trước/sau.
Mạng được xác minh bằng IP server nhận từ proxy tin cậy; SSID/BSSID tự khai chỉ dùng đối soát.
Xem [quy trình, cấu hình mạng và kiểm thử](docs/ATTENDANCE_WORKFLOW.md).

### 14. Tính công và lương dự tính

- Admin/Manager chọn chi nhánh và khoảng ngày, tối đa 93 ngày.
- Chỉ dùng bản chấm công đã có check-in, check-out và được quản lý duyệt.
- Giữ nguyên bảng lương đã xác nhận khi chạy tính lại.
- Công thay đổi làm bảng lương nháp cần tính lại; chặn xác nhận khi công đã đổi hoặc còn đơn điều chỉnh chờ duyệt.
- Không sửa công thuộc kỳ lương đã xác nhận.
- Có thể nhập thưởng và ghi chú khi bảng lương còn Nháp.
- Xác nhận từng bảng lương và gửi thông báo cho nhân viên.
- Nhân viên xem lịch sử công/lương của chính mình.

Công thức hiện tại:

```text
Phút thực tế = Check-out - Check-in - Thời gian nghỉ
Lương cơ bản = (Phút thực tế / 60) × Đơn giá giờ × Hệ số lương
Tổng dự tính = Lương cơ bản + Thưởng
```

Đây là lương dự tính phục vụ quản lý nội bộ, chưa phải chức năng thanh toán lương.

### 15. Lịch sử thay đổi lịch

- Ghi tự động người thao tác, thời gian, hành động, đối tượng và lý do.
- Ghi nhận tạo/sửa kỳ lịch, sinh/sửa/hủy ca, thay đổi nhu cầu, phân công/gỡ người, xếp tự động, công bố, khóa và chuyển ca.
- Lưu dữ liệu trước và sau thay đổi để xem chi tiết.
- Lọc theo thời gian, chi nhánh, kỳ lịch và loại hành động.
- Nhật ký không thể sửa từ giao diện.

### 16. Thông báo trên web

- Danh sách thông báo có phân trang và bộ lọc chưa đọc.
- Đếm số thông báo chưa đọc trên thanh điều hướng.
- Đánh dấu một thông báo hoặc tất cả thông báo là đã đọc.
- Push realtime bằng SSE khi người dùng đang mở web.
- Tự kết nối lại với thời gian chờ tăng dần nếu luồng SSE bị ngắt.
- Lưu thông báo trong PostgreSQL nên người dùng vẫn xem lại được sau khi đăng nhập lại.

### 17. Tích hợp Lark

Hệ thống có hai kênh độc lập:

| Kênh | Mục đích |
|---|---|
| App Bot cá nhân | Gửi riêng lịch, kết quả duyệt, đổi ca, nhận ca và lương cho đúng người nhận |
| Webhook nhóm | Gửi cảnh báo vận hành chung cho nhóm quản lý |

App Bot cá nhân nhận mọi sự kiện đã tạo thông báo web. Webhook nhóm chỉ nhận các sự kiện vận hành cần quản lý chú ý: công bố lịch, có đơn xin nghỉ mới, có yêu cầu nhận ca mới và yêu cầu đổi/nhường ca đã được người nhận chấp nhận.

Các khả năng quản trị Lark:

- Xem trạng thái cấu hình từng kênh.
- Đồng bộ một hoặc tất cả tài khoản bằng email/số điện thoại để lấy `open_id`.
- Gửi thử tin cá nhân và tin nhóm.
- Xem lịch sử, người nhận, trạng thái, số lần thử và lỗi gần nhất.
- Gửi lại bản ghi thất bại.
- Xử lý hàng đợi nền và tự thử lại; mặc định tối đa 3 lần, cách nhau 30 giây.
- Gắn đường dẫn quay lại đúng màn hình Smart Shift trong tin nhắn.
- Lỗi Lark không làm hỏng nghiệp vụ chính hoặc thông báo trên web.

### Tóm tắt thuật toán và cơ chế kỹ thuật nổi bật

| Chức năng | Thuật toán hoặc cơ chế | Ý nghĩa |
|---|---|---|
| Xếp lịch tự động | Hybrid ALNS–CP-SAT | Tối ưu độ phủ, định mức giờ, công bằng, nguyện vọng và chi phí |
| Phân công, nhận ca, đổi ca | Dùng chung bộ kiểm tra ràng buộc | Không tạo ra phân công sai vị trí, trùng giờ, vượt giờ hoặc thiếu thời gian nghỉ |
| Chấm công GPS | Công thức Haversine và kiểm tra độ chính xác thiết bị | Tính khoảng cách theo tọa độ Trái Đất và chỉ cho chấm công trong bán kính chi nhánh |
| Tính công/lương | Tính thời lượng thực tế theo phút, đơn giá giờ và hệ số lương | Kết quả có thể kiểm tra lại từ dữ liệu check-in/check-out |
| Lịch sử thay đổi | Audit trail lưu dữ liệu trước/sau | Truy vết ai thay đổi lịch, thay đổi lúc nào và nội dung thay đổi |
| Thông báo web | Server-Sent Events, tự kết nối lại theo backoff | Đẩy thông báo một chiều gần realtime khi người dùng đang mở web |
| Thông báo Lark | Hàng đợi nền, retry và cô lập lỗi | Lỗi dịch vụ ngoài không rollback nghiệp vụ chính; người dùng có thể nhận tin khi đóng Smart Shift |

## Quy trình sử dụng tổng thể

Quy trình đầy đủ để vận hành một kỳ làm việc:

```text
Admin tạo Manager và Employee
→ Cấu hình hồ sơ, giới hạn giờ, đơn giá và hệ số lương
→ Admin/Manager tạo mẫu ca
→ Tạo kỳ xếp lịch ở trạng thái Nháp
→ Sinh ca từ mẫu hoặc tạo ca riêng
→ Khai báo nhu cầu nhân sự cho từng vị trí
→ Nhân viên đăng ký lịch rảnh
→ Xếp lịch tự động hoặc phân công thủ công
→ Nhân viên đăng ký nhận các ca còn thiếu nếu cần
→ Kiểm tra điều kiện và công bố lịch
→ Nhân viên đổi/nhường ca nếu phát sinh
→ Khóa lịch khi đã chốt
→ Nhân viên check-in/check-out bằng GPS
→ Quản lý kiểm tra và duyệt bảng công
→ Tính lương, nhập thưởng và xác nhận
→ Nhân viên xem công/lương và nhận thông báo
```

## Khởi động bằng Docker

### Yêu cầu

- Docker Desktop đang chạy.
- Docker Compose v2.
- Các cổng `3000`, `8080` và `5432` chưa bị ứng dụng khác sử dụng, hoặc đã đổi trong `.env`.

### 1. Tạo file cấu hình

PowerShell:

```powershell
Copy-Item .env.example .env
```

Bash:

```bash
cp .env.example .env
```

Có thể chạy bằng giá trị mặc định mà không cần tạo `.env`, nhưng nên tạo file này để thay JWT secret, mật khẩu database và cấu hình Lark. Không commit `.env`.

### 2. Build và chạy hệ thống

```bash
docker compose up --build -d
```

Địa chỉ mặc định:

- Web: <http://localhost:3000>
- Backend trực tiếp: <http://localhost:8080>
- API qua Nginx: <http://localhost:3000/api>
- PostgreSQL: `localhost:5432`

### 3. Kiểm tra container và log

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
```

### 4. Dừng hoặc khởi động lại

```bash
docker compose down
docker compose up -d
```

`docker compose down` vẫn giữ dữ liệu trong volume `postgres_data`.

> `docker compose down -v` sẽ xóa toàn bộ database trong volume. Chỉ dùng khi chắc chắn muốn tạo lại dữ liệu từ đầu.

## Dữ liệu và tài khoản mặc định

Khi database mới được tạo, Flyway thêm:

- Ba vai trò: Admin, Manager và Employee.
- Chi nhánh `STORE_HCM_001` – Chi nhánh Hồ Chí Minh 01.
- Các vị trí: Quản lý ca, Thu ngân, Phục vụ, Nhân viên bếp và Nhân viên kho.
- Ba mẫu ca: Sáng `06:00–14:00`, Chiều `14:00–22:00`, Tối `22:00–06:00`; mỗi ca nghỉ 30 phút.

Nếu `BOOTSTRAP_ADMIN_ENABLED=true`, backend tạo tài khoản Admin khi username chưa tồn tại:

```text
Username: admin
Password: Admin@123456
```

Mật khẩu trên chỉ dành cho môi trường phát triển. Hãy đổi `BOOTSTRAP_ADMIN_PASSWORD` và `JWT_SECRET` trước khi triển khai thật.

Database mới không tự tạo Manager hoặc Employee. Đăng nhập Admin, mở **Nhân viên** và tạo các tài khoản cần dùng. Mật khẩu ban đầu phải có từ 8 đến 72 ký tự.

## Hướng dẫn sử dụng theo chức năng

### Tạo dữ liệu để thử xếp lịch

1. Đăng nhập bằng Admin.
2. Mở **Nhân viên**, tạo ít nhất một Manager và hai Employee.
3. Gán Employee cùng chi nhánh và vị trí, ví dụ Thu ngân.
4. Đặt giới hạn giờ/ngày, giờ/tuần, giờ nghỉ tối thiểu, đơn giá và hệ số lương.
5. Đăng nhập từng Employee, mở **Lịch rảnh** và đăng ký khoảng rảnh bao phủ ca cần xếp.
6. Đăng nhập Admin/Manager, mở **Kỳ xếp lịch** và tạo một kỳ Nháp.
7. Mở **Lịch làm việc**, chọn kỳ và **Sinh ca từ mẫu**.
8. Với từng ca, khai báo **Nhu cầu nhân sự**.
9. Chọn **Tự động xếp lịch** hoặc mở **Phân công nhân viên** để xếp tay.
10. Nếu còn thiếu người, Employee có thể mở **Ca trống** để đăng ký; Admin/Manager duyệt tại **Duyệt nhận ca**.
11. Chọn **Kiểm tra và công bố lịch**. Sửa hết lỗi được liệt kê trước khi công bố.

### Thử đổi hoặc nhường ca

1. Phải có lịch `PUBLISHED`, chưa khóa và ca chưa bắt đầu.
2. Đăng nhập Employee A, mở **Đổi/nhường ca**.
3. Chọn phân công của mình và chọn đổi trực tiếp hoặc nhường công khai.
4. Đăng nhập Employee B để chấp nhận.
5. Đăng nhập Admin/Manager, mở **Duyệt đổi ca** và duyệt yêu cầu.
6. Kiểm tra lịch cá nhân của hai nhân viên và **Lịch sử thay đổi**.

### Thử chấm công và tính lương

1. Tạo ca gần thời điểm hiện tại và phân công Employee.
2. Công bố kỳ lịch.
3. Bảo đảm tọa độ chi nhánh đúng với nơi đang thử và thiết bị nằm trong bán kính cho phép.
4. Đăng nhập Employee, mở **Chấm công**, cho phép trình duyệt truy cập vị trí và bấm **Check-in**.
5. Sau đó bấm **Check-out**.
6. Đăng nhập Admin/Manager, mở **Quản lý chấm công**, chọn khoảng ngày, kiểm tra thời gian vào/ra và duyệt bản công.
7. Mở **Tính công và lương**, chọn cùng chi nhánh và khoảng ngày rồi bấm tính.
8. Nhập thưởng nếu có và xác nhận bảng lương.
9. Đăng nhập Employee, mở **Công và lương** để xem kết quả.

### Xem thông báo

- Biểu tượng chuông trên header hiển thị thông báo mới ngay khi web đang mở.
- Mở **Thông báo** để lọc chưa đọc, đọc chi tiết hoặc đánh dấu tất cả đã đọc.
- Đóng web không làm mất dữ liệu thông báo, nhưng SSE chỉ realtime khi trang đang mở.
- Nếu cần nhận tin khi không mở Smart Shift, hãy cấu hình Lark App Bot cá nhân.

## Cấu hình chấm công GPS

Chi nhánh phải có đủ `latitude`, `longitude` và `attendanceRadiusMeters`. Bán kính hợp lệ từ 10 m đến 5.000 m; giá trị mặc định là 200 m.

Backend cung cấp:

```http
GET /api/locations
PUT /api/locations/{id}
```

Khi test trên trình duyệt:

- Bật GPS hoặc Location Services của thiết bị.
- Cho phép trang web truy cập vị trí.
- Chờ độ chính xác giảm xuống không quá 200 m rồi mới bấm chấm công.
- `localhost` được trình duyệt xem là secure context; khi triển khai bằng domain thật nên dùng HTTPS.
- Nếu báo ngoài bán kính, cập nhật tọa độ chi nhánh theo vị trí làm việc thật; không tăng bán kính quá lớn chỉ để vượt kiểm tra.

## Cấu hình Lark

Lark mặc định bị tắt để hệ thống vẫn chạy khi chưa có credential.

### App Bot cá nhân

1. Vào [Lark Developer](https://open.larksuite.com/) và tạo ứng dụng nội bộ cho workspace.
2. Bật khả năng Bot.
3. Cấp quyền cần thiết để tra cứu user ID bằng email/số điện thoại và [gửi tin nhắn](https://open.larksuite.com/document/server-docs/im-v1/message/create).
4. Phát hành/cài ứng dụng trong workspace và bảo đảm nhân viên thuộc phạm vi sử dụng ứng dụng.
5. Lấy App ID và App Secret.
6. Email hoặc số điện thoại trên Smart Shift phải khớp tài khoản Lark. Số điện thoại nên có mã quốc gia.

Điền vào `.env`:

```dotenv
LARK_APP_ENABLED=true
LARK_APP_ID=cli_xxxxxxxxxxxxxxxx
LARK_APP_SECRET=xxxxxxxxxxxxxxxx
LARK_API_BASE_URL=https://open.larksuite.com/open-apis
SMART_SHIFT_PUBLIC_URL=https://smart-shift.example.com
```

API tra cứu đang dùng là [Batch get user ID](https://open.larksuite.com/document/server-docs/contact-v3/user/batch_get_id). App ID và App Secret chỉ nằm trong biến môi trường; database chỉ lưu `open_id`, thời gian đồng bộ và lỗi gần nhất.

### Webhook nhóm quản lý

1. Mở nhóm Lark cần nhận cảnh báo.
2. Thêm Custom Bot và lấy webhook URL theo [hướng dẫn Custom Bot](https://open.larksuite.com/document/client-docs/bot-v3/add-custom-bot).
3. Nếu bật ký request, sao chép secret của bot.

Điền vào `.env`:

```dotenv
LARK_ENABLED=true
LARK_WEBHOOK_URL=https://open.larksuite.com/open-apis/bot/v2/hook/xxxxxxxx
LARK_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
```

Hiện tại hệ thống dùng một webhook nhóm chung, chưa tách một chat riêng cho từng chi nhánh.

### Hoàn tất cấu hình trên Smart Shift

1. Khởi động lại backend:

   ```bash
   docker compose up -d --build backend
   ```

2. Đăng nhập Admin và mở <http://localhost:3000/admin/integrations/lark>.
3. Kiểm tra hai thẻ trạng thái kênh.
4. Bấm **Đồng bộ tất cả** hoặc đồng bộ từng tài khoản.
5. Gửi thử tin cá nhân cho một người đã liên kết.
6. Bấm **Gửi thử nhóm** để kiểm tra webhook.
7. Xem **Lịch sử gửi Lark**; nếu một bản ghi thất bại, sửa cấu hình rồi bấm gửi lại.

Lưu ý: khi email hoặc số điện thoại của người dùng được sửa, liên kết Lark cũ sẽ bị xóa và cần đồng bộ lại. `SMART_SHIFT_PUBLIC_URL` phải là địa chỉ mà người nhận có thể truy cập; không dùng `localhost` khi triển khai cho thiết bị khác.

## Biến môi trường quan trọng

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `POSTGRES_DB` | `smart_shift_db` | Tên database |
| `POSTGRES_USER` | `smart_shift` | Tài khoản PostgreSQL |
| `POSTGRES_PASSWORD` | `smart_shift_password` | Mật khẩu PostgreSQL |
| `FRONTEND_PORT` | `3000` | Cổng web |
| `BACKEND_PORT` | `8080` | Cổng backend |
| `JWT_SECRET` | Giá trị development | Secret ký JWT, cần ít nhất 32 byte |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `PT1H` | Thời hạn access token |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` | Cho phép tạo Admin ban đầu |
| `BOOTSTRAP_ADMIN_USERNAME` | `admin` | Username Admin ban đầu |
| `BOOTSTRAP_ADMIN_PASSWORD` | `Admin@123456` | Mật khẩu Admin ban đầu |
| `LARK_APP_ENABLED` | `false` | Bật tin nhắn cá nhân |
| `LARK_APP_ID` | Trống | App ID của Lark App |
| `LARK_APP_SECRET` | Trống | App Secret của Lark App |
| `LARK_ENABLED` | `false` | Bật webhook nhóm |
| `LARK_WEBHOOK_URL` | Trống | URL Custom Bot của nhóm |
| `LARK_WEBHOOK_SECRET` | Trống | Secret ký webhook, có thể để trống nếu bot không bật chữ ký |
| `SMART_SHIFT_PUBLIC_URL` | `http://localhost:3000` | Link Smart Shift gắn trong tin Lark |
| `LARK_MAX_ATTEMPTS` | `3` | Số lần gửi tối đa |
| `LARK_RETRY_DELAY` | `PT30S` | Khoảng chờ giữa các lần gửi |

Các biến cấu hình đầy đủ nằm trong [.env.example](./.env.example).

## API chính

API trả JSON và sử dụng prefix `/api`. Ngoại trừ đăng nhập, endpoint được bảo vệ yêu cầu header:

```http
Authorization: Bearer <access-token>
```

### Xác thực, dashboard và thông báo

```http
POST  /api/auth/login
GET   /api/auth/me
GET   /api/dashboard
GET   /api/notifications
GET   /api/notifications/unread-count
GET   /api/notifications/stream
PATCH /api/notifications/{id}/read
PATCH /api/notifications/read-all
```

### Người dùng và danh mục

```http
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
PATCH  /api/users/{id}/status
PATCH  /api/users/me/password
POST   /api/users/{id}/reset-password
GET    /api/roles
GET    /api/roles/{id}
GET    /api/locations
GET    /api/locations/{id}
POST   /api/locations
PUT    /api/locations/{id}
GET    /api/positions
GET    /api/positions/{id}
POST   /api/positions
PUT    /api/positions/{id}
```

### Lịch rảnh, nghỉ phép và lịch làm

```http
GET    /api/employee-availabilities/me
POST   /api/employee-availabilities/me
PUT    /api/employee-availabilities/me/{id}
DELETE /api/employee-availabilities/me/{id}
GET    /api/employee-availabilities/users/{userId}

GET   /api/time-off-requests/me
POST  /api/time-off-requests/me
PATCH /api/time-off-requests/me/{id}/cancel
GET   /api/time-off-requests
PATCH /api/time-off-requests/{id}/review

GET   /api/shift-templates
GET   /api/shift-templates/{id}
POST  /api/shift-templates
PUT   /api/shift-templates/{id}
PATCH /api/shift-templates/{id}/status

GET  /api/schedule-periods
GET  /api/schedule-periods/{id}
POST /api/schedule-periods
PUT  /api/schedule-periods/{id}
GET  /api/schedule-periods/{id}/publication-check
POST /api/schedule-periods/{id}/publish
POST /api/schedule-periods/{id}/lock

GET   /api/work-shifts
GET   /api/work-shifts/{id}
POST  /api/work-shifts
POST  /api/work-shifts/generate
PUT   /api/work-shifts/{id}
PATCH /api/work-shifts/{id}/status

GET /api/shift-requirements
PUT /api/shift-requirements/work-shifts/{workShiftId}

GET    /api/shift-assignments/me
GET    /api/shift-assignments/work-shifts/{workShiftId}
GET    /api/shift-assignments/work-shifts/{workShiftId}/candidates
POST   /api/shift-assignments/work-shifts/{workShiftId}
DELETE /api/shift-assignments/{assignmentId}
POST   /api/schedules/generate
GET    /api/schedule-audits
```

### Nhận ca, đổi ca, chấm công và lương

```http
GET   /api/open-shift-claims/available
GET   /api/open-shift-claims/me
POST  /api/open-shift-claims
PATCH /api/open-shift-claims/me/{id}/cancel
GET   /api/open-shift-claims
PATCH /api/open-shift-claims/{id}/review

GET   /api/shift-swap-requests/me
GET   /api/shift-swap-requests/available
GET   /api/shift-swap-requests/candidates
POST  /api/shift-swap-requests
PATCH /api/shift-swap-requests/me/{id}/respond
PATCH /api/shift-swap-requests/me/{id}/cancel
GET   /api/shift-swap-requests
PATCH /api/shift-swap-requests/{id}/review

POST /api/attendances/check-in
POST /api/attendances/check-out
GET  /api/attendances/me
GET  /api/attendances
PUT  /api/attendances/{id}/approve

GET   /api/payroll/me
GET   /api/payroll
POST  /api/payroll/calculate
PATCH /api/payroll/{id}/bonus
POST  /api/payroll/{id}/confirm
```

### Tích hợp Lark

```http
GET  /api/integrations/lark/status
GET  /api/integrations/lark/deliveries
POST /api/integrations/lark/test
GET  /api/integrations/lark/users
POST /api/integrations/lark/users/{id}/sync
POST /api/integrations/lark/users/sync-all
POST /api/integrations/lark/users/{id}/test
POST /api/integrations/lark/deliveries/{id}/retry
```

## Cấu trúc project

```text
smart-shift-system/
├── smart-shift-be/                  Spring Boot backend
│   ├── src/main/java/com/smartshift/
│   │   ├── config/                  Security, JWT, Lark, bootstrap
│   │   ├── controller/              REST controllers
│   │   ├── dto/                     Request/response DTO
│   │   ├── entity/                  JPA entities
│   │   ├── enums/                   Trạng thái nghiệp vụ
│   │   ├── integration/lark/        Lark webhook và App API client
│   │   ├── mapper/                  Chuyển entity/DTO
│   │   ├── repository/              Spring Data repositories
│   │   ├── scheduler/               Tác vụ vắng mặt và hàng đợi Lark
│   │   ├── security/                JWT và xử lý 401/403
│   │   └── service/                 Nghiệp vụ hệ thống
│   └── src/main/resources/
│       ├── application.yaml
│       └── db/migration/            Flyway V1–V16
├── smart-shift-fe/                  React frontend
│   ├── src/components/              Layout, guard, notification center
│   ├── src/pages/                   Các màn hình nghiệp vụ
│   ├── src/services/                REST/SSE clients
│   └── nginx.conf                   Proxy API và SSE trong Docker
├── docker-compose.yml
├── .env.example
└── README.md
```

## Kiểm tra và xử lý lỗi

### Build frontend

```bash
cd smart-shift-fe
npm ci
npm run lint
npm run build
```

### Build backend

PowerShell:

```powershell
cd smart-shift-be
.\mvnw.cmd test
```

Bash:

```bash
cd smart-shift-be
./mvnw test
```

Hoặc kiểm tra build bằng Docker:

```bash
docker compose build backend frontend
```

### Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| Không mở được web | Chạy `docker compose ps` và xem log frontend/backend |
| Backend không khởi động | Kiểm tra PostgreSQL healthy, mật khẩu DB và log Flyway |
| Đăng nhập Admin thất bại | Kiểm tra Admin đã tồn tại trong volume và giá trị bootstrap lúc tạo lần đầu |
| Không xếp được nhân viên | Kiểm tra đúng vị trí/chi nhánh, lịch rảnh bao phủ ca, đơn nghỉ, trùng ca và giới hạn giờ |
| Không công bố được lịch | Mở bước kiểm tra công bố và xử lý ca thiếu/thừa, ca chưa có nhu cầu hoặc phân công lỗi |
| Không thấy ca trống | Ca phải còn thiếu người, ở kỳ Nháp, đúng vị trí/chi nhánh và nhân viên phải thỏa ràng buộc |
| Không đổi được ca | Lịch phải đã công bố, chưa khóa, ca chưa bắt đầu và chưa có dữ liệu chấm công |
| Trình duyệt không lấy GPS | Bật Location, cấp quyền cho site, dùng localhost hoặc HTTPS |
| GPS chưa đủ chính xác | Chờ thiết bị cập nhật vị trí đến khi accuracy không quá 200 m |
| Không tính ra giờ/lương | Check-out bản công, quản lý duyệt rồi tính lại đúng khoảng ngày |
| Không đồng bộ được Lark | Kiểm tra App đã phát hành, quyền API, email/số điện thoại khớp và App ID/Secret |
| Tin Lark thất bại | Xem lỗi tại trang tích hợp, sửa cấu hình rồi bấm gửi lại |

## Phạm vi hiện tại

Đã hoàn thành phạm vi MVP phục vụ đồ án với đầy đủ luồng từ lập lịch đến công/lương. Các giới hạn chủ động của phiên bản hiện tại:

- Không có đăng ký tài khoản công khai.
- Mỗi người dùng thuộc một chi nhánh và một vị trí chính.
- Chưa có Machine Learning dự đoán nhu cầu; nhu cầu được nhập bởi quản lý.
- Lương chỉ là dự tính, chưa có thuế, bảo hiểm, phụ cấp phức tạp hoặc thanh toán.
- Thông báo Lark là một chiều; mọi thao tác duyệt và chỉnh sửa vẫn thực hiện trong Smart Shift.
- Một webhook nhóm Lark dùng chung cho toàn hệ thống; App Bot cá nhân gửi đúng từng người.
- Chưa có ứng dụng mobile riêng; giao diện web có thể dùng trên máy tính và trình duyệt di động.
