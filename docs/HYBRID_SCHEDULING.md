# Thuật toán xếp lịch ALNS–CP-SAT

## Phạm vi hiện thực

Endpoint giữ nguyên: `POST /api/schedules/generate`, body `{"schedulePeriodId": 1}`.
Luồng Greedy đã được thay thế, không có lựa chọn hoặc fallback sang Greedy.
Chỉ bổ sung số người còn thiếu đến nhu cầu tối thiểu trong kỳ DRAFT.
Tất cả phân công đã lưu (kể cả AUTO từ lần chạy trước) được giữ cố định.
Do đó, chạy lại một kỳ đã đủ người không tự hoán đổi lịch cũ.
Nguồn các phân công mới vẫn là AUTO; điểm cá nhân `score` là null vì quyết định được tối ưu ở cấp toàn lịch.

## Luồng xử lý

1. Kiểm tra quyền chi nhánh tại controller, khóa kỳ lịch và nhân viên bằng transaction hiện tại.
2. Tải phân công có sẵn, lịch rảnh và nghỉ phép thành snapshot của lần chạy.
   Bao gồm lịch ở tuần lân cận, lịch ngoài kỳ và lịch cũ tại chi nhánh khác của nhân viên.
3. Loại những cặp nhân viên–ca vi phạm ràng buộc với dữ liệu cố định.
4. CP-SAT tạo phương án cho tất cả phân công mới đồng thời, không ghi thử vào database.
   Nếu đã chứng minh OPTIMAL trên toàn mô hình, trả ngay với 0 vòng ALNS.
5. Khi chưa chứng minh tối ưu, ALNS chọn vùng phân công và CP-SAT giải lại vùng đó.
   Các vùng còn lại được cố định.
6. Giữ nghiệm có điểm phạt thấp nhất; có thể nhận nghiệm bằng điểm để đa dạng hóa.
7. Kiểm tra lại từng phân công trước khi lưu, cập nhật trạng thái ca và ghi audit.
   Lỗi xác thực hoặc lưu dữ liệu làm rollback transaction.

Snapshot tránh truy vấn database trong vòng lặp tìm kiếm.
Việc kiểm tra lần cuối trước khi lưu vẫn dùng bộ ràng buộc chung.

## Mô hình CP-SAT

Mỗi cặp nhân viên–nhu cầu hợp lệ có biến nhị phân x.
Mỗi nhu cầu có biến thiếu người u không âm:

sum(x của nhu cầu) + u = số người còn thiếu so với minimum.

Các điều kiện bắt buộc:
- Nhân viên hoạt động, đúng chi nhánh và vị trí đang hoạt động.
- AVAILABLE/PREFERRED bao phủ toàn ca; không giao UNAVAILABLE hoặc nghỉ đã duyệt.
- Không phân công trùng ca; không đủ giờ nghỉ giữa các ca.
- Tổng giờ ngày/tuần tính cả công việc cố định không vượt giới hạn.
- Số ngày làm liên tiếp tính cả lịch trước/sau kỳ, qua ranh giới tuần.
- Không thêm người vượt mức thiếu tới minimum, bảo toàn phân công đã có.
- Không tạo lại cặp nhân viên–ca đã tồn tại ở bất kỳ trạng thái nào (unique DB).
- Không xếp ca đã bắt đầu.

Ca có giờ bắt đầu/kết thúc cố định. Mô hình quét các khoảng kéo dài thêm thời gian nghỉ
để tìm nhóm ứng viên xung đột. Ngoại lệ ca gãy có thể làm nhóm này không còn là clique:
hai ca ban ngày cùng ngày không trùng giờ được phép cùng chọn. Vì vậy mỗi nhóm được
tách thành các clique thực sự, phủ mọi cạnh xung đột, rồi mới thêm `AtMostOne`.
Không áp một `AtMostOne` lên nhóm có cặp ca gãy hợp lệ. Các mốc dùng `Instant`, giữ
độ chính xác thời gian đầu vào; đủ thời gian nghỉ chính xác thì hợp lệ, thiếu 1 nanosecond
thì không hợp lệ. Test đối chiếu mọi tập con trên 60 bộ ca ngẫu nhiên ở UTC và múi giờ Việt Nam.

Quy ước tương thích hệ thống:
- Thời điểm dùng Instant; ngày và tuần theo timezone chi nhánh, tuần bắt đầu thứ Hai.
- Giờ công mỗi ca = (phút ca - phút nghỉ) / 60, làm tròn 2 chữ số.
  Solver dùng số nguyên bằng giờ công nhân 100, tương thích validator hiện tại.
