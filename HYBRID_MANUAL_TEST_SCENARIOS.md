# Kịch bản kiểm thử bằng tay — Xếp lịch ALNS–CP-SAT

Ngày soạn: 04/11/2026. Áp dụng cho môi trường `compose.test.yml` và dữ liệu của `hybrid_e2e.py`.

Mục tiêu: kiểm tra thao tác trên giao diện, kết quả phân công, ràng buộc, công bố và phân quyền.
Các ca trong tài liệu này bắt đầu ở trạng thái **NOT RUN**; kết quả API tự động trước đây không được tính là đã kiểm thử giao diện.

## 1. Chuẩn bị

Chạy tại thư mục gốc `smart-shift-system`:

```powershell
docker compose -f compose.test.yml up -d --build
```

Đợi backend khởi động. Tạo bộ dữ liệu mới để tự bấm sinh lịch:

```powershell
python qa-automation/scripts/hybrid_e2e.py --new-run --seed-only
```

Lệnh trên tạo thêm dữ liệu, không xóa bộ cũ. Các kỳ chính ở trạng thái Nháp, chưa chạy thuật toán.
Riêng kỳ `E2E previous Sunday boundary` của bộ night được seed và công bố trước để làm dữ liệu biên.
Nếu dùng bộ đã chạy tự động ngày 03/11, balanced/night đã công bố nên không còn phù hợp để làm lại các bước sinh lịch từ đầu.

| Thành phần | Giá trị |
|---|---|
| Giao diện cần mở | http://localhost:13000 |
| API kiểm thử | http://localhost:18080 |
| Admin | `admin` / `Demo@123456` |
| Mật khẩu mặc định của nhân viên và quản lý mẫu | `Demo@123456` |
| Thông tin bộ dữ liệu mới nhất | `qa-automation/results/hybrid-e2e.json` |

Nếu trước đó đã cấu hình biến `E2E_PASSWORD` hoặc `E2E_ADMIN_PASSWORD`, sử dụng mật khẩu tương ứng thay giá trị mặc định.
Dùng cửa sổ ẩn danh hoặc trình duyệt khác khi cần đăng nhập nhân viên/Manager đồng thời với Admin.

### 1.1. Lấy đúng tài khoản và ID của lần seed mới

Trong PowerShell:

```powershell
$e2eRun = Get-Content -Raw -Encoding UTF8 qa-automation/results/hybrid-e2e.json | ConvertFrom-Json
$e2eRun | Select-Object runId, startDate, baseUrl
$e2eRun.cases | Select-Object kind, locationId, periodId, start, end, status

$balancedCase = $e2eRun.cases | Where-Object kind -eq 'balanced'
$shortageCase = $e2eRun.cases | Where-Object kind -eq 'shortage'
$nightCase = $e2eRun.cases | Where-Object kind -eq 'night'

$balancedCase.manager
$balancedCase.employees | Format-Table id, username, positionId
$shortageCase.manager
$nightCase.employees | Format-Table id, username, positionId
```

Tên tài khoản có dạng `e2e_<runId>_bal_01`, `e2e_<runId>_bal_mgr`, v.v.
Không dùng nguyên tài khoản của một lần chạy cũ. Mỗi lần seed có thể tạo chi nhánh/kỳ cùng tên hiển thị;
đối chiếu `locationId` và `periodId` trong manifest. Khi cần, mở thẳng:

```powershell
"http://localhost:13000/admin/work-shifts?periodId=$($balancedCase.periodId)"
"http://localhost:13000/admin/work-shifts?periodId=$($shortageCase.periodId)"
"http://localhost:13000/admin/work-shifts?periodId=$($nightCase.periodId)"
```

Tìm đúng chi nhánh trong trang Nhân viên bằng mã tài khoản có `runId`; không suy ra ID từ ví dụ cũ.
Manifest là thông tin của script: trường `status` không tự cập nhật khi bạn thao tác bằng tay.
Trạng thái trên giao diện/API/database mới là trạng thái đang có của kỳ lịch.

