# Kết quả benchmark Smart Shift

Kết quả trên dữ liệu đã chuyển đổi sang mô hình Smart Shift; không phải điểm chuẩn NRP gốc.
Chỉ so sánh chất lượng giữa các lịch hợp lệ. Ưu tiên: thiếu người → thiếu theo ưu tiên → soft penalty.
Soft penalty thấp hơn tốt hơn khi hai tiêu chí trước bằng nhau. Mỗi ô là trung bình các lần chạy hợp lệ.

| Dữ liệu | Giây | Thuật toán | Hợp lệ/tổng | Thiếu | Phủ (%) | Soft penalty | Thời gian giải (ms) |
|---|---:|---|---:|---:|---:|---:|---:|
| nrp03_smartshift-projection-v1 | 15 | ALNS_CP_SAT | 2/2 | 14.0 | 90.9 | 90,800 | 1,044.8 |
| nrp03_smartshift-projection-v1 | 15 | CP_SAT | 2/2 | 14.0 | 90.9 | 90,800 | 750.7 |
| nrp03_smartshift-projection-v1 | 15 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 19.0 | 87.7 | 102,000 | 97.9 |
| synthetic_300_28_balanced_s42 | 15 | ALNS_CP_SAT | 2/2 | 0.0 | 100.0 | 3,460,188 | 15,001.5 |
| synthetic_300_28_balanced_s42 | 15 | CP_SAT | 2/2 | 0.0 | 100.0 | 3,823,042 | 15,249.3 |
| synthetic_300_28_balanced_s42 | 15 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 0.0 | 100.0 | 3,281,032 | 2,204.3 |
| synthetic_30_14_shortage_s42 | 15 | ALNS_CP_SAT | 2/2 | 106.0 | 72.0 | 211,408 | 2,499.9 |
| synthetic_30_14_shortage_s42 | 15 | CP_SAT | 2/2 | 106.0 | 72.0 | 211,408 | 2,638.8 |
| synthetic_30_14_shortage_s42 | 15 | GREEDY_SHARED_CONSTRAINTS | 1/1 | 109.0 | 71.2 | 232,922 | 156.2 |

Greedy là bản tái dựng thứ tự/chấm điểm cũ, áp dụng chung ràng buộc cứng; không chạy lại dịch vụ cũ nguyên trạng.
Greedy chạy một lần mỗi dữ liệu/ngân sách vì không dùng seed. Hai bộ giải chạy từng seed đã cấu hình.
CP-SAT chạy một luồng. Hybrid cấu hình tối đa 1000 vòng; ứng dụng mặc định 20 vòng.
Thời gian giải bao gồm dựng mô hình/tìm kiếm; processWallMillis còn gồm khởi động JVM và nạp native.
Giới hạn thời gian tìm kiếm không phải hạn chót tuyệt đối của tiến trình. Xem CSV để biết mức vượt thực tế.
Không suy ra hybrid luôn tốt hơn CP-SAT hoặc tối ưu toàn cục từ bảng này.

Chi tiết từng lần chạy: `runs.csv`; trung bình/độ lệch chuẩn: `summary.csv`; so sánh từng cặp seed: `comparisons.csv`; cấu hình và checksum: `run.json`.
