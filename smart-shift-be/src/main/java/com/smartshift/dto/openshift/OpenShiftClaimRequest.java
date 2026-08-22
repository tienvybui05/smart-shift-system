package com.smartshift.dto.openshift;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OpenShiftClaimRequest(
    @NotNull(message = "Ca làm không được để trống")
    @Positive(message = "Id ca làm phải lớn hơn 0")
    Long workShiftId,

    @Size(max = 1000, message = "Lý do nhận ca không được vượt quá 1000 ký tự")
    String reason
) {
}
