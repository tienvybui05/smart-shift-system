# 🧪 KỊCH BẢN TEST HỆ THỐNG SMART SHIFT

> Bộ test tay dành riêng cho thuật toán ALNS–CP-SAT và dữ liệu E2E tại cổng 13000: [HYBRID_MANUAL_TEST_SCENARIOS.md](HYBRID_MANUAL_TEST_SCENARIOS.md).

> Bộ test tay dành riêng cho thuật toán ALNS–CP-SAT và dữ liệu E2E tại cổng 13000: [HYBRID_MANUAL_TEST_SCENARIOS.md](HYBRID_MANUAL_TEST_SCENARIOS.md).

> **URL Dev:** http://localhost:5173  
> **URL Production:** http://localhost:3000  
> **Swagger UI:** http://localhost:8081  

---

## 👤 TÀI KHOẢN CẦN CHUẨN BỊ

| STT | Username | Password | Role | Mục đích |
|-----|----------|----------|------|----------|
| 1 | `admin` | `Admin@123456` | ADMIN | Quản trị toàn hệ thống |
| 2 | `manager01` | `Manager@123456` | MANAGER | Quản lý chi nhánh |
| 3 | `emp01` | `Emp@123456` | EMPLOYEE | Nhân viên toàn thời gian |
| 4 | `emp02` | `Emp@123456` | EMPLOYEE | Nhân viên thứ 2 (test đổi ca) |

> ⚠️ Tài khoản `admin` đã có sẵn. Các tài khoản còn lại cần **tạo bằng admin** trước khi test.

---

## ⚡ THỨ TỰ TEST KHUYẾN NGHỊ

```
Module 1 → Module 2 → Module 3 → Module 4 → Module 5
→ Module 6 → Module 7 → Module 8 → Module 9
→ Module 10 → Module 11 → Module 12 → End-to-End
```

---

## 📋 MODULE 1: XÁC THỰC & PHÂN QUYỀN

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 1.1 | Đăng nhập đúng | `admin` | Nhập đúng username/password → Login | Vào Dashboard thành công | | |
| 1.2 | Đăng nhập sai password | Bất kỳ | Nhập sai password → Login | Hiện thông báo lỗi xác thực | | |
| 1.3 | Đăng nhập tài khoản bị khóa | `emp01` (sau khi khóa) | Đăng nhập bình thường | Báo tài khoản bị khóa / từ chối | | |
| 1.4 | Phân quyền Admin | `admin` | Xem sidebar sau khi đăng nhập | Thấy đầy đủ tất cả menu kể cả "Tích hợp Lark" | | |
| 1.5 | Phân quyền Manager | `manager01` | Xem sidebar sau khi đăng nhập | Thấy menu quản lý, không thấy "Tích hợp Lark" | | |
| 1.6 | Phân quyền Employee | `emp01` | Xem sidebar sau khi đăng nhập | Chỉ thấy menu cá nhân (lịch, nghỉ, chấm công, lương) | | |
| 1.7 | Đăng xuất | Bất kỳ | Click tên → Đăng xuất | Về trang Login, không vào được trang nội bộ | | |

---

## 📋 MODULE 2: QUẢN LÝ NHÂN VIÊN

