# Số liệu hiệu chỉnh trước khi ràng buộc chính xác biến phạt

Thư mục này giữ nguyên 54 lượt thăm dò và bản chụp mã trước bước sửa cuối. Không dùng làm bảng kết quả cuối của v3.

Trong một lượt ALNS 15 giây, seed 1, bộ 300 nhân viên cân đối, `internalObjective=3.447.040` trong khi phạt mềm tính lại từ lịch là `3.446.850`. Lịch không vi phạm ràng buộc cứng; ba biến phạt chỉ có cận dưới nên nghiệm FEASIBLE có thể còn phần phạt dư. Sau phát hiện này, mô hình đã ràng buộc chính xác các phạt bằng `max(0, biểu thức)` và chạy lại các đợt chính/bổ sung.

Kết quả cuối, kiểm thử và số liệu xác nhận nằm ở [báo cáo v3](../README.md). Các file `comparison-with-previous.csv` ở đây chỉ mô tả lượt hiệu chỉnh trước sửa. File `README-before-correction.md` là bản ghi chú cũ trước hiệu chỉnh, số liệu/các liên kết tương đối trong đó không phải báo cáo cuối.
