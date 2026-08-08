package com.smartshift.dto.user;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
    @NotNull(message = "Trạng thái hoạt động không được để trống")
    Boolean active
) {
}
