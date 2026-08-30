package com.smartshift.dto.requirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveShiftRequirementsRequest(
    @NotNull(message = "Danh sách nhu cầu không được để trống")
    List<@Valid ShiftRequirementItemRequest> requirements,

    @NotNull(message = "Phạm vi áp dụng không được để trống")
    Boolean applyToSameTemplate,

    @Size(max = 500, message = "Lý do thay đổi không được vượt quá 500 ký tự")
    String changeReason
) {
}
