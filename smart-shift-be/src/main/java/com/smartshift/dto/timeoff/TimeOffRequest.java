package com.smartshift.dto.timeoff;

import com.smartshift.enums.LeaveType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TimeOffRequest(
    @NotNull(message = "Thời gian bắt đầu nghỉ không được để trống")
    Instant startAt,

    @NotNull(message = "Thời gian kết thúc nghỉ không được để trống")
    Instant endAt,

    @NotNull(message = "Loại nghỉ phép không được để trống")
    LeaveType leaveType,

    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự")
    String reason
) {
}
