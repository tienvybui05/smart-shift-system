package com.smartshift.dto.autoschedule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AutoScheduleRequest(
    @NotNull(message = "Kỳ xếp lịch không được để trống")
    @Positive(message = "Id kỳ xếp lịch phải lớn hơn 0")
    Long schedulePeriodId
) {
}
