package com.smartshift.dto.requirement;

import java.util.List;

public record ShiftRequirementSummaryResponse(
    Long workShiftId,
    int totalMinEmployees,
    int totalMaxEmployees,
    int affectedShiftCount,
    List<ShiftRequirementResponse> requirements
) {
}
