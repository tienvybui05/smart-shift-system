package com.smartshift.dto.shiftswap;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ShiftSwapCreateRequest(
    @NotNull(message = "Vui lòng chọn ca muốn đổi hoặc nhường")
    @Positive(message = "Mã phân công phải là số dương")
    Long requesterAssignmentId,

    @Positive(message = "Mã phân công đối ứng phải là số dương")
    Long targetAssignmentId,

    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự")
    String reason
) {
}
