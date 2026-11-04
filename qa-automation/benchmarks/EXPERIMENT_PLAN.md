# Kế hoạch thực nghiệm thuật toán xếp lịch

Mục tiêu: kiểm tra bằng số liệu liệu **ALNS + CP-SAT** có tạo lịch tốt hơn Greedy của đồ án trong cùng mô hình nghiệp vụ, và phần ALNS có mang lại lợi ích so với chỉ CP-SAT hay không. Chưa có kết luận rằng hybrid luôn tốt nhất.

Đã có [báo cáo pilot 04/11/2026](reports/2026-11-04-pilot/README.md): 36 lượt, bao gồm các trường hợp chưa tìm được lịch ở quy mô 300 người. Cần giải quyết giới hạn khởi tạo trước khi chạy ma trận thực nghiệm chính.

[Báo cáo v2](reports/2026-11-04-budget-v2/README.md) đã đo lại sau sửa ngân sách/dừng tối ưu, tổng 45 lượt bao gồm cấu hình 8 giây/20 vòng. Bài nhỏ nhanh hơn; bài 300 người vẫn chưa có nghiệm trong giới hạn thử, nên chưa chốt khả năng triển khai quy mô lớn.

[Báo cáo mới nhất v3](reports/2026-11-04-scale-v3/README.md) kiểm tra mô hình xung đột gọn hơn, chiến lược CP-SAT theo quy mô và vùng ALNS có giới hạn. Tham khảo kết quả theo từng bộ dữ liệu, gồm cả trường hợp Greedy vẫn tốt hơn.

## 1. Ba phương án đối chứng

| Mã trong CSV | Cách chạy | Ý nghĩa |
|---|---|---|
| `GREEDY_SHARED_CONSTRAINTS` | Tái dựng thứ tự và điểm ứng viên từ mã cũ trong Git; kiểm tra cùng ràng buộc cứng | Baseline tham lam, chỉ dùng trong thực nghiệm |
| `CP_SAT` | Cùng mô hình/hàm mục tiêu, `maxIterations=0`, dùng toàn bộ thời gian còn lại cho CP-SAT | Đo tác dụng của mô hình tối ưu khi không có ALNS bên ngoài |
| `ALNS_CP_SAT` | CP-SAT khởi tạo, ba toán tử phá và CP-SAT sửa; nhận lịch không kém lịch hiện tại | Phương án hybrid cần đánh giá |

Greedy tham chiếu: commit `0d9bf086e1b66dcc5b353ac8cdc1f9b6781f8234`, file `smart-shift-be/src/main/java/com/smartshift/service/impl/AutoScheduleServiceImpl.java`. Bản chụp nguyên gốc và SHA-256 được lưu tại `reference/AutoScheduleServiceImpl.greedy.java.txt` và `data/manifest.json`.

Baseline giữ điểm AVAILABLE=60, PREFERRED=100, bù thiếu giờ tối thiểu, dung lượng tuần còn lại và thứ tự ưu tiên → khan hiếm ứng viên → thời gian ca. Tên nhân viên dùng nhãn gốc NRP hoặc nhãn ổn định từ ID. Trường hợp hòa hoàn toàn ở mức nhu cầu dùng thứ tự chỉ số đầu vào thay cho tên vị trí của dịch vụ cũ. Tất cả phương án áp dụng cùng giới hạn giờ/nghỉ/ngày liên tiếp. Vì vậy đây là **bản đối chứng tái dựng có kiểm soát**, không phải thực thi nguyên trạng dịch vụ Greedy cũ qua DB.

CP-SAT vẫn có cơ chế tìm kiếm nội bộ của OR-Tools; tên `CP_SAT` chỉ biểu thị bỏ vòng ALNS do đồ án tự xây dựng.

Từ phiên bản v3, hai bộ giải cùng dùng mô hình clique cho xung đột ca và cùng lựa chọn
chiến lược CP-SAT theo kích thước bài toán: dưới 5.000 biến lựa chọn tự do dùng mặc định,
từ 5.000 dùng rẽ nhánh ưu tiên phủ ca, tắt LP và một vòng presolve. ALNS hạn chế vùng
sửa ở tối đa 800 lựa chọn và 20% đầu vào. Do đó so sánh v2/v3 là so sánh toàn bộ gói
cải tiến; không thể quy hết lợi ích cho một thành phần riêng lẻ.

## 2. Dữ liệu và nguồn