### 1.2. Quy ước dữ liệu và tính giờ

Gọi **D** là `startDate` trong manifest, tức thứ Hai bắt đầu kỳ chính.
Ví dụ nếu D = 09/11/2026 thì D+2 = 11/11, D+3 = 12/11; Chủ nhật trước kỳ là D−1 = 08/11.

| Bộ | Nhân viên | Kỳ chính | Số ca | Tổng nhu cầu tối thiểu | Dữ liệu có sẵn trước khi sinh lịch |
|---|---:|---|---:|---:|---|
| balanced | 24 | D đến D+13 | 42 | 126 vị trí | 1 phân công MANUAL, 1 đơn nghỉ đã duyệt, 1 ngày UNAVAILABLE |
| shortage | 6 | D đến D+6 | 21 | 63 vị trí | Chưa có phân công, ngân sách thấp |
| night | 8 | D đến D+6 | 14 | 28 vị trí | Ngoài kỳ có 1 phân công MANUAL vào đêm D−1 |

- balanced/shortage: ca 06:00–14:00, 14:00–22:00 và 22:00–06:00 hôm sau.
- night: ca 06:00–14:00 và 22:00–06:00 hôm sau.
- Mỗi ca nghỉ 30 phút, nên giờ công là **7,5 giờ**, không phải 8 giờ.
- Full-time: tối thiểu 32 giờ/tuần, tối đa 40 giờ/tuần.
- Part-time: tối thiểu 16 giờ/tuần, tối đa 24 giờ/tuần.
- Tối đa 8 giờ công/ngày, nghỉ ít nhất 11 giờ giữa các ca, tối đa 5 ngày làm liên tiếp.
- Ngày và tuần tính theo `Asia/Ho_Chi_Minh`; tuần bắt đầu thứ Hai.
- Giờ công của ca qua đêm hiện được tính toàn bộ vào ngày/tuần bắt đầu ca.
- Giới hạn tối đa là ràng buộc bắt buộc. Đạt đủ giờ tối thiểu, nguyện vọng và ngân sách là mục tiêu mềm.

### 1.3. Thứ tự chạy

```text
HYB-01 → HYB-11: balanced còn Nháp
→ HYB-12: công bố balanced
→ HYB-13 → HYB-15: xem lịch, thông báo và quyền
→ HYB-16 → HYB-18: shortage
→ HYB-19 → HYB-22: night
→ HYB-23: khóa một kỳ đã công bố
→ HYB-24: đối soát database
→ HYB-25: bổ sung trường hợp không có ứng viên
```

Không công bố hoặc khóa balanced/night trước khi hoàn thành các bước kiểm tra ở trạng thái Nháp.
Nếu cần làm lại từ đầu, tạo một bộ mới bằng lệnh `--new-run --seed-only`; không cần xóa volume.

## 2. balanced — đủ nhân sự

### HYB-01 — Đăng nhập Admin vào môi trường test

**Thao tác:** mở `http://localhost:13000`, đăng nhập tài khoản Admin trong mục 1.

**Mong đợi:** vào Dashboard; có menu Nhân viên, Kỳ xếp lịch, Lịch làm việc, Lịch sử thay đổi.
Ghi URL đang sử dụng để xác nhận đang kiểm tra stack cổng 13000.

### HYB-02 — Kiểm tra dữ liệu đầu vào của balanced

**Thao tác:** mở Nhân viên, lọc đúng chi nhánh của balanced; xem hồ sơ full-time và part-time.
Mở Lịch làm việc, chọn đúng `periodId`; xem mẫu ca và nhu cầu nhân sự ở một ca sáng, chiều, đêm.

**Mong đợi:** 24 Employee trong bộ, 42 ca; mỗi ca cần 1 Thu ngân, 1 Phục vụ, 1 Bếp, tối đa cũng là 1/vị trí.
Giờ làm và thời gian nghỉ khớp mục 1.2. Không tính tài khoản Manager vào số ứng viên xếp ca.

### HYB-03 — Ghi lại phân công cố định trước khi tối ưu