- Toàn bộ giờ của ca qua đêm tính vào ngày/tuần bắt đầu ca; chưa chia ca qua nửa đêm.
- `minRestHours` áp dụng giữa các ca khác ngày và mọi cặp có ca qua đêm.
  Hai ca nằm trọn trong cùng ngày địa phương được miễn kiểm tra thời gian nghỉ giữa ca,
  nhưng vẫn phải không trùng giờ và không vượt giờ ngày/tuần. Ca kết thúc đúng nửa đêm
  không được hưởng ngoại lệ. Xem [chính sách và kiểm thử ca gãy](SPLIT_SHIFT_POLICY.md).

## Hàm mục tiêu và thang điểm

Ưu tiên theo thứ tự:
1. Giảm tổng số vị trí thiếu người.
2. Khi tổng thiếu bằng nhau, ưu tiên nhu cầu có số priority nhỏ hơn.
3. Khi hai mức trên bằng nhau, giảm tổng các phạt mềm.

Phạt mềm hiện tại:
- Mỗi phân công AVAILABLE thay vì PREFERRED: 400.
- Thiếu định mức tuần: 2 × phần giờ còn thiếu (đơn vị 0.01 giờ).
- Vượt giờ chuẩn tuần: 2 × phần vượt. Giờ chuẩn tạm lấy minHoursPerWeek nếu > 0,
  nếu không thì min(40 giờ, maxHoursPerWeek). Đây là quy ước tối ưu, không phải công thức lương.
- Công bằng: độ lệch tuyệt đối so với mục tiêu phân bổ theo tỷ lệ giới hạn giờ tuần,
  trong nhóm cùng vị trí và employmentType.
- Chi phí nhân công dự kiến: 1 × chi phí, đơn vị nghìn đồng.
- Vượt ngân sách tuần: 10 × phần vượt, đơn vị nghìn đồng.

Chi phí mỗi ca tính từ phút thực tế của ca, basePayAmount và salaryCoefficient,
làm tròn đến nghìn đồng cho mô hình tối ưu. Payroll vẫn giữ cách tính tiền hiện tại.
Ngân sách là mục tiêu mềm: cho phép vượt để không đánh đổi việc đủ người.

Các biến thiếu định mức, vượt chuẩn và vượt ngân sách được ràng buộc bằng giá trị
`max(0, biểu thức)` chính xác. Chỉ dùng bất đẳng thức cận dưới có thể để biến phạt còn dư
ở một nghiệm FEASIBLE, khiến điểm dùng nhận/từ chối incumbent khác điểm tính từ lịch.
Test hồi quy yêu cầu không thể tạo nghiệm hợp lệ với ba loại phạt bị tăng giả.

Hệ số độ phủ và priority được tính từ cận trên các phạt khác để bảo đảm
không hy sinh một vị trí nhân sự chỉ để giảm chi phí hoặc tăng nguyện vọng.
Mô hình kiểm tra overflow và giới hạn độ lớn điểm trước khi giải.
Cận chi phí/phạt lựa chọn theo mỗi nhu cầu chỉ cộng các giá trị lớn nhất trong số
người tối đa có thể xếp, thay vì cộng mọi ứng viên. Cận ngân sách tuần dùng cùng nguyên tắc.
Cận mới chặt hơn nhưng giữ nguyên thứ tự tối ưu: thiếu người → ưu tiên → phạt mềm.
Giá trị `objective` mã hóa có thể thay đổi giữa các phiên bản mô hình;
so sánh trước/sau bằng các thành phần được tính lại độc lập trong CSV.
Thang điểm này là thiết kế ban đầu cần hiệu chỉnh qua thực nghiệm, không phải trọng số đã được chứng minh tốt nhất.

## Cơ chế ALNS

Ba toán tử gỡ vùng:
- Random ruin: chọn các lựa chọn nhân viên–nhu cầu quanh một ca làm mốc.
- Shortage ruin: ưu tiên một nhu cầu còn thiếu, các ứng viên của nó và phân công đang cạnh tranh.
- Employee ruin: lấy nhóm ứng viên của ca làm mốc, ưu tiên cả nhân viên thiếu và dư định mức giờ tuần.

