# Nâng cấp mô hình và vùng ALNS cho quy mô lớn — v3

Đã tiếp tục ALNS–CP-SAT và chạy **54 lượt trên bản cuối: tất cả trả lịch hợp lệ, không phát hiện vi phạm cứng qua bộ kiểm tra độc lập**. Trong đó 45 lượt dùng cùng dữ liệu/cấu hình để so với [v2](../2026-11-04-budget-v2/README.md), 9 lượt kiểm tra thêm các bộ lớn khác. Không đưa Greedy trở lại luồng ứng dụng.

**Kết quả chính:** bộ 300 nhân viên/28 ngày cân đối đã phủ **3.780/3.780 vị trí** ở ngân sách 8 và 15 giây, thay cho lỗi chưa có nghiệm của v2. ALNS có cải thiện so với CP-SAT đơn thuần ở các bài lớn đã đo. **Hybrid chưa luôn hơn Greedy**: trên bộ 300 người cân đối và bộ ít lựa chọn, Greedy vẫn có điểm phạt mềm tốt hơn và nhanh hơn.

## Đã thay đổi gì?

1. **Xung đột ca bằng clique.** Với giờ ca cố định, kéo dài khoảng ca thêm thời gian nghỉ và dùng `AtMostOne` cho các nhóm khoảng giao nhau, thay cho optional interval/`NoOverlap`. Test đối chiếu mọi tập con trên các bộ ca ngẫu nhiên; kiểm tra cả ranh giới đúng thời gian nghỉ và thiếu 1 nanosecond.
2. **Gom dữ liệu nhóm/tuần trước khi dựng mô hình.** Mục tiêu công bằng không còn quét mọi ứng viên cho từng nhân viên/tuần.
3. **Tìm kiếm theo quy mô.** Dưới 5.000 lựa chọn tự do dùng CP-SAT/LP mặc định. Bài lớn dùng `PARTIAL_FIXED_SEARCH`, tắt LP và một vòng presolve; ưu tiên phủ nhu cầu quan trọng/khan hiếm, thời gian ca, PREFERRED, xoay thứ tự nhân viên theo ngày. CP-SAT vẫn lan truyền, quay lui và tối ưu đầy đủ; không gọi bộ Greedy cũ. Ngưỡng 5.000 là heuristic cần đánh giá thêm.
4. **Giới hạn vùng ALNS.** Mỗi vòng mở tối đa `min(800, ceil(20% số lựa chọn))` lựa chọn trong ±3 ngày quanh ca mốc; tối đa 12 nhân viên với toán tử theo nhân viên. Ưu tiên nhu cầu còn thiếu và phân công cạnh tranh; chọn cả nhân viên thiếu/dư định mức.
5. **Dựng mô hình hạn chế thay vì clone toàn bộ biến.** Lựa chọn ngoài vùng giữ nguyên, phần phạt không đổi tính thành hằng số. Mọi ràng buộc vẫn có hiệu lực. Test so mục tiêu với mô hình đầy đủ bị khóa tại nhiều incumbent, kể cả lịch rỗng, và kiểm tra lịch khóa vi phạm ngày liên tiếp bị từ chối.
6. **Siết cận phạt/ngân sách theo số người có thể xếp.** Không cộng chi phí của tất cả ứng viên vào cận của một nhu cầu chỉ cần ít người. Thứ tự tối ưu giữ nguyên: tổng thiếu → thiếu theo ưu tiên → phạt mềm. Hệ số mã hóa đổi nên không so `internalObjective` giữa v2/v3; dùng các thành phần được tính lại độc lập.
7. **Ràng buộc phạt chính xác ở cả nghiệm FEASIBLE.** Thiếu định mức, vượt chuẩn và vượt ngân sách dùng giá trị `max(0, biểu thức)` thay cho chỉ cận dưới. Điều này tránh phần phạt dư làm lệch điểm nhận/từ chối incumbent. Đã bổ sung test hồi quy cho cả ba loại phạt và chạy lại toàn bộ 54 lượt trên mã cuối.
8. **Log chẩn đoán tùy chọn.** JVM property `-Dsmartshift.scheduling.profile=true` ghi dựng mô hình, tiến trình CP-SAT, kích thước vùng và reward từng vòng; mặc định tắt, không thay response API.