**Thao tác:** mở ca `fixedShiftId` của balanced bằng trang Lịch làm việc.
Ở cột thao tác, mở **Phân công nhân viên**; lưu ảnh nhân viên và nguồn phân công.
Đối chiếu `fixedAssignmentId` trong manifest nếu xem thêm API/DevTools.

**Mong đợi:** `employees[0]` của balanced đã được phân công ở nguồn MANUAL trước khi chạy thuật toán.
Ghi rõ nhân viên, ngày, giờ ca và ID để so lại ở HYB-07.

### HYB-04 — Mở rồi hủy hộp thoại sinh lịch

**Thao tác:** vào Kỳ xếp lịch, tìm balanced Nháp; rê chuột lên biểu tượng tia sét có tooltip **Tự động xếp lịch**.
Mở hộp thoại rồi bấm **Hủy**, chưa bấm **Bắt đầu xếp lịch**.

**Mong đợi:** hộp thoại đóng; không tạo thêm phân công. Ca ở HYB-03 vẫn chỉ có phân công cố định ban đầu.

### HYB-05 — Sinh lịch balanced

**Thao tác:** mở lại **Tự động xếp lịch**, bấm **Bắt đầu xếp lịch**, đợi trả kết quả.
Lưu ảnh phần tổng hợp và ít nhất một trang trong bảng **Phân công vừa tạo**.

**Mong đợi của bộ dữ liệu này:**

| Chỉ số | Giá trị mong đợi |
|---|---:|
| Phân công mới | 125 |
| Đã xếp / Tối thiểu | 126 / 126 |
| Ca đã đủ người | 42 / 42 |
| Vị trí còn thiếu | 0 |
| Độ phủ | 100% |

Kỳ vẫn là Nháp sau sinh lịch. Nếu chưa đủ người, ghi đúng số thực tế, trạng thái bộ giải và thời gian;
không tự đánh dấu PASS theo số tham khảo. Trạng thái FEASIBLE không chứng minh lịch đã tốt nhất.

### HYB-06 — Kiểm tra thông tin thuật toán và ý nghĩa chỉ số

**Thao tác:** xem thông báo ALNS–CP-SAT, thời gian bộ giải, số vòng ALNS và bảng phân công.
Nếu cần xem chi tiết, mở DevTools → Network → request `POST /api/schedules/generate` → Response.

**Mong đợi:**

- `optimization.algorithm = ALNS_CP_SAT`; thời gian không âm, số vòng không vượt cấu hình mặc định 20.
- `status` là OPTIMAL hoặc FEASIBLE khi có phân công mới.
- Không hiển thị FEASIBLE thành “đã chứng minh tối ưu”.
- Cột Nguồn trong bảng kết quả thể hiện ALNS–CP-SAT; nguồn nghiệp vụ lưu trong phân công vẫn là AUTO.
- `score` cá nhân của AUTO mới có thể là `null`; đây không phải lỗi thiếu điểm Greedy.
- Phần trăm độ phủ/nguyện vọng không được diễn giải là mức chứng minh tối ưu.

Không yêu cầu thời gian đúng bằng 8 giây: đây là giới hạn tìm kiếm, còn request có thêm thời gian tải native,
đọc dữ liệu, dựng mô hình và lưu. Không yêu cầu số vòng luôn bằng 20 nếu hết thời gian trước đó.

### HYB-07 — Bảo toàn phân công cố định sau khi sinh lịch

**Thao tác:** bấm **Kiểm tra và điều chỉnh ca**, mở lại ca ở HYB-03 → Phân công nhân viên.
So với ảnh và ID đã ghi trước đó.

**Mong đợi:** nhân viên, ca và nguồn MANUAL cũ được giữ nguyên, không bị tạo trùng hoặc chuyển thành AUTO.
Hai vị trí còn lại trong ca có thể được bổ sung AUTO.

### HYB-08 — Sinh lại balanced trước công bố

**Thao tác:** khi balanced vẫn Nháp và đã đủ người, quay về Kỳ xếp lịch → Tự động xếp lịch → Bắt đầu xếp lịch.

