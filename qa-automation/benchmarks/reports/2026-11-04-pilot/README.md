# Thử nghiệm ban đầu — 04/11/2026

Bản mới nhất là [báo cáo v3](../2026-11-04-scale-v3/README.md), sau cải thiện mô hình và vùng ALNS cho bài lớn. Đây là số liệu pilot gốc được giữ để đối chứng.

Đây là báo cáo phiên bản trước sửa. Đã có [báo cáo v2: ngân sách khởi tạo và dừng sớm](../2026-11-04-budget-v2/README.md), gồm so sánh trước/sau và cấu hình ứng dụng 8 giây/20 vòng. Các số liệu gốc bên dưới được giữ để đối chứng.

Đã chạy **36 lượt**, trong đó **29 lượt trả lịch và vượt qua kiểm tra độc lập**, **7 lượt bộ giải chưa tìm được lịch trong thời gian cho phép**. Không phát hiện vi phạm cứng ở 29 lịch được trả về.

**Chưa thể chốt hybrid hiện tại cho quy mô lớn.** Ở bộ mô phỏng 300 nhân viên/28 ngày, cả CP-SAT và hybrid chưa trả được lịch với ngân sách 15 giây trên cấu hình thử, trong khi baseline Greedy phủ đủ nhu cầu. Lỗi này không có nghĩa bài toán không có lịch hợp lệ.

Đây là pilot để kiểm tra công cụ và tìm điểm yếu, không phải thực nghiệm chính đã hoàn thành hoặc bằng chứng hybrid luôn tốt nhất.

## Kết quả đáng chú ý

Số trong ba cột cuối là **số vị trí còn thiếu người**, thấp hơn tốt hơn. Mỗi vị trí tương ứng một người cần được phân công vào một nhu cầu ca; không phải số nhân viên trong toàn kỳ.

| Dữ liệu | Ngân sách | Seed của hai bộ giải | Greedy | CP-SAT | ALNS + CP-SAT |
|---|---:|---|---:|---:|---:|
| NRP 1 chuyển đổi, 8 người/14 ngày | 2 giây | 0,1,2 | 7 | 7 | 7 |
| NRP 3 chuyển đổi, 20 người/14 ngày | 2 giây | 0,1,2 | 19 | 14 | 14 |
| Mô phỏng thiếu người, 30 người/14 ngày | 2 giây | 0,1,2 | 109 | TB 142; từng seed: 154,117,155 | Chưa có lịch, 3/3 lượt |
| NRP 3 chuyển đổi, 20 người/14 ngày | 15 giây | 0,1 | 19 | 14 | 14 |
| Mô phỏng thiếu người, 30 người/14 ngày | 15 giây | 0,1 | 109 | 106 | 106 |
| Mô phỏng cân đối, 300 người/28 ngày | 15 giây | 0,1 | 0 | Chưa có lịch, 2/2 lượt | Chưa có lịch, 2/2 lượt |

Ở NRP 1 chuyển đổi, hai bộ giải có cùng mức thiếu người với Greedy nhưng soft penalty tốt hơn: 36.000 so với 36.800. NRP 3 chuyển đổi có cùng kết quả tối ưu giữa CP-SAT và hybrid, trong khi CP-SAT hoàn thành nhanh hơn đáng kể: khoảng 0,69 giây so với 15 giây của hybrid ở đợt 15 giây.

Ở bộ 30 người, tăng ngân sách từ 2 lên 15 giây giúp cả hai bộ giải tìm được lịch tốt hơn Greedy về độ phủ: thiếu 106/378 thay vì 109/378 vị trí. Trong các lần đã chạy, chưa thấy phần ALNS cải thiện kết quả so với CP-SAT đơn thuần.

Ở bộ 300 người có 22.153 lựa chọn phân công, Greedy phủ 3.780/3.780 vị trí trong khoảng 1,85 giây giải. CP-SAT chưa có lịch sau khoảng 15,2 giây. Hybrid dừng vì bước khởi tạo chưa có lịch sau khoảng 8,1–8,2 giây, dù ngân sách toàn bộ là 15 giây. Không ghi các lượt lỗi thành lịch có 0% độ phủ để tránh so sánh sai; thời gian/lỗi gốc vẫn có trong CSV và JSON.

## Cấu hình và giới hạn diễn giải

