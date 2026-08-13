package com.smartshift.dto.requirement;

public record ShiftRequirementResponse(
    Long id,
    Long workShiftId,
    Long positionId,
    String positionCode,
    String positionName,
    Short minEmployees,
    Short maxEmployees,
    Short priority
) {
}
