# Sửa ngân sách khởi tạo và dừng tối ưu — 04/11/2026

Đã có [bản v3](../2026-11-04-scale-v3/README.md) cải thiện mô hình và vùng ALNS; các hạn chế và kết quả bên dưới mô tả riêng phiên bản v2 để đối chứng.

Đã sửa hai vấn đề phát hiện ở [pilot gốc](../2026-11-04-pilot/README.md), chạy **45 lượt mới** trên cùng các fixture: **39 lượt trả lịch hợp lệ, 6 lượt chưa tìm được lịch trong thời gian cho phép**. Kiểm tra độc lập không phát hiện vi phạm cứng ở các lịch được trả về. Nguồn Java, Python và đầu vào đều được đối chiếu SHA-256 với metadata của mỗi đợt.

**Kết quả chính:** bài nhỏ đã dừng sớm và giữ nguyên chất lượng; bộ 30 nhân viên không còn thất bại khởi tạo ở ngân sách 2 giây trong ba seed đã thử. **Bộ 300 nhân viên vẫn chưa được giải trong 8/15 giây** trên cấu hình thử một CPU. Hai sửa đổi này chưa đủ để chứng nhận khả năng triển khai quy mô lớn.

## Thay đổi đã làm

1. **Dùng ngân sách còn lại khi chưa có lịch.** CP-SAT khởi tạo nhận giới hạn toàn bộ thời gian còn lại. Mốc 45% chỉ yêu cầu chuyển sang ALNS khi đã có nghiệm. Nếu tới mốc đó vẫn chưa có nghiệm, giữ nguyên lần giải đang chạy; không khởi động lại presolve. Nếu nghiệm đầu xuất hiện muộn, chuyển thời gian còn lại cho ALNS. Bộ hẹn giờ xử lý cả trường hợp đã có nghiệm sớm nhưng không có callback cải thiện tiếp theo; được hủy trong `finally`.
2. **Dừng khi đã chứng minh tối ưu trên mô hình đầy đủ.** Trả `OPTIMAL`, `iterations=0`, trọng số toán tử `[1,1,1]`. Không tiếp tục chạy ALNS. Chứng nhận OPTIMAL của vùng sửa không được nâng thành chứng nhận toàn cục.
3. **Kiểm tra cấu hình ứng dụng.** CLI/harness hỗ trợ `--hybrid-iterations`, ghi số vòng cấu hình vào metadata và kết quả. Đã đo thêm 8 giây/20 vòng/seed 42.
4. **So sánh trước/sau có kiểm tra đầu vào.** `compare_runs.py` từ chối ghép các đợt khác checksum dữ liệu, ngân sách, seed, giới hạn vòng, số luồng hoặc giới hạn CPU. Các lượt lỗi không được gán điểm chất lượng giả để so sánh.

Hàm mục tiêu, các điều kiện cứng và ba toán tử ALNS giữ nguyên trong đợt sửa này. Greedy vẫn chỉ là baseline thực nghiệm. Không thêm Greedy vào luồng xếp lịch ứng dụng.

Các request khó có thể chờ gần hết ngân sách đã cấu hình thay vì dừng ở mốc 45%. Nếu hết thời gian vẫn chưa tìm được nghiệm, bộ giải tiếp tục báo lỗi để luồng service không lưu phân công mới.

## Kết quả trước/sau

Thời gian dưới đây là trung bình `solverWallMillis`, gồm dựng mô hình/tìm kiếm, bỏ nạp native và khởi động JVM. Chỉ so chất lượng khi cả hai lượt có lịch hợp lệ.