- Java 17.0.21, OR-Tools 9.15.6755, Docker Linux, giới hạn mỗi container 1 CPU, CP-SAT một luồng, job chạy tuần tự.
- Đợt 2 giây có 3 bộ × (Greedy 1 lượt + CP-SAT 3 seed + hybrid 3 seed) = 21 lượt.
- Đợt 15 giây có 3 bộ × (Greedy 1 lượt + CP-SAT 2 seed + hybrid 2 seed) = 15 lượt.
- Greedy là bản tái dựng từ Git có cùng ràng buộc cứng. Không phải chạy dịch vụ cũ nguyên trạng.
- Hybrid benchmark tối đa 1000 vòng, ứng dụng mặc định 20 vòng; chưa dùng kết quả này để chứng nhận cấu hình triển khai.
- Dữ liệu NRP đã chuyển đổi sang mô hình Smart Shift, có giả định giờ bắt đầu/nghỉ và bỏ các quy tắc chưa hỗ trợ. Không so điểm này với best-known NRP gốc.
- Các seed cùng instance không phải các mẫu bài toán độc lập. Số seed/instance còn ít, chưa kiểm định thống kê và chưa chạy ma trận chính 630 job.
- `summary.csv` lấy trung bình chất lượng và thời gian trên các lần thành công; xem `runs.csv`/`run.json` để biết toàn bộ lỗi và thời gian từng lần.

## Công việc cần ưu tiên từ kết quả này

1. Nghiên cứu bước tạo nghiệm khả thi cho bài lớn và cách dùng ngân sách còn lại khi bước khởi tạo chưa có lịch. Hiện vòng ALNS chưa bắt đầu nếu CP-SAT khởi tạo thất bại.
2. Dừng sớm nếu CP-SAT trên mô hình đầy đủ đã chứng minh OPTIMAL; hiện hybrid vẫn tiếp tục sửa nên mất thời gian ở bài nhỏ.
3. Đo kích thước vùng lân cận, thời gian dựng/presolve/giải và số vòng sửa thật sự cải thiện. Điều chỉnh cách phá/sửa và thời gian mỗi vòng dựa trên dữ liệu đó; đây là hướng nghiên cứu, chưa phải nguyên nhân đã được xác nhận cho mọi thất bại.
4. Sau khi cải thiện, chạy lại cùng các fixture, giữ nguyên baseline và bổ sung seed/instance; kiểm tra cấu hình ứng dụng 20 vòng và E2E. Chưa nên chạy toàn bộ ma trận lớn rồi bỏ các trường hợp lỗi.

## Bằng chứng và cách tái lập

- [Báo cáo 2 giây](2s/REPORT.md), [dữ liệu từng lượt](2s/runs.csv), [thắng/hòa/thua](2s/comparisons.csv), [biểu đồ SVG](2s/comparison.svg).
- [Báo cáo 15 giây](15s/REPORT.md), [dữ liệu từng lượt](15s/runs.csv), [thắng/hòa/thua](15s/comparisons.csv), [biểu đồ SVG](15s/comparison.svg).
- Mỗi thư mục giữ `run.json` (SHA của đầu vào/mã nguồn, image ID và cấu hình), JSON lời giải và kết quả audit. Thư viện vẽ được ghi trong `plot-environment.txt` và `plot-metadata.json`.
- Kiểm tra công cụ: 7 test Python và 16 test Java gồm 14 test OR-Tools thực + 2 test baseline đã đạt trong image sạch. Java trực tiếp trên Windows bị Application Control chặn DLL; không thay đổi chính sách bảo mật.

Từ thư mục gốc repository, chuẩn bị dữ liệu, build image và chạy lại:

```powershell
python qa-automation/benchmarks/prepare_data.py
docker build -f qa-automation/benchmarks/Dockerfile -t smart-shift-benchmark:local smart-shift-be
python qa-automation/benchmarks/run_benchmarks.py --docker
python qa-automation/benchmarks/run_benchmarks.py --docker --datasets nrp03,synthetic_30_14_shortage,synthetic_300_28_balanced --seconds 15 --seeds 0,1
```

Script trả exit code khác 0 khi có lượt lỗi; vẫn ghi đầy đủ báo cáo. Thời gian giới hạn có thể tạo kết quả khác giữa các lần chạy, kể cả cùng seed. Xem [kế hoạch thực nghiệm](../../EXPERIMENT_PLAN.md) để hiểu các phép đo và ràng buộc chuyển đổi.