Mỗi vùng có tối đa `min(800, ceil(20% số lựa chọn))` lựa chọn và nằm trong ±3 ngày
quanh mốc. Hai toán tử theo nhân viên chọn tối đa 12 nhân viên. Đây là tham số ban đầu
đã chọn qua phép đo chẩn đoán, chưa phải cấu hình đã được chứng minh tốt nhất.

CP-SAT là toán tử repair duy nhất, đồng thời đóng vai trò constructor.
Giữ cố định các phân công ngoài vùng và đưa incumbent làm solution hint cho các biến trong vùng.
Mỗi vòng dựng mô hình hạn chế: lựa chọn ngoài vùng bằng 0 không tạo biến, các phân công
khóa dùng giá trị cố định; phần phạt của nhân viên/tuần không đổi được tính thành hằng số.
Vẫn dùng cùng toàn bộ ràng buộc và cùng cận tính điểm của bài gốc, bảo đảm so sánh incumbent đúng.
Tổng nhu cầu/giới hạn giờ theo nhóm và tuần được gom trước để tránh quét toàn bộ ứng viên cho từng nhân viên/tuần.
Chọn toán tử bằng roulette wheel, ban đầu trọng số 1.
Cập nhật trọng số: 0.8 × trọng số cũ + 0.2 × reward.
Reward: cải thiện = 5, bằng điểm = 1, không cải thiện/không tìm được = 0.2.
Chấp nhận cải thiện hoặc bằng điểm; không nhận nghiệm kém hơn.

## Cấu hình

- SCHEDULING_TIME_LIMIT_SECONDS: mặc định 8, khoảng cho phép 0.1–60.
- SCHEDULING_MAX_ITERATIONS: mặc định 20, 0–1000. Giá trị 0 chỉ chạy constructor CP-SAT để đối chứng.
- SCHEDULING_RANDOM_SEED: mặc định 42, không âm.
- Một worker CP-SAT mỗi lần giải. Cùng seed không bảo đảm kết quả giống hệt khi dừng theo wall-clock.

Khi có ít hơn 5.000 biến lựa chọn tự do, dùng tìm kiếm/LP mặc định của CP-SAT.
Từ 5.000 biến, dùng `PARTIAL_FIXED_SEARCH`, `linearization_level=0` và một vòng presolve.
Thứ tự rẽ nhánh ưu tiên nhu cầu quan trọng/khan hiếm, thời gian ca và lựa chọn PREFERRED;
xoay thứ tự nhân viên theo ngày để tránh dồn lịch lên một nhóm. CP-SAT vẫn lan truyền,
quay lui và tối ưu toàn mô hình; không gọi lại bộ Greedy cũ. Các vùng sửa nhỏ dùng tìm kiếm mặc định.
Ngưỡng 5.000 là heuristic thực nghiệm, cần tiếp tục đo trên các đầu vào khác nhau.