| Dữ liệu, cấu hình | Trước sửa | Sau sửa | Chất lượng |
|---|---|---|---|
| NRP 1 chuyển đổi, 2 giây, seed 0/1/2 | Hybrid khoảng 1,98 giây | Khoảng 0,28 giây, 0 vòng ALNS | Giữ nguyên thiếu 7 và soft penalty 36.000 |
| NRP 3 chuyển đổi, 2 giây, seed 0/1/2 | Khoảng 2,01 giây | Khoảng 0,73 giây, 0 vòng ALNS | Giữ nguyên thiếu 14 và soft penalty 90.800 |
| NRP 3 chuyển đổi, 15 giây, seed 0/1 | Khoảng 15,01 giây | Khoảng 0,69 giây, 0 vòng ALNS | Giữ nguyên thiếu 14 và soft penalty 90.800 |
| Mô phỏng thiếu người 30 nhân viên, 2 giây, seed 0/1/2 | Hybrid chưa có lịch 3/3 lượt | Trả lịch hợp lệ 3/3 lượt | Thiếu trung bình 143; chưa tốt bằng Greedy thiếu 109 |
| Mô phỏng thiếu người 30 nhân viên, 15 giây, seed 0/1 | Thiếu 106 ở cả hai seed | Giữ nguyên thiếu 106 ở cả hai seed | Tốt hơn Greedy thiếu 109; bằng CP-SAT đơn thuần trong các lần thử |
| Mô phỏng cân đối 300 nhân viên, 15 giây, seed 0/1 | Hybrid chưa có lịch, dừng khoảng 8,2 giây | Vẫn chưa có lịch, dùng khoảng 15,2 giây | Chưa giải quyết được giới hạn quy mô; Greedy phủ đủ nhu cầu |

Ở bộ 30 người/2 giây, số vị trí thiếu của hybrid theo seed 0/1/2 là 154/156/119. Việc trả được lịch đã cải thiện độ sẵn sàng so với lỗi khởi tạo trước đây, **không đồng nghĩa chất lượng đã tốt hơn Greedy**.

Đường CP-SAT đơn thuần vẫn nhận toàn bộ ngân sách và không dùng bộ hẹn giờ chuyển pha. Kết quả CP-SAT cũng dao động giữa hai đợt do giới hạn theo wall-clock. Không gán mọi khác biệt trước/sau cho sửa đổi thuật toán, và chưa thực hiện kiểm định thống kê.

## Kiểm tra cấu hình mặc định ứng dụng

Ngân sách 8 giây, tối đa 20 vòng, seed 42, mỗi container 1 CPU. Đây là phép đo bộ tối ưu trên snapshot, chưa phải thời gian request API/DB.

| Dữ liệu | Greedy thiếu | CP-SAT thiếu | Hybrid thiếu | Thời gian hybrid | Vòng ALNS |
|---|---:|---:|---:|---:|---:|
| NRP 3 chuyển đổi, 20 người/14 ngày | 19 | 14 | 14 | Khoảng 0,80 giây | 0 |
| Mô phỏng thiếu người, 30 người/14 ngày | 109 | 106 | 106 | Khoảng 8,00 giây | 7 |
| Mô phỏng cân đối, 300 người/28 ngày | 0 | Chưa có lịch | Chưa có lịch | Xem CSV của lượt lỗi | 0 |

Không có số đo cấu hình này trước sửa để kết luận về mức cải thiện trước/sau. Chưa thấy lợi ích về chất lượng của vòng ALNS so với CP-SAT đơn thuần ở những lượt đã đo. Cần tiếp tục nghiên cứu tạo nghiệm ban đầu cho bài lớn và thu hẹp vùng phá/sửa; chưa thay đổi các phần đó trong bản v2.

## Kiểm thử và bằng chứng

