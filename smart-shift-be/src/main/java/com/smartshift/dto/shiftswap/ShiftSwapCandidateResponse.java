package com.smartshift.dto.shiftswap;

import java.util.List;

public record ShiftSwapCandidateResponse(
    Long targetAssignmentId,
    Long targetUserId,
    String employeeCode,
    String fullName,
    ShiftSwapAssignmentResponse targetAssignment,
    boolean eligible,
    List<String> reasons
) {

    public ShiftSwapCandidateResponse {
        reasons = List.copyOf(reasons);
    }
}