Mốc 45% thời gian còn lại sau dựng mô hình là thời điểm chuyển sang ALNS **nếu đã có nghiệm**.
Constructor được phép dùng toàn bộ ngân sách còn lại khi chưa tìm được nghiệm;
giữ nguyên lần giải CP-SAT đang chạy, không chạy lại bước presolve từ đầu.
Nếu nghiệm đầu tiên xuất hiện sau mốc 45%, chuyển phần thời gian còn lại cho ALNS.
Nếu đã có nghiệm sớm nhưng chưa có cải thiện tiếp theo, bộ hẹn giờ vẫn yêu cầu chuyển pha ở mốc đó.
Callback và bộ hẹn giờ dùng cơ chế dừng bất đồng bộ được
[OR-Tools Java hỗ trợ](https://or-tools.github.io/docs/javadoc/com/google/ortools/sat/CpSolver.html#stopSearch()).
Khi constructor chứng minh OPTIMAL trên toàn mô hình, kết thúc ngay; `iterations=0`
và trọng số toán tử vẫn là `[1,1,1]`. OPTIMAL của một vùng sửa không được dùng để dừng toàn cục.
Mỗi vòng repair tối đa 0.6 giây và dừng theo thời gian còn lại.
Giới hạn này dành cho quá trình tìm kiếm; tải native lần đầu, đọc DB, dựng mô hình,
xác thực và lưu có thể khiến tổng request dài hơn. Frontend/proxy cho phép chờ 120 giây.
ALNS chạy đồng bộ và giữ các khóa transaction trong request; chưa có queue/background job.

OR-Tools 9.15.6755 dùng native library. Docker backend dùng Ubuntu/glibc thay Alpine/musl.
Không tự động fallback nếu thiếu native library hoặc chưa tìm được nghiệm trong thời gian cho phép.

Để chẩn đoán offline, bật JVM property `-Dsmartshift.scheduling.profile=true`.
Log thêm thời gian dựng mô hình, tiến trình CP-SAT, kích thước vùng sửa và reward từng vòng.
Mặc định tắt; không đưa chi tiết log này vào response giao diện. Log có overhead nên không
so trực tiếp thời gian profile với benchmark mặc định không bật log.

## Kết quả và cách diễn giải

Response có thêm `optimization`:
- algorithm: ALNS_CP_SAT.
- status: OPTIMAL, FEASIBLE hoặc NO_CANDIDATES.
- objective: điểm phạt nguyên cần giảm; chỉ so sánh cùng snapshot và mô hình.
- iterations, elapsedMillis, seed, operatorWeights.

OPTIMAL chỉ được báo khi constructor trên toàn mô hình đã chứng minh tối ưu.
OPTIMAL của một vùng repair không được dùng để tuyên bố tối ưu toàn lịch.
Chứng nhận luôn có điều kiện theo snapshot, các phân công cố định và quy ước mô hình.
NO_CANDIDATES không phải chứng nhận toàn lịch tối ưu.
qualityScore cũ vẫn là 80% độ phủ + 20% tỷ lệ PREFERRED của phân công mới,
không phải objective hay optimality gap. Giao diện ghi rõ sự khác biệt này.

## Kiểm thử

- Test solver gọi OR-Tools native thực, không mock lời giải.
- Kiểm tra: ứng viên khan hiếm, overlap/ca qua đêm, giờ nghỉ, giờ ngày/tuần,
  công việc cố định, ngày liên tiếp qua tuần, priority, ngân sách, nguyện vọng,
  công bằng và ALNS không làm nghiệm incumbent xấu đi.
- Test snapshot so sánh với bộ validator dùng chung và kiểm tra không query theo từng ứng viên.
- Test luồng service: bảo toàn phân công, idempotency, ca đã bắt đầu, thiếu ứng viên,
  và từ chối lưu khi xác thực cuối thất bại.

Chạy unit test không cần PostgreSQL:
`./mvnw -B '-Dtest=*Test' test`

Test `SmartShiftBeApplicationTests` và demo end-to-end cần PostgreSQL.
Bộ thực nghiệm độc lập nằm tại [qa-automation/benchmarks](../qa-automation/benchmarks/EXPERIMENT_PLAN.md):
6 bộ NRP được chuyển đổi rõ giả định, 9 bộ mô phỏng, baseline Greedy tái dựng, CP-SAT và hybrid.
Benchmark không đưa Greedy trở lại runtime. Chế độ `max-iterations=0` dành toàn bộ thời gian
tìm kiếm còn lại cho CP-SAT để so sánh với hybrid công bằng hơn.
Các fixture kiểm thử tính đúng đắn và lần benchmark thử nhanh không thay cho thực nghiệm chính;
chưa có kết luận rằng hybrid luôn tốt nhất hoặc bảo đảm tối ưu trong thời gian giới hạn.

[Pilot 04/11/2026](../qa-automation/benchmarks/reports/2026-11-04-pilot/README.md) ghi nhận
CP-SAT và hybrid chưa trả được lịch trên fixture 300 người/28 ngày trong ngân sách 15 giây
với cấu hình thử một CPU. Cần cải thiện bước tạo nghiệm khả thi và kiểm tra lại cấu hình
triển khai trước khi tuyên bố đáp ứng quy mô lớn.

[Báo cáo v2](../qa-automation/benchmarks/reports/2026-11-04-budget-v2/README.md) kiểm tra lại
sau sửa ngân sách khởi tạo và dừng sớm: NRP 3 ở ngân sách 15 giây giảm còn khoảng
0,69 giây và giữ chất lượng; bài 300 người vẫn chưa có nghiệm. Cấu hình ứng dụng
8 giây/20 vòng/seed 42 cũng đã được đo trên snapshot, chưa thay cho kiểm thử API/DB.

Phiên bản hiện tại dùng mô hình/vùng sửa v3, xem [báo cáo v3](../qa-automation/benchmarks/reports/2026-11-04-scale-v3/README.md)
cho số liệu mới nhất. Các kết quả pilot/v2 bên trên là lịch sử đối chứng, không phải kết quả của mã hiện tại.
