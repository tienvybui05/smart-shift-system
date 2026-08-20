package com.smartshift.dto.timeoff;

import com.smartshift.enums.TimeOffStatus;
import jakarta.validation.constraints.NotNull;

public record TimeOffReviewRequest(
    @NotNull(message = "Trạng thái xét duyệt không được để trống")
    TimeOffStatus status
) {
}