**Mong đợi:** phân công mới = 0; tổng vẫn 126; không tạo trùng người/ca; lịch cũ không bị đổi.
Có thể nhận trạng thái NO_CANDIDATES do không còn nhu cầu cần bổ sung. Trạng thái đó không phải lỗi server.

Lần chạy thứ hai không tối ưu lại những AUTO đã được lưu ở lần thứ nhất; hiện mọi phân công đã lưu đều được giữ cố định.

### HYB-09 — Nghỉ phép và ngày không rảnh

**Thao tác:** xem toàn bộ phân công của hai người sau trong balanced; đối chiếu Lịch rảnh/Đơn nghỉ:

| Nhân viên trong manifest | Dữ liệu cần đối chiếu |
|---|---|
| `employees[1]`, tài khoản kết thúc `_bal_02` | Nghỉ đã duyệt từ D+2 00:00 đến D+3 00:00 |
| `employees[23]`, tài khoản kết thúc `_bal_24` | UNAVAILABLE toàn ngày D+3 |

**Mong đợi:** không có phân công giao với các khoảng trên.
Kiểm tra cả ca đêm bắt đầu ngày trước rồi kéo dài sang ngày nghỉ/bận;
không chỉ lọc những ca có ngày bắt đầu trùng ngày nghỉ.

Ví dụ ca 22:00 ngày D+1 đến 06:00 ngày D+2 không được giao cho `_bal_02`.

### HYB-10 — Giờ công, giờ nghỉ, số ngày liên tiếp và vị trí

**Thao tác:** chọn ít nhất 1 full-time và 1 part-time; liệt kê ca theo ngày bắt đầu cho từng tuần.
Tính giờ công = số ca × 7,5; tính khoảng cách giữa giờ kết thúc và giờ bắt đầu ca kế tiếp.
Đối chiếu vị trí của nhân viên với vị trí phân công.

**Mong đợi:** không trùng giờ, nghỉ ít nhất 11 giờ; không quá 8 giờ công/ngày;
full-time không quá 40 giờ/tuần, part-time không quá 24 giờ/tuần; không quá 5 ngày liên tiếp, kể cả qua tuần.
Chỉ Employee đúng chi nhánh/vị trí được xếp.

Nếu giao diện không cung cấp đủ tổng hợp để kiểm hết, dùng HYB-24 đối soát toàn bộ thay cho việc chỉ quan sát vài dòng.

### HYB-11 — Nguyện vọng và lịch sử chạy

**Thao tác:** xem Lý do lựa chọn trong kết quả và mở **Lịch sử thay đổi**, lọc đúng kỳ balanced.
Đối chiếu nguyện vọng PREFERRED của nhân viên với các phân công mới.

**Mong đợi:** giải thích lịch rảnh/nguyện vọng phù hợp dữ liệu; có audit sinh lịch tự động của người thao tác.
Không yêu cầu 100% phân công phải là PREFERRED, hoặc tất cả nhân viên có giờ bằng nhau.
Nguyện vọng, công bằng và chi phí có thể đánh đổi trong mục tiêu mềm; đây không phải ca chứng minh tối ưu nghiên cứu.

### HYB-12 — Công bố balanced

**Điều kiện:** hoàn thành HYB-03 đến HYB-11; balanced còn Nháp, đủ 126 phân công.

**Thao tác:** Kỳ xếp lịch → biểu tượng có tooltip **Kiểm tra và công bố lịch**.
Xem kiểm tra rồi bấm **Công bố lịch**.

**Mong đợi:** hiển thị **Lịch đã đáp ứng điều kiện công bố**, Phân công lỗi = 0;
nút Công bố lịch khả dụng, thao tác thành công và kỳ chuyển PUBLISHED/Đã công bố.

## 3. Nhân viên và phân quyền

### HYB-13 — Nhân viên xem lịch và nhận thông báo

**Thao tác:** đăng nhập `_bal_01` trong cửa sổ ẩn danh; vào **Lịch của tôi**, chọn D đến D+13.
Mở chuông hoặc trang Thông báo.

