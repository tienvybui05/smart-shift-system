package com.smartshift.dto.device;

import jakarta.validation.constraints.NotNull;

public record DeviceReviewRequest(
    @NotNull(message = "Quyết định duyệt không được để trống")
    Boolean approved,

    String note
) {
}