> **Truy cập:** Nhân viên (Admin) / Thông tin nhân viên (Manager)

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 2.1 | Tạo tài khoản Manager | `admin` | Nhân viên → Thêm nhân viên → Role: Manager → điền đầy đủ → Lưu | Tạo thành công, có mã NV tự động | | |
| 2.2 | Tạo tài khoản Employee | `admin` | Thêm nhân viên → Role: Employee → chọn chi nhánh, vị trí, loại HĐ → Lưu | Tạo thành công, có mã NV tự động | | |
| 2.3 | Tạo trùng username | `admin` | Thêm nhân viên → username đã tồn tại → Lưu | Báo lỗi trùng username | | |
| 2.4 | Sửa thông tin NV | `admin` | Click ✏️ → đổi tên/email → Lưu | Cập nhật thành công | | |
| 2.5 | Khóa tài khoản NV | `admin` | Click 🔒 vào emp01 → Xác nhận | Trạng thái → "Đã khóa" | | |
| 2.6 | Mở tài khoản NV | `admin` | Click 🔓 vào emp01 → Xác nhận | Trạng thái → "Hoạt động" | | |
| 2.7 | Không tự khóa chính mình | `admin` | Click 🔒 vào chính tài khoản admin | Nút bị disable, không thao tác được | | |
| 2.8 | Reset mật khẩu | `admin` | Click 🔑 → nhập mật khẩu mới → Lưu | Đăng nhập được bằng mật khẩu mới | | |
| 2.9 | Tìm kiếm theo tên | `admin` | Nhập tên vào ô tìm kiếm → Enter | Lọc đúng kết quả | | |
| 2.10 | Tìm kiếm theo mã NV | `admin` | Nhập mã NV → Enter | Hiện đúng nhân viên | | |
| 2.11 | Lọc theo chi nhánh | `admin` | Chọn chi nhánh trong dropdown | Chỉ hiện NV thuộc chi nhánh đó | | |
| 2.12 | Lọc theo trạng thái | `admin` | Chọn "Đang hoạt động" / "Đã khóa" | Lọc đúng trạng thái | | |
| 2.13 | Đặt lại bộ lọc | `admin` | Click "Đặt lại" | Hiện toàn bộ danh sách | | |
| 2.14 | Manager cập nhật hồ sơ NV | `manager01` | Thông tin nhân viên → Click ✏️ → đổi vị trí/giờ làm | Cập nhật hồ sơ công việc thành công | | |

---

## 📋 MODULE 3: MẪU CA

> **Truy cập:** Mẫu ca (Admin/Manager)

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 3.1 | Tạo mẫu ca | `admin` | Mẫu ca → Thêm → đặt tên, giờ bắt đầu/kết thúc → Lưu | Tạo thành công | | |
| 3.2 | Tạo mẫu ca qua đêm | `admin` | Thêm → giờ kết thúc < giờ bắt đầu (vd: 22:00 - 06:00) | Tạo thành công, hệ thống nhận diện ca đêm | | |
| 3.3 | Sửa mẫu ca | `admin` | Click ✏️ → đổi tên/giờ → Lưu | Cập nhật thành công | | |
| 3.4 | Vô hiệu hóa mẫu ca | `admin` | Toggle trạng thái → Inactive | Mẫu ca không xuất hiện khi tạo ca mới | | |

---

## 📋 MODULE 4: KỲ XẾP LỊCH

> **Truy cập:** Kỳ xếp lịch (Admin/Manager)

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 4.1 | Tạo kỳ xếp lịch | `admin` | Kỳ xếp lịch → Thêm → đặt tên, ngày bắt đầu/kết thúc → Lưu | Tạo thành công, status: DRAFT | | |
| 4.2 | Sửa kỳ xếp lịch | `admin` | Click ✏️ → đổi tên/ngày → Lưu | Cập nhật thành công | | |
| 4.3 | Publish lịch | `admin` | Chọn kỳ DRAFT → Publish → xem cảnh báo → Xác nhận | Status → PUBLISHED, NV nhận thông báo | | |
| 4.4 | Lock lịch | `admin` | Chọn kỳ PUBLISHED → Lock → Xác nhận | Status → LOCKED, không sửa được nữa | | |

---

## 📋 MODULE 5: LỊCH LÀM VIỆC

> **Truy cập:** Lịch làm việc (Admin/Manager)

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 5.1 | Tạo ca thủ công | `admin` | Lịch làm việc → Tạo ca → chọn kỳ, mẫu ca, ngày → Lưu | Ca được tạo, status: DRAFT | | |
| 5.2 | Generate ca tự động | `admin` | Chọn kỳ → Generate → chọn mẫu ca, ngày trong tuần → Xác nhận | Tự động tạo nhiều ca theo mẫu | | |
| 5.3 | Xem yêu cầu nhân lực ca | `admin` | Mở ca → xem Shift Requirements | Hiện số lượng NV cần theo vị trí | | |
| 5.4 | Xếp NV vào ca thủ công | `admin` | Mở ca → Phân công → chọn NV từ danh sách ứng viên → Assign | NV được gán vào ca | | |
| 5.5 | Xóa NV khỏi ca | `admin` | Mở ca → Phân công → Remove NV | NV bị xóa khỏi ca | | |
| 5.6 | Xếp lịch tự động (AI) | `admin` | Chọn kỳ → Auto Schedule → Xác nhận | Hệ thống tự phân công NV dựa trên availability | | |
| 5.7 | Hủy ca làm việc | `admin` | Chọn ca → Cancel → nhập lý do → Xác nhận | Status → CANCELLED | | |