Đây là so sánh toàn bộ gói cải tiến, chưa có ablation đầy đủ để quy lợi ích cho từng thành phần. Tham số đã được hiệu chỉnh trên một số lượt thăm dò; không phải phép thử được đăng ký trước hay bằng chứng tổng quát.

## Kết quả trước/sau trên các bộ cũ

45 lượt tương ứng v2 dùng cùng checksum đầu vào, ngân sách, seed và giới hạn vòng. V2 có 39 lượt hợp lệ và 6 lượt chưa có nghiệm; v3 có 45 lượt hợp lệ. Giới hạn mỗi container 1 CPU, CP-SAT một luồng, job tuần tự.

| Dữ liệu/cấu hình | v2 hybrid | v3 hybrid | Diễn giải |
|---|---|---|---|
| NRP 1 chuyển đổi, 2 giây, seed 0/1/2 | Thiếu 7, soft 36.000 | Giữ nguyên, OPTIMAL | Không hồi quy chất lượng ở các lượt đã đo |
| NRP 3 chuyển đổi, 2/8/15 giây | Thiếu 14, soft 90.800 | Giữ nguyên, OPTIMAL | Vẫn dừng sớm, không sửa thêm sau chứng minh tối ưu |
| Thiếu nhân sự 30 người, 2 giây, seed 0/1/2 | Thiếu TB 143 | Thiếu TB 138,33 | Cải thiện TB trước/sau nhưng vẫn kém Greedy thiếu 109 |
| Thiếu nhân sự 30 người, 8 giây, seed 42 | Thiếu 106, soft 211.808, FEASIBLE | Thiếu 106, soft 211.408, OPTIMAL; khoảng 2,49 giây | Giữ mức phủ, cải thiện mềm và kết thúc sớm |
| Thiếu nhân sự 30 người, 15 giây, seed 0/1 | Thiếu 106, soft 211.808 | Thiếu 106, soft 211.408, OPTIMAL; TB 2,50 giây | Tốt hơn Greedy thiếu 109; bằng CP-SAT |
| Cân đối 300 người, 8 giây, seed 42 | Chưa có lịch ở cả CP-SAT/hybrid | Cả hai phủ 3.780/3.780 vị trí | Khắc phục lỗi chưa có nghiệm trong lượt thử này |
| Cân đối 300 người, 15 giây, seed 0/1 | Chưa có lịch ở 4 lượt CP-SAT/hybrid | Cả 4 lượt phủ 3.780/3.780 vị trí | Cần thêm instance/seed để đánh giá rộng hơn |

Ở bộ 30 người/2 giây, v3 CP-SAT đơn thuần thiếu 127/108/141 vị trí theo seed 0/1/2, còn hybrid thiếu 144/130/141. Chia thời gian cho ALNS vẫn có thể bất lợi ở ngân sách ngắn; không bỏ trường hợp này khỏi báo cáo. Chỉ số phạt mềm thấp hơn khi phủ ít người hơn không được dùng để kết luận lịch tốt hơn.

## ALNS giúp gì trên bài 300 người cân đối?

Các lịch dưới đây đều đủ nhu cầu và không thiếu theo ưu tiên; có thể so soft penalty trực tiếp, thấp hơn tốt hơn.

| Cấu hình | Greedy | CP-SAT | Hybrid | Hybrid so với CP-SAT |
|---|---:|---:|---:|---|
| 8 giây, seed 42, tối đa 20 vòng | 3.281.032 | 3.823.117 | 3.704.091 | Giảm khoảng 3,11% |
| 15 giây, seed 0/1, tối đa 1000 vòng, trung bình | 3.281.032 | 3.823.042 | 3.460.188,5 | Giảm khoảng 9,49%; thắng cả hai seed |