**Mong đợi:** có lịch của nhân viên này thuộc balanced, trạng thái Đã công bố;
có thông báo công bố lịch. Kiểm tra tên, vị trí và ngày giờ, không dùng tài khoản Admin để thay cho bước này.

Có thể đăng nhập nhân viên trước HYB-12 để quan sát SSE; khi đó thông báo mới phải xuất hiện mà không cần F5.
Nếu đăng nhập sau công bố, chỉ xác nhận lịch sử thông báo; không kết luận SSE đã được kiểm thử.

### HYB-14 — Manager chỉ quản lý chi nhánh của mình

**Thao tác:** đăng nhập `balanced.manager.username` trong manifest; vào Lịch làm việc, chọn kỳ balanced.
Sau đó thử mở thẳng đường dẫn kỳ shortage theo `periodId` ở mục 1.1.

**Mong đợi:** xem được dữ liệu balanced; không lấy được ca hoặc thao tác quản lý của shortage.
Nếu kiểm Network, API lấy ca của shortage trả 403. Giao diện có thể báo lỗi hoặc không đưa kỳ ngoài phạm vi vào lựa chọn.

### HYB-15 — Employee không được sinh lịch quản lý

**Thao tác:** đăng nhập một Employee, kiểm tra menu và mở thẳng `/admin/schedule-periods`.

**Mong đợi:** không có quyền sử dụng trang quản lý/sinh lịch; route chặn hoặc điều hướng về trang được phép.
Chỉ quan sát menu chưa chứng minh backend phân quyền đúng. Nếu cần xác nhận backend,
chạy phần kiểm tra API tự động trên bộ mới hoặc kiểm request bị từ chối bằng tài khoản Employee.

## 4. shortage — thiếu nhân sự

### HYB-16 — Sinh lịch khi không thể đủ người

**Thao tác:** dùng Admin hoặc Manager của shortage; chọn kỳ shortage Nháp → Tự động xếp lịch → Bắt đầu xếp lịch.
Lưu ảnh phần tổng hợp và bảng Ca còn thiếu nhân sự.

**Mong đợi:** xử lý thành công, có phân công hợp lệ và có phần thiếu được báo rõ; không phá ràng buộc để đạt 100%.
Kỳ vẫn Nháp.

Lần chạy API tham khảo ngày 03/11/2026: **28 phân công, còn thiếu 35/63 vị trí**, độ phủ khoảng 44,44%.
Với cấu hình fixture hiện tại, 28 cũng là cận trên từ số ngày/giờ của các nhóm vị trí.
Nếu bộ giải trả ít hơn 28 trong giới hạn thời gian, ghi nhận để đánh giá chất lượng;
đừng kết luận có vi phạm ràng buộc chỉ vì kết quả khác số tham khảo.

### HYB-17 — Chặn công bố lịch thiếu người

**Thao tác:** mở **Kiểm tra và công bố lịch** của shortage.

**Mong đợi:** **Lịch chưa thể công bố**, có danh sách ca/vị trí thiếu;
Phân công lỗi có thể bằng 0 vì thiếu người khác với phân công sai.
Nút **Công bố lịch** bị vô hiệu hóa; kỳ giữ DRAFT.
API công bố, nếu gọi trực tiếp bằng Admin, cũng phải trả 400.

### HYB-18 — Sinh lại không ghi đè hoặc tạo trùng

**Thao tác:** ghi số phân công hiện có của shortage, chạy sinh lịch lần nữa; kiểm tra các ca đã có người.

**Mong đợi:** phân công cũ được giữ nguyên; không trùng cặp người–ca, không vượt maximum=1 cho mỗi vị trí.
Nếu không còn ứng viên khả thi thì phân công mới = 0 và vẫn báo thiếu.
Không đòi hỏi thuật toán phải hoán đổi AUTO cũ để sửa shortage, vì hiện các phân công đã lưu là cố định.

## 5. night — ca đêm và lịch biên

### HYB-19 — Sinh lịch night