---

## 📋 MODULE 6: LỊCH RẢNH

> **Truy cập:** Lịch rảnh (Employee/Admin)

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 6.1 | Đăng ký lịch rảnh | `emp01` | Lịch rảnh → Thêm → chọn ngày, loại (Available) → Lưu | Tạo thành công | | |
| 6.2 | Đăng ký không rảnh | `emp01` | Thêm → loại: Unavailable → ngày, lý do → Lưu | Tạo thành công, hệ thống tránh xếp ca ngày này | | |
| 6.3 | Sửa lịch rảnh | `emp01` | Click ✏️ → đổi ngày/loại → Lưu | Cập nhật thành công | | |
| 6.4 | Xóa lịch rảnh | `emp01` | Click 🗑️ → Xác nhận | Xóa thành công | | |

---

## 📋 MODULE 7: ĐƠN XIN NGHỈ

> **Truy cập:** Đơn xin nghỉ (tất cả) / Duyệt nghỉ phép (Admin/Manager)

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 7.1 | Tạo đơn xin nghỉ | `emp01` | Đơn xin nghỉ → Tạo → chọn loại nghỉ, ngày bắt đầu/kết thúc, lý do → Gửi | Status: PENDING | | |
| 7.2 | Hủy đơn đang chờ | `emp01` | Chọn đơn PENDING → Hủy | Status: CANCELLED | | |
| 7.3 | Duyệt chấp thuận | `manager01` | Duyệt nghỉ phép → chọn đơn → Approve | Status: APPROVED, NV nhận thông báo | | |
| 7.4 | Từ chối đơn | `manager01` | Duyệt nghỉ phép → chọn đơn → Reject | Status: REJECTED, NV nhận thông báo | | |
| 7.5 | Lọc theo trạng thái | `manager01` | Filter theo PENDING/APPROVED/REJECTED | Hiện đúng danh sách | | |

---

## 📋 MODULE 8: ĐỔI / NHƯỜNG CA

> **Truy cập:** Đổi/nhường ca (Employee) / Duyệt đổi ca (Admin/Manager)  
> ⚠️ *Cần có 2 NV (emp01, emp02) đã được xếp ca trong kỳ đã publish*

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 8.1 | Yêu cầu đổi ca (swap) | `emp01` | Đổi/nhường ca → Tạo → chọn ca của mình → chọn ca muốn đổi của emp02 → Gửi | Status: PENDING_RESPONSE | | |
| 8.2 | emp02 đồng ý đổi | `emp02` | Xem yêu cầu đến → Chấp nhận | Status → chờ manager duyệt | | |
| 8.3 | emp02 từ chối đổi | `emp02` | Xem yêu cầu đến → Từ chối + lý do | Status: REJECTED | | |
| 8.4 | Nhường ca (giveaway) | `emp01` | Tạo yêu cầu → chọn ca của mình → không chọn ca đổi → Gửi | Đăng lên dạng ca nhường | | |
| 8.5 | emp02 nhận ca nhường | `emp02` | Xem danh sách ca nhường → Nhận | Gửi yêu cầu chờ duyệt | | |
| 8.6 | Manager duyệt đổi ca | `manager01` | Duyệt đổi ca → Approve | Status: APPROVED, ca được hoán đổi | | |
| 8.7 | Manager từ chối đổi | `manager01` | Duyệt đổi ca → Reject + lý do | Status: REJECTED | | |
| 8.8 | Hủy yêu cầu đổi ca | `emp01` | Chọn yêu cầu PENDING → Hủy | Status: CANCELLED | | |