Hybrid thực hiện 3 vòng ở 8 giây, trung bình 9 vòng ở 15 giây. Thời gian giải thực tế tương ứng khoảng 8,06 và 15,00 giây, gồm dựng mô hình/tìm kiếm, bỏ nạp native và khởi động JVM. Greedy vẫn tốt hơn hybrid theo chỉ số mềm trên instance này.

## Kiểm tra thêm ngoài bộ cân đối

Ngân sách 15 giây, seed 42, tối đa 20 vòng ALNS. Mỗi thuật toán một lượt; các số dưới đây là số vị trí còn thiếu, không phải số nhân viên trong kỳ.

| Dữ liệu | Greedy thiếu | CP-SAT thiếu | Hybrid thiếu | Nhận xét |
|---|---:|---:|---:|---|
| NRP 13 chuyển đổi, 120 người/28 ngày | 145 | 166 | **135** | Hybrid phủ tốt hơn cả hai đối chứng trong lượt này |
| Mô phỏng thiếu người, 300 người/28 ngày | 2.257 | 2.254 | **2.220** | Hybrid phủ tốt hơn; phạt mềm tăng vì phân công được nhiều người hơn |
| Mô phỏng ít lựa chọn, 300 người/28 ngày | 0 | 0 | 0 | Soft: Greedy 3.431.671; CP-SAT 3.955.935; hybrid 3.715.634 |

Ưu tiên so độ phủ trước soft penalty. Hybrid thắng CP-SAT ở cả ba bộ bổ sung theo bộ ba chỉ số; Greedy vẫn tốt hơn hybrid về mềm/thời gian ở bộ ít lựa chọn. Đây là dữ liệu kiểm tra bổ sung sau khi cố định bản cuối; chưa kiểm định thống kê. Các seed cùng instance không phải các mẫu bài toán độc lập.

## Bằng chứng

- [Cấu hình ứng dụng 8 giây/20 vòng](app-8s-20/REPORT.md), [CSV](app-8s-20/runs.csv), [so v2/v3](app-8s-20/comparison-with-previous.csv), [biểu đồ](app-8s-20/comparison.svg).
- [15 giây](15s/REPORT.md), [CSV](15s/runs.csv), [so v2/v3](15s/comparison-with-previous.csv), [biểu đồ](15s/comparison.svg).
- [2 giây](2s/REPORT.md), [CSV](2s/runs.csv), [so v2/v3](2s/comparison-with-previous.csv), [biểu đồ](2s/comparison.svg).
- [Các bộ lớn bổ sung](extra-large/REPORT.md), [CSV](extra-large/runs.csv), [biểu đồ](extra-large/comparison.svg).

Mỗi thư mục giữ `run.json`, kết quả gốc, audit, summary/comparisons và metadata biểu đồ. [Log kiểm thử Java](java-verification.txt). Ngân sách tìm kiếm không phải hạn chót tuyệt đối của request; CSV giữ thời gian thực tế.

Đã đối chiếu checksum nguồn/đầu vào, đọc lại 54 lời giải và tính lại mọi audit/CSV; 8 lượt bộ giải phủ đủ nhu cầu có `internalObjective` khớp chính xác soft penalty tính từ lịch. Xem [kết quả xác minh](verification.json) và [bản chụp mã](source/HybridScheduleOptimizer.java.txt).

## Đo chẩn đoán nguyên nhân chậm

Log trước cải tiến trên bộ 300 người cân đối ghi khoảng 2,75 giây dựng mô hình, 6,51 giây presolve/preload trong native solver và 83.407 vòng lặp LP; chưa có nghiệm khi hết ngân sách 15 giây. Đây là dấu hiệu cần giảm chi phí dựng/presolve và tìm nghiệm ban đầu, không phải chứng minh LP luôn bất lợi cho mọi bài.