**Thao tác:** chọn kỳ chính night từ D đến D+6, không chọn nhầm kỳ Chủ nhật trước đó.
Bấm Tự động xếp lịch và xem kết quả.

**Mong đợi của fixture:** 28 phân công mới, 14/14 ca đủ người, 0 vị trí thiếu.
Ca 22:00–06:00 có thông tin qua ngày/hôm sau đúng; không đảo ngược thời gian hoặc tính giờ công âm.

### HYB-20 — Nghỉ đủ sau ca đêm Chủ nhật ngoài kỳ

**Thao tác:** xem `night.boundaryShiftId`, ghi phân công `night.employees[0]` từ 22:00 ngày D−1 đến 06:00 ngày D.
Trong kết quả kỳ chính, kiểm tra ca 06:00 ngày D của nhân viên này.

**Mong đợi:** ca cố định cũ còn nguyên và người đó không được nhận ca sáng 06:00–14:00 ngày D.
Khoảng nghỉ ở trường hợp này là 0 giờ, thấp hơn giới hạn 11 giờ.
Người đó có thể nhận ca đêm 22:00 ngày D nếu các điều kiện khác cho phép: khoảng nghỉ là 16 giờ.

### HYB-21 — Đối chiếu ca qua đêm và ngày/tuần tính công

**Thao tác:** đăng nhập một nhân viên được xếp night, xem lịch từ D−1 đến D+7.
Đếm ngày làm liên tiếp và tính giờ theo ngày bắt đầu ca; đối chiếu ca cũ tại Chủ nhật nếu là người ở HYB-20.

**Mong đợi:** dữ liệu giờ/ngày nhất quán với quy ước hiện tại; ca qua đêm có 7,5 giờ công.
Giờ nghỉ xét theo thời điểm thực tế qua nửa đêm; không tự bỏ qua vì hai ca thuộc hai tuần/kỳ khác nhau.
Một ca đêm bắt đầu trước khoảng tra cứu nhưng còn giao với khoảng đó có thể được trả về;
đối chiếu thêm ID kỳ lịch, không mặc định mọi dòng đều thuộc kỳ chính.

### HYB-22 — Công bố night

**Thao tác:** khi kỳ chính night đủ người, mở kiểm tra công bố và công bố lịch.

**Mong đợi:** không có phân công lỗi, công bố thành công; nhân viên xem được lịch Đã công bố và thông báo.
Ca cố định ở kỳ trước không bị sửa/xóa bởi thao tác công bố kỳ chính.

## 6. Trạng thái lịch và đối soát

### HYB-23 — Chặn sinh lịch sau công bố/khóa

**Điều kiện:** balanced hoặc night đã công bố; thực hiện sau các bước cần kỳ Nháp.

**Thao tác:** xem các hành động của kỳ đã công bố. Thử mở chức năng phân công để quan sát khả năng chỉnh sửa.
Sau đó chọn biểu tượng **Khóa kỳ lịch**, xác nhận khóa một kỳ test đã công bố.

**Mong đợi:** không còn hành động sinh lịch thông thường trên kỳ đã công bố; không sửa nhân sự theo luồng Nháp.
Sau khóa, trạng thái là LOCKED/Đã khóa. Nếu gọi API sinh lịch trực tiếp bằng Admin cho kỳ PUBLISHED hoặc LOCKED,
phải nhận lỗi nghiệp vụ 400, không tạo thêm phân công.

### HYB-24 — Đối soát toàn bộ phân công đã lưu bằng SQL

Đây là bước hỗ trợ bằng lệnh, bổ sung cho các kiểm tra bằng tay trên giao diện.
Chạy tại thư mục gốc:

```powershell
docker cp qa-automation/sql/verify_hybrid_e2e.sql smart-shift-e2e-postgres:/tmp/verify_hybrid_e2e.sql
docker exec smart-shift-e2e-postgres psql -U smart_shift_test -d smart_shift_e2e -f /tmp/verify_hybrid_e2e.sql
```

**Mong đợi:** `all_checks_passed = 1`; cả 11 nhóm có số vi phạm bằng 0:

| Nhóm đối soát | Mong đợi |
|---|---:|
| Sai vị trí/chi nhánh/nhân viên ngừng hoạt động | 0 |
| Trùng thời gian ca | 0 |
| Thiếu giờ nghỉ | 0 |
| Vượt giờ ngày | 0 |
| Vượt giờ tuần | 0 |
| Vượt số ngày liên tiếp | 0 |
| Giao đơn nghỉ đã duyệt | 0 |
| Không được lịch rảnh bao phủ | 0 |
| Giao UNAVAILABLE | 0 |
| Trùng người trong cùng ca | 0 |
| Vượt nhân sự tối đa theo vị trí | 0 |

SQL hiện kiểm tra mọi chi nhánh mã `E2E_%` trong database test, bao gồm các lần seed cũ.
Nếu lỗi, xác định ID nhân viên/ca và lần seed liên quan trước khi quy kết cho bộ mới.
Không yêu cầu số vị trí thiếu người bằng 0 ở shortage: thiếu người được phép trong Nháp và không phải một vi phạm trong 11 nhóm trên.

### HYB-25 — Không có ứng viên có lịch rảnh (bổ sung bằng tay)

**Điều kiện:** dùng Admin, chi nhánh balanced của bộ test; thực hiện sau các ca chính để không làm thay đổi số liệu của chúng.

**Thao tác:**

1. Tạo kỳ Nháp mới một ngày tại D+21, tên `E2E - Khong co lich ranh`.
2. Sinh đúng 1 ca từ mẫu 06:00–14:00 có sẵn của chi nhánh balanced.
3. Khai báo nhu cầu 1 Thu ngân, tối đa 1, priority=1.
4. Không khai báo thêm lịch rảnh cho ngày D+21; fixture gốc chỉ khai báo đến D+14.
5. Chạy Tự động xếp lịch cho kỳ mới.

**Mong đợi:** 0 phân công mới, còn thiếu 1; thông báo không có ứng viên phù hợp/lịch rảnh hoặc trạng thái NO_CANDIDATES;
không lỗi server, không lấy người từ chi nhánh khác, không tự giả định nhân viên rảnh.
Kiểm tra công bố phải chặn kỳ này. Nếu đã tạo kỳ khác trùng D+21, chọn một ngày trống xa hơn và vẫn chưa có lịch rảnh.

## 7. Bảng ghi kết quả thực tế

Điền sau khi thực hiện. PASS = đúng kỳ vọng; FAIL = sai kỳ vọng; BLOCKED = chưa thể thực hiện vì thiếu điều kiện;
NOT RUN = chưa chạy. Không copy kết quả PASS từ script/API sang bảng kiểm thử bằng tay này.

| ID | Nội dung | Thực tế / ID ảnh hoặc lỗi | Kết quả |
|---|---|---|---|
| HYB-01 | Đăng nhập đúng môi trường | | NOT RUN |
| HYB-02 | Dữ liệu đầu vào balanced | | NOT RUN |
| HYB-03 | Ghi nhận MANUAL ban đầu | | NOT RUN |
| HYB-04 | Hủy hộp thoại không sinh lịch | | NOT RUN |
| HYB-05 | Sinh lịch balanced | | NOT RUN |
| HYB-06 | Metadata và ý nghĩa chỉ số | | NOT RUN |
| HYB-07 | Giữ MANUAL | | NOT RUN |
| HYB-08 | Sinh lại balanced không trùng | | NOT RUN |
| HYB-09 | Nghỉ phép và UNAVAILABLE | | NOT RUN |
| HYB-10 | Giờ, nghỉ, ngày liên tiếp, vị trí | | NOT RUN |
| HYB-11 | Nguyện vọng và audit | | NOT RUN |
| HYB-12 | Công bố balanced | | NOT RUN |
| HYB-13 | Nhân viên xem lịch/thông báo | | NOT RUN |
| HYB-14 | Manager trong phạm vi chi nhánh | | NOT RUN |
| HYB-15 | Employee không quản lý lịch | | NOT RUN |
| HYB-16 | Sinh lịch thiếu nhân sự | | NOT RUN |
| HYB-17 | Chặn công bố shortage | | NOT RUN |
| HYB-18 | Sinh lại shortage giữ dữ liệu | | NOT RUN |
| HYB-19 | Sinh lịch night | | NOT RUN |
| HYB-20 | Giờ nghỉ qua ranh giới tuần | | NOT RUN |
| HYB-21 | Giờ công và khoảng tra cứu ca đêm | | NOT RUN |
| HYB-22 | Công bố night | | NOT RUN |
| HYB-23 | Trạng thái công bố/khóa | | NOT RUN |
| HYB-24 | Đối soát SQL toàn bộ | | NOT RUN |
| HYB-25 | Không có lịch rảnh | | NOT RUN |

