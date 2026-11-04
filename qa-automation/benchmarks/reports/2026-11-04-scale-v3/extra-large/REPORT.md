# Kết quả benchmark Smart Shift

Kết quả trên dữ liệu đã chuyển đổi sang mô hình Smart Shift; không phải điểm chuẩn NRP gốc.
Chỉ so sánh chất lượng giữa các lịch hợp lệ. Ưu tiên: thiếu người → thiếu theo ưu tiên → soft penalty.
Soft penalty thấp hơn tốt hơn khi hai tiêu chí trước bằng nhau. Mỗi ô là trung bình các lần chạy hợp lệ.

| Dữ liệu | Giây | Thuật toán | Hợp lệ/tổng | Thiếu | Phủ (%) | Soft penalty | Thời gian giải (ms) |
|---|---:|---|---:|---:|---:|---:|---:|
| nrp13_smartshift-projection-v1 | 15 | ALNS_CP_SAT | 1/1 | 135.0 | 92.2 | 836,200 | 15,045.0 |
| nrp13_smartshift-projection-v1 | 15 | CP_SAT | 1/1 | 166.0 | 90.4 | 859,200 | 15,242.6 |
| nrp13_smartshift-projection-v1 | 15 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 145.0 | 91.7 | 884,800 | 806.8 |
| synthetic_300_28_shortage_s42 | 15 | ALNS_CP_SAT | 1/1 | 2,220.0 | 70.6 | 4,324,922 | 15,243.6 |
| synthetic_300_28_shortage_s42 | 15 | CP_SAT | 1/1 | 2,254.0 | 70.2 | 4,254,023 | 15,173.4 |
| synthetic_300_28_shortage_s42 | 15 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 2,257.0 | 70.1 | 4,259,729 | 4,389.6 |
| synthetic_300_28_tight_s42 | 15 | ALNS_CP_SAT | 1/1 | 0.0 | 100.0 | 3,715,634 | 15,059.5 |
| synthetic_300_28_tight_s42 | 15 | CP_SAT | 1/1 | 0.0 | 100.0 | 3,955,935 | 15,207.4 |
| synthetic_300_28_tight_s42 | 15 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 0.0 | 100.0 | 3,431,671 | 2,517.9 |

Greedy là bản tái dựng thứ tự/chấm điểm cũ, áp dụng chung ràng buộc cứng; không chạy lại dịch vụ cũ nguyên trạng.
Greedy chạy một lần mỗi dữ liệu/ngân sách vì không dùng seed. Hai bộ giải chạy từng seed đã cấu hình.
CP-SAT chạy một luồng. Hybrid cấu hình tối đa 20 vòng; ứng dụng mặc định 20 vòng.
Thời gian giải bao gồm dựng mô hình/tìm kiếm; processWallMillis còn gồm khởi động JVM và nạp native.
Giới hạn thời gian tìm kiếm không phải hạn chót tuyệt đối của tiến trình. Xem CSV để biết mức vượt thực tế.
Không suy ra hybrid luôn tốt hơn CP-SAT hoặc tối ưu toàn cục từ bảng này.

Chi tiết từng lần chạy: `runs.csv`; trung bình/độ lệch chuẩn: `summary.csv`; so sánh từng cặp seed: `comparisons.csv`; cấu hình và checksum: `run.json`.
