package com.smartshift.dto.workshift;

import com.smartshift.enums.WorkShiftStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkShiftStatusRequest(
    @NotNull(message = "Trạng thái ca làm không được để trống")
    WorkShiftStatus status,

    @Size(max = 500, message = "Lý do thay đổi không được vượt quá 500 ký tự")
    String changeReason
) {
}
