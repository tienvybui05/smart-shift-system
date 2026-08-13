package com.smartshift.dto.workshift;

import com.smartshift.enums.WorkShiftStatus;
import jakarta.validation.constraints.NotNull;

public record WorkShiftStatusRequest(
    @NotNull(message = "Trạng thái ca làm không được để trống")
    WorkShiftStatus status
) {
}