Nguồn công khai: [Scheduling Benchmarks — Nurse Rostering](https://www.schedulingbenchmarks.org/nrp/index.html). Định dạng: [Instances 1–24](https://www.schedulingbenchmarks.org/nrp/instances1_24.html). Dữ liệu là TXT, ví dụ [Instance 1](https://www.schedulingbenchmarks.org/nrp/data/Instance1.txt). Script tải file gốc, giữ bản cache và SHA-256, rồi tạo bộ đầu vào có nhãn `smartshift-projection-v1`.

| Instance | Nhân viên | Ngày | Loại ca gốc |
|---|---:|---:|---:|
| 1 | 8 | 14 | 1 |
| 2 | 14 | 14 | 2 |
| 3 | 20 | 14 | 3 |
| 8 | 30 | 28 | 4 |
| 11 | 50 | 28 | 6 |
| 13 | 120 | 28 | 18 |

**Đây là dữ liệu công khai được chuyển sang bài toán Smart Shift. Không so điểm với best-known của NRP gốc**, vì chưa hỗ trợ toàn bộ ràng buộc và hàm mục tiêu gốc.

| Nội dung gốc | Cách xử lý trong bản chuyển đổi |
|---|---|
| Nhân viên, ngày, độ dài ca, nhu cầu nhân sự | Giữ thông tin để tạo worker/demand |
| Ngày nghỉ bắt buộc, loại ca có quota bằng 0 | Loại lựa chọn phân công tương ứng |
| Giới hạn tổng phút trên cả kỳ | Chuyển thành mức giờ tuần trung bình; đây là thay đổi ràng buộc |
| Giới hạn số ngày làm liên tiếp tối đa | Áp dụng trong solver |
| Yêu cầu được làm ca có trọng số dương | Chuyển thành cờ PREFERRED; bỏ trọng số gốc |
| Giờ bắt đầu ca | Nguồn không có giờ đồng hồ; sinh giờ giả định theo loại ca |
| Khoảng nghỉ | Bổ sung 11 giờ theo giả định của phép thử |
| Lương, ngân sách | Không có trong nguồn: chi phí bằng 0, ngân sách tắt |
| Cặp loại ca cấm nối tiếp, quota dương theo loại ca, số cuối tuần tối đa | Chưa mô hình hóa |
| Số ngày làm/nghỉ liên tiếp tối thiểu, yêu cầu tránh ca | Chưa mô hình hóa |
| Một ca/ngày, trọng số thiếu/thừa người gốc | Không giữ nguyên; dùng quy tắc giờ/nghỉ và mục tiêu Smart Shift |

Metadata của từng file nhắc lại các giới hạn này để tránh dùng nhầm điểm số.

Thêm **9 bộ dữ liệu mô phỏng**, tổ hợp 30/100 nhân viên × 14 ngày và 300 nhân viên × 28 ngày, mỗi quy mô có ba kịch bản `balanced`, `shortage`, `tight`. Seed sinh dữ liệu mặc định 42; tên kịch bản là tham số sinh, không phải chứng nhận có thể phủ 100% nhu cầu.

Mô phỏng có ba vị trí, toàn thời gian/bán thời gian, ca đêm, nghỉ giữa ca 30 phút, giới hạn giờ tuần/ngày/ngày liên tiếp, lựa chọn giờ rảnh, sở thích, đơn giá và ngân sách. `shortage` tăng nhu cầu; `tight` giảm lựa chọn khả dụng. Lựa chọn nhân viên–nhu cầu được xem là đã qua bước lọc vị trí/giờ rảnh của ứng dụng.

Các fixture benchmark bắt đầu với lịch trống, múi giờ UTC, không gọi DB/API. Các tình huống giữ lịch có sẵn, nghỉ phép, tranh chấp khi lưu và ranh giới tuần của ca đêm tiếp tục được kiểm tra bằng unit/E2E và `HYBRID_MANUAL_TEST_SCENARIOS.md` ở thư mục gốc.

## 3. Chất lượng lịch được đo bằng gì?

So sánh theo thứ tự từ trái sang phải, giá trị thấp hơn tốt hơn:

`(tổng vị trí thiếu, tổng thiếu có trọng số ưu tiên, soft penalty)`

Một lịch vi phạm ràng buộc cứng bị loại khỏi so sánh chất lượng và được ghi lỗi. Không coi lỗi/timeout là lời giải rỗng hợp lệ.

- `missing`, `coveragePercent`: số vị trí còn thiếu và tỷ lệ phủ nhu cầu.
- `priorityMissing`: thiếu nhân sự nhân với `(mức ưu tiên lớn nhất − ưu tiên nhu cầu + 1)`; số ưu tiên nhỏ hơn quan trọng hơn.
- `preferredPercent`: tỷ lệ phân công vào thời gian nhân viên thích trong các phân công đã chọn.
- `costVnd`: chi phí tổng, dùng đơn vị nghìn đồng được làm tròn giống đầu vào bộ giải. Nên so chi phí khi mức phủ tương đương.
- `minimumDeficitHours`, `overtimeHours`: thiếu mức giờ tối thiểu tuần và vượt mức chuẩn dùng trong hàm mục tiêu. Đây là chỉ số mô hình, không phải kết luận tính lương theo pháp luật.
- `fairnessUtilizationStd`: trung bình độ lệch chuẩn tổng thể của tỷ lệ `giờ được xếp / giờ tối đa`, trong từng nhóm vị trí/hợp đồng và tuần; có tính nhân viên được xếp 0 giờ. Thấp hơn biểu thị mức tải đồng đều hơn theo chỉ số này.
- `softPenalty`: 400 × phân công không PREFERRED + chi phí nghìn đồng + 2 × thiếu giờ tối thiểu + 2 × giờ vượt chuẩn + lệch giờ so với mục tiêu nhóm + 10 × vượt ngân sách nghìn đồng. Các thành phần giờ dùng đơn vị 0,01 giờ. Chỉ tính các cặp nhân viên/tuần có lựa chọn ứng viên, theo mô hình Java.
- `solverWallMillis`: dựng mô hình và tìm kiếm, bỏ thời gian nạp thư viện native. `processWallMillis`: toàn bộ JVM hoặc lệnh Docker, gồm khởi động và nạp native. Hai chỉ số được giữ riêng.
- `hardViolations`, `status`, `error`, `iterations`: tính hợp lệ, tình trạng bộ giải và số vòng sửa.

`evaluate.py` kiểm tra độc lập chỉ số lựa chọn, số người tối đa, trùng ca, chồng thời gian, nghỉ giữa ca, giờ ngày/tuần và ngày làm liên tiếp. Bộ kiểm tra này kiểm tra **mô hình chuyển đổi**, không chứng nhận lịch thỏa tất cả ràng buộc NRP gốc.

`internalObjective` trong kết quả Java là điểm mã hóa nhiều tầng, phụ thuộc cận theo đầu vào và phiên bản mô hình; không so trực tiếp giữa những bộ dữ liệu hoặc phiên bản khác nhau. V3 siết cận chi phí theo sức chứa từng nhu cầu; dùng bộ ba chỉ số tính lại độc lập để so trước/sau. Chữ FEASIBLE không chứng nhận tối ưu toàn cục; OPTIMAL của lần giải mô hình đầy đủ mới có ý nghĩa đó.

## 4. Thiết kế so sánh công bằng

1. Cùng file JSON, ràng buộc và hàm mục tiêu cho ba thuật toán; không cho hybrid thêm lịch tốt tạo trước.
2. CP-SAT một luồng. Docker giới hạn 1 CPU; toàn bộ job chạy tuần tự. Không chạy benchmark song song với tác vụ nặng.
3. Mỗi lần giải dùng JVM mới. Thứ tự job được xáo bằng seed cố định, lưu cấu hình, mã nguồn/input SHA và image ID để truy lại.
4. Hai bộ giải chạy seed 0–9 cho thực nghiệm chính. Greedy không dùng seed nên chạy một lần cho mỗi dữ liệu/ngân sách, không nhân bản thành 10 mẫu độc lập.
5. Giai đoạn thử nhanh: 3 bộ nhỏ, ngân sách 2 giây, seed 0/1/2. Mục đích kiểm tra pipeline, chưa dùng để kết luận nghiên cứu.
6. Thực nghiệm chính: 15 bộ, ngân sách 5 và 15 giây, 10 seed. Tổng 630 job, khoảng 100 phút nếu hai bộ giải dùng hết ngân sách, cộng khởi động và dựng mô hình. Có thể chia theo quy mô bằng `--datasets`.
7. Hybrid benchmark mặc định giới hạn 1000 vòng để thời gian là giới hạn chính; cấu hình ứng dụng hiện là 20 vòng. Có thể đặt `--hybrid-iterations 20`; giá trị thực được lưu trong `run.json`. Khi CP-SAT khởi tạo đã chứng minh OPTIMAL, dừng ngay với 0 vòng. Cần nêu rõ cấu hình từng đợt khi so sánh.
8. Ngân sách tìm kiếm không phải timeout tuyệt đối; dựng mô hình và xử lý native có thể làm thời gian vượt mức. Ghi thời gian thực tế, không bỏ các lần vượt hoặc thất bại. Timeout tiến trình mặc định ngân sách + 180 giây.

Xuất `runs.csv`, `summary.csv` (trung bình và độ lệch chuẩn mẫu), `comparisons.csv` (thắng/hòa/thua theo bộ ba tiêu chí với các seed tương ứng). Một baseline Greedy có thể được dùng đối chiếu nhiều seed; đó không phải các lần đo Greedy độc lập. Các lỗi được đếm riêng và không trộn vào trung bình chất lượng. Nếu có lỗi, phân tích tỷ lệ thành công cùng chất lượng để tránh thiên lệch do chỉ nhìn các lần thành công.

Sau thực nghiệm chính, báo cáo theo từng quy mô và loại dữ liệu, xem cả trường hợp hybrid thua. Muốn khẳng định ý nghĩa thống kê cần chọn phép kiểm định trên các instance độc lập phù hợp; không coi seed cùng một instance là nhiều bài toán độc lập. Nếu cần so sánh với công bố/best-known NRP, phải xây mô hình đầy đủ NRP riêng, đó chưa phải phạm vi của bộ chuyển đổi này.

## 5. Cách chạy từ thư mục gốc repository

Chuẩn bị dữ liệu và kiểm tra script (Python 3.10+):

```powershell
python qa-automation/benchmarks/prepare_data.py
python -m unittest discover -s qa-automation/benchmarks -p test_benchmarks.py -v
```

Docker Linux được khuyến nghị trên máy này vì Windows Application Control chặn DLL OR-Tools trong thư mục tạm. Không cần khởi động backend/frontend/PostgreSQL để chạy benchmark.

```powershell
python qa-automation/benchmarks/run_benchmarks.py --docker --build
```

Lần sau đã có image:

```powershell
python qa-automation/benchmarks/run_benchmarks.py --docker
```

Thực nghiệm chính (có thể chạy lâu):

```powershell
python qa-automation/benchmarks/run_benchmarks.py --docker --datasets nrp,synthetic --seconds 5,15 --seeds 0,1,2,3,4,5,6,7,8,9
```

Chỉ thử quy mô lớn:

```powershell
python qa-automation/benchmarks/run_benchmarks.py --docker --datasets nrp13,synthetic_300 --seconds 15 --seeds 0,1,2
```

Kiểm tra đúng cấu hình mặc định của bộ tối ưu ứng dụng (8 giây, tối đa 20 vòng, seed 42):

```powershell
python qa-automation/benchmarks/run_benchmarks.py --docker --datasets nrp03,synthetic_30_14_shortage,synthetic_300_28_balanced --seconds 8 --seeds 42 --hybrid-iterations 20
```

Đây vẫn là phép đo trên snapshot chuẩn hóa, không thay cho kiểm thử API/DB. Sau khi sửa Java CLI hoặc thuật toán, cần build lại image bằng `--build` hoặc `docker build` trước khi chạy. Tham số `--hybrid-iterations` yêu cầu image chứa CLI mới, không dùng với image pilot cũ.

Nếu chạy Java trực tiếp, cần JDK 17+ và môi trường cho phép nạp native OR-Tools. Đặt `JAVA_HOME`, bỏ `--docker` và giữ `--build` ở lần đầu. Script tự tìm JDK ở PATH hoặc `.jdks` nếu cần. Không chỉnh/tắt chính sách bảo mật Windows để chạy benchmark.

Kết quả mỗi đợt nằm trong `qa-automation/benchmarks/results/<timestamp>/`, có JSON lời giải, audit, log, CSV, `REPORT.md` và `run.json`. Dữ liệu tải về và kết quả được Git ignore; manifest, mã nguồn, tài liệu và bản Greedy tham chiếu được giữ để có thể tái lập.

Vẽ biểu đồ bằng Matplotlib trong môi trường riêng:

```powershell
python -m venv qa-automation/benchmarks/.venv
qa-automation/benchmarks/.venv/Scripts/python.exe -m pip install -r qa-automation/benchmarks/requirements-plots.txt
qa-automation/benchmarks/.venv/Scripts/python.exe qa-automation/benchmarks/plot_results.py qa-automation/benchmarks/results/<timestamp>
```

Xuất `comparison.svg` và `comparison.png`, có trung bình và thanh độ lệch chuẩn. `requirements-plots.txt` cho phép các bản Matplotlib tương thích; để tái lập môi trường vẽ đã chạy, lưu thêm `pip freeze` cùng kết quả.

## 6. Điều kiện để chốt thuật toán cho đồ án

- Không có vi phạm cứng trên các lần được báo thành công; thất bại được trình bày đầy đủ.
- Chất lượng tốt hơn baseline trên phần lớn dữ liệu đại diện, với thời gian chờ chấp nhận được.
- Chứng minh lợi ích của ALNS so với CP-SAT đơn thuần ở nhóm dữ liệu cụ thể; nếu chưa thấy lợi ích, báo cáo đúng kết quả và nghiên cứu lại vùng lân cận/phân bổ thời gian.
- Chạy lại cấu hình ứng dụng và E2E trước khi khẳng định đã phù hợp triển khai. Dữ liệu mô phỏng 300 người không thay cho kiểm thử tải nhiều người dùng và DB.
