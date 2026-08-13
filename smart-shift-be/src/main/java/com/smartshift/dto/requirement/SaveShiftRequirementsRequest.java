package com.smartshift.dto.requirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveShiftRequirementsRequest(
    @NotNull(message = "Danh sách nhu cầu không được để trống")
    List<@Valid ShiftRequirementItemRequest> requirements,

    @NotNull(message = "Phạm vi áp dụng không được để trống")
    Boolean applyToSameTemplate
) {
}