### 7.1. Thông tin phiên kiểm thử

| Trường | Giá trị điền khi chạy |
|---|---|
| Người kiểm thử | |
| Ngày/giờ | |
| Trình duyệt | |
| Commit/bản build | |
| runId | |
| Ngày D | |
| ID kỳ balanced / shortage / night | |
| Tổng PASS / FAIL / BLOCKED / NOT RUN | |

### 7.2. Ghi số đo sinh lịch

Chụp kết quả ngay ở lần chạy đầu tiên, trước khi sinh lại. Bảng kết quả lần hai chỉ phản ánh phần bổ sung của lần hai.

| Bộ | Phân công trước chạy | AUTO mới | Tổng sau chạy | Còn thiếu | Độ phủ | Thời gian solver | Vòng ALNS | Status | Objective nếu lấy từ API |
|---|---:|---:|---:|---:|---|---|---:|---|---|
| balanced | 1 | | | | | | | | |
| shortage | 0 | | | | | | | | |
| night (kỳ chính) | 0 | | | | | | | | |

Mục tiêu 100% độ phủ của balanced/night là tiêu chí của fixture cụ thể này, không phải cam kết mọi bộ dữ liệu đều đủ người.
Không so sánh số objective giữa balanced và shortage vì khác dữ liệu, số biến và thang điểm suy ra.

### 7.3. Mẫu báo lỗi

```text
ID lỗi:
Test case:
runId / periodId / employeeId / shiftId:
Điều kiện trước khi chạy:
Các bước tái hiện:
Kết quả mong đợi:
Kết quả thực tế:
Ảnh / video / HTTP status / response:
Mức độ và ảnh hưởng:
```

## 8. Bằng chứng cần lưu và phạm vi kết luận

- Ảnh kết quả lần sinh đầu cho cả 3 bộ, ảnh cảnh báo shortage và màn hình kiểm tra công bố.
- Ảnh MANUAL trước/sau, nghỉ phép/UNAVAILABLE và trường hợp ca đêm ngoài kỳ.
- Ảnh lịch/chuông của Employee và quyền Manager khác chi nhánh.
- Output SQL ở HYB-24; response API sinh lịch nếu cần phân tích status/objective/seed.
- Ghi lỗi trung thực; không đánh dấu các bước chưa thực hiện là PASS.

Chỉ cần chạy trước HYB-01 đến HYB-08 nếu muốn làm quen; sau đó tiếp tục các ca còn lại theo đúng thứ tự.
Bộ này đánh giá tính đúng đắn và luồng sử dụng. Nó chưa chứng minh ALNS–CP-SAT tốt hơn Greedy hoặc chạy tốt ở quy mô lớn;
phần đó cần benchmark riêng trên cùng dữ liệu, thời gian, phần cứng và nhiều seed.

Tài liệu liên quan:

- [Cách chạy môi trường và dữ liệu test](docs/LOCAL_E2E.md).
- [Mô hình và giới hạn thuật toán hiện tại](docs/HYBRID_SCHEDULING.md).
- [Script seed/kiểm thử API](qa-automation/scripts/hybrid_e2e.py).
- [SQL đối soát](qa-automation/sql/verify_hybrid_e2e.sql).
