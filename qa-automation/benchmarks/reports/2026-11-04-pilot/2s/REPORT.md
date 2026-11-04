# Kết quả benchmark Smart Shift

Kết quả trên dữ liệu đã chuyển đổi sang mô hình Smart Shift; không phải điểm chuẩn NRP gốc.
Chỉ so sánh chất lượng giữa các lịch hợp lệ. Ưu tiên: thiếu người → thiếu theo ưu tiên → soft penalty.
Soft penalty thấp hơn tốt hơn khi hai tiêu chí trước bằng nhau. Mỗi ô là trung bình các lần chạy hợp lệ.

| Dữ liệu | Giây | Thuật toán | Hợp lệ/tổng | Thiếu | Phủ (%) | Soft penalty | Thời gian giải (ms) |
|---|---:|---|---:|---:|---:|---:|---:|
| nrp01_smartshift-projection-v1 | 2 | ALNS_CP_SAT | 3/3 | 7.0 | 90.1 | 36,000 | 1,977.7 |
| nrp01_smartshift-projection-v1 | 2 | CP_SAT | 3/3 | 7.0 | 90.1 | 36,000 | 514.7 |
| nrp01_smartshift-projection-v1 | 2 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 7.0 | 90.1 | 36,800 | 99.6 |
| nrp03_smartshift-projection-v1 | 2 | ALNS_CP_SAT | 3/3 | 14.0 | 90.9 | 90,800 | 2,005.3 |
| nrp03_smartshift-projection-v1 | 2 | CP_SAT | 3/3 | 14.0 | 90.9 | 90,800 | 879.9 |
| nrp03_smartshift-projection-v1 | 2 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 19.0 | 87.7 | 102,000 | 78.8 |
| synthetic_30_14_shortage_s42 | 2 | ALNS_CP_SAT | 0/3 | — | — | — | — |
| synthetic_30_14_shortage_s42 | 2 | CP_SAT | 3/3 | 142.0 | 62.4 | 204,917 | 2,081.3 |
| synthetic_30_14_shortage_s42 | 2 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 109.0 | 71.2 | 232,922 | 92.4 |

Greedy là bản tái dựng thứ tự/chấm điểm cũ, áp dụng chung ràng buộc cứng; không chạy lại dịch vụ cũ nguyên trạng.
Greedy chạy một lần mỗi dữ liệu/ngân sách vì không dùng seed. Hai bộ giải chạy từng seed đã cấu hình.
CP-SAT chạy một luồng. Hybrid dùng tối đa 1000 vòng để phép thử giới hạn chủ yếu bằng thời gian; ứng dụng mặc định 20 vòng.
Thời gian giải bao gồm dựng mô hình/tìm kiếm; processWallMillis còn gồm khởi động JVM và nạp native.
Giới hạn thời gian tìm kiếm không phải hạn chót tuyệt đối của tiến trình. Xem CSV để biết mức vượt thực tế.
Không suy ra hybrid luôn tốt hơn CP-SAT hoặc tối ưu toàn cục từ bảng này.

Chi tiết từng lần chạy: `runs.csv`; trung bình/độ lệch chuẩn: `summary.csv`; so sánh từng cặp seed: `comparisons.csv`; cấu hình và checksum: `run.json`.