- **20 kiểm thử Java đạt** trong Docker Linux: 18 kiểm thử lớp HybridScheduleOptimizer và 2 kiểm thử baseline. Bao gồm native CP-SAT, dừng sớm OPTIMAL, tiếp tục khi hết lát thời gian mà chưa có nghiệm, chuyển pha khi có nghiệm sớm/muộn, bảo toàn incumbent và không chứng nhận toàn cục từ vùng sửa. Các kiểm thử điều phối dùng phản hồi có kịch bản để tránh phụ thuộc tốc độ máy; kiểm thử native vẫn chạy riêng. [Log xác nhận](java-verification.txt).
- **9 kiểm thử Python đạt**, gồm bộ đánh giá lịch và phép so sánh trước/sau.
- **21 lượt ở 2 giây:** 21 hợp lệ, 0 lỗi. [Báo cáo](2s/REPORT.md), [CSV từng lượt](2s/runs.csv), [so trước/sau](2s/comparison-with-previous.csv), [biểu đồ](2s/comparison.svg).
- **15 lượt ở 15 giây:** 11 hợp lệ, 4 lỗi chưa có nghiệm ở bộ 300 người. [Báo cáo](15s/REPORT.md), [CSV từng lượt](15s/runs.csv), [so trước/sau](15s/comparison-with-previous.csv), [biểu đồ](15s/comparison.svg).
- **9 lượt ở 8 giây/20 vòng:** 7 hợp lệ, 2 lỗi chưa có nghiệm ở bộ 300 người. [Báo cáo](app-8s-20/REPORT.md), [CSV từng lượt](app-8s-20/runs.csv), [biểu đồ](app-8s-20/comparison.svg).

Mỗi thư mục giữ `run.json`, JSON lời giải, audit, summary CSV và metadata biểu đồ. Tất cả 45 lời giải/kết quả lỗi đã được đọc lại và đánh giá độc lập, đối chiếu với CSV/audit đã lưu. Không thay đổi dữ liệu E2E thủ công hay khởi động dịch vụ DB trong các phép đo này.

OR-Tools 9.15.6755, Java 17.0.21, Docker Linux, CP-SAT một luồng; các job chạy tuần tự. Hai đợt 2/15 giây dùng tối đa 1000 vòng giống pilot gốc. Dữ liệu NRP đã chuyển đổi sang bài toán Smart Shift; không so điểm với best-known NRP gốc.

## Chạy lại

Image v1 cũ được giữ dưới tag `smart-shift-benchmark:pilot-v1`. Image v2 có tag `smart-shift-benchmark:budget-v2`; tag mặc định `smart-shift-benchmark:local` trên máy này cũng đã trỏ tới v2. Image ID chính xác được ghi trong từng `run.json`. Không dùng harness mới truyền sáu tham số với CLI năm tham số của image pilot cũ.

Từ thư mục gốc, build lại mã hiện tại và chạy:

```powershell
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-benchmark:budget-v2 smart-shift-be
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:budget-v2
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:budget-v2 --datasets nrp03,synthetic_30_14_shortage,synthetic_300_28_balanced --seconds 15 --seeds 0,1
python qa-automation/benchmarks/run_benchmarks.py --docker --docker-image smart-shift-benchmark:budget-v2 --datasets nrp03,synthetic_30_14_shortage,synthetic_300_28_balanced --seconds 8 --seeds 42 --hybrid-iterations 20
```

Các đợt còn lỗi trả exit code khác 0 nhưng vẫn xuất đủ báo cáo. Chạy `prepare_data.py` trước nếu chưa có dữ liệu tải về. Xem [kế hoạch thực nghiệm](../../EXPERIMENT_PLAN.md) để hiểu các giả định của dữ liệu và cách đo.

So sánh báo cáo cũ với một đợt mới có cùng cấu hình, thay đường dẫn cuối bằng thư mục kết quả thực tế:

```powershell
python qa-automation/benchmarks/compare_runs.py qa-automation/benchmarks/reports/2026-11-04-pilot/15s qa-automation/benchmarks/results/<timestamp>
```

Thay đổi nằm ở mã nguồn và image benchmark. Để đưa mã mới vào môi trường ứng dụng Docker dùng cho kiểm thử thủ công, cần build lại backend theo quy trình của môi trường đó; các phép đo này không tự triển khai hay chạy lại toàn bộ E2E.
