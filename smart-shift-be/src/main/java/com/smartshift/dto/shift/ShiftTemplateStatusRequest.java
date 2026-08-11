package com.smartshift.dto.shift;

import jakarta.validation.constraints.NotNull;

public record ShiftTemplateStatusRequest(
    @NotNull(message = "Trạng thái hoạt động không được để trống")
    Boolean active
) {
}
