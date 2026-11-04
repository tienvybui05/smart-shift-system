# Ca gãy và Clopening

Quy tắc dùng chung cho danh sách ứng viên, phân công thủ công, xếp lịch ALNS–CP-SAT,
kiểm tra công bố lịch, nhận ca trống và chấp nhận/duyệt đổi hoặc nhường ca.

## Quy tắc

- **Ca gãy:** hai ca không trùng nhau, có cả giờ bắt đầu và kết thúc nằm trong cùng
  một ngày lịch theo múi giờ chi nhánh. Không bắt buộc khoảng nghỉ `minRestHours` giữa hai ca này.
- Ngoại lệ chỉ bỏ kiểm tra khoảng nghỉ giữa ca. Lịch rảnh, nghỉ phép, vị trí/chi nhánh,
  tổng giờ ngày/tuần và các điều kiện phân công khác vẫn được kiểm tra.
- **Clopening:** hai ca thuộc ngày khác nhau hoặc có ít nhất một ca qua đêm thì phải
  đủ `minRestHours` cấu hình của nhân viên (ví dụ 11 giờ); không cố định mọi nhân viên ở 11 giờ.
- Ca qua đêm không được coi là ca gãy chỉ vì kết thúc cùng ngày với ca tiếp theo,
  hoặc bắt đầu cùng ngày với ca trước. Ca kết thúc đúng 00:00 ngày sau cũng không được miễn.
- Giờ trùng luôn bị chặn. Hai ca ban ngày nối tiếp đúng mốc kết thúc/bắt đầu không bị coi là trùng.
- Dùng thời gian thực trôi qua giữa các `Instant`, không làm tròn giờ nghỉ. Đủ đúng ngưỡng
  được phép; thiếu dù 1 nanosecond bị chặn. Chuyển giờ mùa hè không làm thay đổi ngưỡng thực tế.
- Ngày/tuần vẫn theo múi giờ chi nhánh. Tổng giờ ca qua đêm vẫn được tính vào ngày/tuần
  bắt đầu ca theo quy ước hiện tại; nâng cấp này không chia giờ công qua nửa đêm.

Ví dụ với ngưỡng nghỉ 11 giờ và giới hạn làm 8 giờ/ngày:

| Ca trước | Ca sau | Kết quả |
|---|---|---|
| Thứ Hai 08:00–12:00 | Thứ Hai 15:00–19:00 | Cho phép, tổng 8 giờ |
| Thứ Hai 04:00–08:00 | Thứ Hai 15:00–19:00 | Cho phép theo ngày Việt Nam, dù ngày bắt đầu UTC khác nhau |
| Thứ Hai 08:00–12:00 | Thứ Hai 11:00–15:00 | Chặn trùng giờ |
| Thứ Hai 08:00–12:00 | Thứ Hai 15:00–20:00 | Chặn vì tổng 9 giờ/ngày |
| Thứ Hai 20:00–23:00 | Thứ Ba 05:00–09:00 | Chặn vì chỉ nghỉ 6 giờ |
| Thứ Hai 18:00–22:00 | Thứ Ba 09:00–13:00 | Cho phép, nghỉ đúng 11 giờ |
| Thứ Hai 22:00–Thứ Ba 06:00 | Thứ Ba 07:00–11:00 | Chặn vì chỉ nghỉ 1 giờ; không phải ca gãy |
| Thứ Hai 08:00–12:00 | Thứ Hai 22:00–Thứ Ba 06:00 | Chặn vì nghỉ 10 giờ; ca sau là ca đêm |

## Cài đặt

`ShiftRestPolicy` định nghĩa cùng một quy tắc cho validator và bộ giải. Validator cũng
được dùng ở bước chấp nhận/duyệt đổi ca và kiểm tra trước công bố. Khi duyệt, lịch hiện tại
được đánh giá lại: một yêu cầu đã được nhân viên đồng ý vẫn có thể bị chặn nếu phân công
khác vừa được duyệt làm vi phạm giờ/nghỉ. Các phân công bị thay thế khi đổi ca được loại
khỏi phép kiểm tra để tránh tự xung đột với dữ liệu cũ.

Hybrid áp dụng ngoại lệ cho cả ca mới và ứng viên cạnh phân công cố định, trong mô hình
đầy đủ lẫn mô hình sửa vùng ALNS. Các nhóm khoảng thời gian chỉ được chuyển thành
`AtMostOne` sau khi bảo đảm mọi cặp trong nhóm thực sự xung đột.

Không có migration DB hay sửa phân công cũ. Quy tắc mới có hiệu lực khi đánh giá hoặc
chạy phân công mới; các giới hạn giờ và khoảng nghỉ cấu hình cá nhân được giữ nguyên.

## Kết quả xác minh ngày 04/11/2026

- **114 unit test Java đạt**, gồm 13 test bổ sung và test đối chiếu mọi tập con của 60 bộ
  ca ngẫu nhiên; CP-SAT native chạy thật trong Linux Docker. Có ca gãy, ca đêm, ranh giới
  thời gian nghỉ, múi giờ, DST, giới hạn giờ, snapshot validator và sửa vùng ALNS.
- **12 test Python đạt** cho bộ audit/benchmark độc lập và ghép kết quả thực nghiệm.
- **18 kiểm tra API thật đạt** trên PostgreSQL QA riêng: ứng viên, xếp thủ công, Hybrid
  với/không có lịch cố định, công bố, đổi ca trực tiếp, nhường ca, chặn ca đêm và tái kiểm tra
  khi lịch thay đổi trước lúc quản lý duyệt.
- Một lượt kiểm tra quy mô: 300 nhân viên/28 ngày, 1 CPU, seed 42, ngân sách 8 giây/
  tối đa 20 vòng ALNS; phủ 3.780/3.780 vị trí, không vi phạm cứng qua audit độc lập,
  thời gian bộ giải 8.069 ms (làm tròn), 1 vòng ALNS, trạng thái FEASIBLE.
  Đây là một lượt kiểm tra khả năng xử lý quy mô, không chứng minh tối ưu hay tăng tốc.

Chạy lại unit test và API:

```powershell
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-split-unit:local smart-shift-be
docker compose -f compose.attendance-test.yml up -d --build
python qa-automation/scripts/split_shift_e2e.py
python -m unittest discover -s qa-automation/benchmarks -p test_benchmarks.py
```

API QA dùng http://127.0.0.1:28080 và DB `attendance_qa`. Script tạo bộ nhân viên/chi nhánh/
ca tương lai riêng mỗi lần, không xóa dữ liệu và từ chối DB khác tên. Kết quả và tài khoản
ở `qa-automation/results/split-shift.json`, mật khẩu QA `Demo@123456`; file này được Git ignore.

## Lưu ý đối với benchmark cũ

Các báo cáo v3 trước nâng cấp đã dùng quy tắc nghỉ giữa **mọi cặp ca**. Giữ nguyên các
báo cáo, snapshot và dữ liệu đó. Không ghép điểm trước/sau để kết luận thuật toán tốt hơn
khi tập nghiệm hợp lệ đã thay đổi. Runner mới ghi `restPolicy=same-local-day-daytime-split-v1`
và checksum `ShiftRestPolicy.java`; công cụ so sánh từ chối ghép hai run khác chính sách.
Bộ audit độc lập hiện vẫn chỉ hỗ trợ fixture UTC và lịch ban đầu rỗng như trước.