---

## 📋 MODULE 9: CA TRỐNG

> **Truy cập:** Ca trống (Employee) / Duyệt nhận ca (Admin/Manager)  
> ⚠️ *Cần có ca làm việc chưa đủ số lượng NV trong kỳ đã publish*

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 9.1 | Xem danh sách ca trống | `emp01` | Ca trống → xem danh sách | Hiện các ca chưa đủ nhân lực | | |
| 9.2 | Đăng ký ca trống | `emp01` | Chọn ca → Đăng ký → Xác nhận | Status: PENDING | | |
| 9.3 | Hủy đăng ký | `emp01` | Chọn đơn PENDING → Hủy | Status: CANCELLED | | |
| 9.4 | Duyệt nhận ca | `manager01` | Duyệt nhận ca → Approve | NV được thêm vào ca, nhận thông báo | | |
| 9.5 | Từ chối nhận ca | `manager01` | Duyệt nhận ca → Reject + lý do | Status: REJECTED | | |

---

## 📋 MODULE 10: CHẤM CÔNG

> **Truy cập:** Chấm công (Employee) / Quản lý chấm công (Admin/Manager)  
> ⚠️ *Cần emp01 đã có ca làm việc hôm nay*

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 10.1 | Check-in | `emp01` | Chấm công → Check-in → Xác nhận | Ghi nhận giờ vào, status: CHECKED_IN | | |
| 10.2 | Check-out | `emp01` | Chấm công → Check-out → Xác nhận | Ghi nhận giờ ra, tính số giờ làm | | |
| 10.3 | Xem lịch sử chấm công | `emp01` | Chấm công → xem danh sách | Hiện các lần check-in/out | | |
| 10.4 | Quản lý duyệt chấm công | `manager01` | Quản lý chấm công → chọn bản ghi → Approve | Status: APPROVED | | |
| 10.5 | Lọc theo ngày | `manager01` | Chọn khoảng ngày → filter | Hiện đúng dữ liệu khoảng thời gian | | |
| 10.6 | Chấm công thiết bị mới | `emp01` | Check-in từ thiết bị chưa duyệt | Thành công, hiện cảnh báo "Thiết bị chờ duyệt", cờ TB lạ | | |
| 10.7 | Giải trình khi đổi thiết bị | `emp01` | "Dùng máy khác? Giải trình" → nhập lý do → Chấm công | Lưu giải trình, thông báo quản lý kèm nội dung lý do | | |
| 10.8 | Quản lý duyệt thiết bị | `manager01` | Quản lý chấm công → Tab "Duyệt thiết bị" → Duyệt | Thiết bị: APPROVED, NV nhận thông báo đã duyệt | | |
| 10.9 | Chấm công thiết bị đã duyệt | `emp01` | Check-in từ thiết bị đã APPROVED | Thành công mượt mà, không còn cảnh báo thiết bị mới | | |
| 10.10 | Quản lý thu hồi thiết bị | `manager01` | Tab "Duyệt thiết bị" → Thu hồi | Thiết bị: REVOKED, NV nhận thông báo bị thu hồi | | |
| 10.11 | Chặn thiết bị bị thu hồi | `emp01` | Check-in từ thiết bị REVOKED | Bị chặn đứng, báo lỗi thiết bị bị thu hồi quyền | | |
| 10.12 | Nhân viên xem thiết bị | `emp01` | Chấm công → "Thiết bị của tôi" | Xem đầy đủ danh sách thiết bị và trạng thái duyệt | | |
| 10.13 | Chấm công đúng Wi-Fi chi nhánh | `emp01` | Check-in khi kết nối đúng Wi-Fi chi nhánh | Thành công, `unverifiedWifi = false` | | |
| 10.14 | Cảnh báo sai Wi-Fi chi nhánh | `emp01` | Check-in khi sai Wi-Fi/IP chi nhánh | Thành công, hiện cảnh báo và gắn cờ `Sai Wi-Fi` | | |


---

## 📋 MODULE 11: TÍNH LƯƠNG

