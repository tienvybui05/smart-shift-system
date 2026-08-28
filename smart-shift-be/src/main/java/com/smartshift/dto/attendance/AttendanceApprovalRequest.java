package com.smartshift.dto.attendance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AttendanceApprovalRequest(
    Instant checkInAt,
    Instant checkOutAt,

    @NotNull(message = "Thời gian nghỉ không được để trống")
    @Min(value = 0, message = "Thời gian nghỉ không được âm")
    @Max(value = 1440, message = "Thời gian nghỉ không được vượt quá 1440 phút")
    Short breakMinutes,

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    String note
) {
}