Một lượt profile của **đúng mã cuối**, 15 giây/seed 42/20 vòng, ghi khoảng 2,12 giây dựng mô hình; native bắt đầu tìm kiếm sau 2,48 giây và có nghiệm đầu sau 3,24 giây kể từ bắt đầu native. ALNS thực hiện 10 vòng, 9 vòng cải thiện, mở 128–800 lựa chọn trong tổng 22.153 lựa chọn; thời gian dựng mỗi vùng khoảng 0,12–0,40 giây. Log profile có overhead nên không trộn vào 54 lượt benchmark hoặc dùng làm tỷ lệ tăng tốc tổng quát.

[Log profile cuối](profiles/final/ALNS_CP_SAT.txt), [tổng hợp từng vòng](profiles/final/summary.json), [checksum và image ID](profiles/final/metadata.json), [audit lịch profile](profiles/final/ALNS_CP_SAT.audit.json). Các log/biến thể thăm dò trước sửa điểm nằm riêng ở `profiles/calibration/`. Chưa thực hiện ablation đầy đủ.

## Kiểm thử và giới hạn

**86 unit test Java đạt**, gồm 25 test HybridScheduleOptimizer với CP-SAT native thực. **9 test Python đạt**. Tất cả 54 lời giải cuối được kiểm tra độc lập. Không chạy lại Spring context có DB hoặc toàn bộ E2E ở đợt này. Snapshot benchmark bắt đầu trống, UTC, lựa chọn vị trí/giờ rảnh đã được lọc; lịch có sẵn được bảo vệ bằng unit test và bộ E2E riêng.

Các lượt trước sửa biến phạt được giữ riêng ở [thư mục hiệu chỉnh](calibration-before-exact-penalties/README.md); không trộn vào thống kê 54 lượt cuối. Số liệu có log profile được giữ riêng, không tính vào các trung bình benchmark vì log có overhead.

Chưa chạy ma trận chính 630 job hay đánh giá đồng thời nhiều request. NRP đã chuyển đổi sang mô hình Smart Shift; không so điểm với best-known NRP gốc. Số liệu thời gian giữa các lượt có thể dao động do wall-clock, kể cả cùng seed.

Mã nằm trong backend và image benchmark; không tự triển khai lại ứng dụng hay thay dữ liệu kiểm thử thủ công. Cần build lại backend trước khi thử qua giao diện. Có tiến bộ rõ về tìm nghiệm bài lớn và lợi ích ALNS ở các bộ đã đo, nhưng chưa đủ để tuyên bố thuật toán tối ưu nhất hay luôn vượt Greedy.

Image v2 vẫn được giữ ở `smart-shift-benchmark:budget-v2`. Image cuối là `smart-shift-benchmark:scale-v3`; tag mặc định `smart-shift-benchmark:local` trên máy này trỏ tới v3. Image ID chính xác được lưu trong từng `run.json`.

## Tái lập

Từ thư mục gốc repository:

```powershell
python qa-automation/benchmarks/prepare_data.py
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-benchmark:scale-v3 smart-shift-be
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:scale-v3 --datasets nrp03,synthetic_30_14_shortage,synthetic_300_28_balanced --seconds 8 --seeds 42 --hybrid-iterations 20
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:scale-v3 --datasets nrp03,synthetic_30_14_shortage,synthetic_300_28_balanced --seconds 15 --seeds 0,1
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:scale-v3
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:scale-v3 --datasets nrp13,synthetic_300_28_shortage,synthetic_300_28_tight --seconds 15 --seeds 42 --hybrid-iterations 20
```

Xem [kế hoạch thực nghiệm](../../EXPERIMENT_PLAN.md) để hiểu các giả định và phép đo. V3 dùng cùng baseline và cùng đầu vào v2; việc siết cận mã hóa không thay thứ tự so sánh độc lập.