> **Truy cập:** Tính công và lương (Admin/Manager) / Lương của tôi (Employee/Manager)  
> ⚠️ *Cần đã có chấm công được approve*

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 11.1 | Tính lương theo kỳ | `admin` | Tính công và lương → chọn kỳ xếp lịch → Calculate | Tạo bản ghi lương cho từng NV | | |
| 11.2 | Xem chi tiết lương | `admin` | Chọn NV → xem chi tiết | Hiện giờ làm, đơn giá, tổng lương | | |
| 11.3 | Thêm thưởng/phạt | `admin` | Chọn bản ghi → Bonus → nhập số tiền & ghi chú → Lưu | Cập nhật tổng lương | | |
| 11.4 | Xác nhận lương | `admin` | Chọn bản ghi → Confirm | Status: CONFIRMED | | |
| 11.5 | Xem lương cá nhân (NV) | `emp01` | Lương của tôi → xem danh sách | Hiện lịch sử lương các kỳ | | |
| 11.6 | Xem lương cá nhân (Manager) | `manager01` | Lương của tôi → xem | Hiện lương theo tháng | | |

---

## 📋 MODULE 12: DASHBOARD, THÔNG BÁO & LỊCH SỬ

| # | Kịch bản | Tài khoản | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Pass/Fail |
|---|----------|-----------|----------------|-----------------|-----------------|-----------|
| 12.1 | Dashboard Admin | `admin` | Vào trang chủ | Hiện metrics: tổng NV, ca hôm nay, chờ duyệt,... | | |
| 12.2 | Dashboard Manager | `manager01` | Vào trang chủ | Hiện thông tin chi nhánh | | |
| 12.3 | Dashboard Employee | `emp01` | Vào trang chủ | Hiện ca sắp tới, trạng thái cá nhân | | |
| 12.4 | Xem thông báo | `emp01` | Click chuông 🔔 hoặc vào Thông báo | Hiện danh sách thông báo | | |
| 12.5 | Lịch sử thay đổi lịch | `admin` | Lịch sử thay đổi → xem | Hiện audit log: ai thay đổi gì, lúc nào | | |
| 12.6 | Lịch của tôi | `emp01` | Lịch của tôi → xem calendar | Hiện ca làm việc theo dạng lịch | | |

---

## 🔄 LUỒNG TEST TỔNG HỢP (End-to-End)

```
[ADMIN] Tạo tài khoản manager01, emp01, emp02
    ↓
[ADMIN] Tạo mẫu ca (vd: Ca sáng 08:00–17:00, Ca chiều 13:00–22:00)
    ↓
[ADMIN] Tạo kỳ xếp lịch (vd: tuần hiện tại)
    ↓
[ADMIN] Generate ca làm việc từ mẫu ca
    ↓
[EMP01] Đăng ký lịch rảnh
    ↓
[ADMIN] Xếp emp01, emp02 vào các ca (thủ công hoặc Auto Schedule)
    ↓
[ADMIN] Publish kỳ xếp lịch
    ↓
[EMP01] Xem lịch của mình → thấy ca được xếp
    ↓
[EMP01] Check-in khi bắt đầu ca → Check-out khi kết thúc
    ↓
[MANAGER01] Duyệt chấm công của emp01
    ↓
[ADMIN] Tính lương theo kỳ
    ↓
[ADMIN] Xác nhận lương
    ↓
[EMP01] Xem lương của mình
```

---

## ⚠️ LƯU Ý QUAN TRỌNG

1. **Thứ tự bắt buộc:** Tạo NV → Mẫu ca → Kỳ xếp lịch → Ca làm việc → Publish → Chấm công → Tính lương
2. **Gán chi nhánh đúng:** NV chỉ thấy ca/thông tin của chi nhánh mình được gán
3. **Kỳ phải PUBLISHED** thì NV mới thấy lịch và mới có ca trống để đăng ký
4. **Đổi ca cần 2 NV** đều có ca trong kỳ đã publish
5. **Chấm công phải APPROVED** mới được tính vào lương
6. **Không tự khóa** tài khoản đang đăng nhập

---

*Tài liệu test: Smart Shift System — Tổng cộng ~60 kịch bản test*
