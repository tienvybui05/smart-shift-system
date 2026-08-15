package com.smartshift.dto.assignment;

import com.smartshift.enums.WorkShiftStatus;

import java.util.List;

public record ShiftAssignmentSummaryResponse(
    Long workShiftId,
    WorkShiftStatus workShiftStatus,
    int totalAssignedEmployees,
    int totalMinEmployees,
    int totalMaxEmployees,
    boolean minimumStaffed,
    boolean editable,
    List<AssignmentRequirementProgressResponse> requirements,
    List<ShiftAssignmentResponse> assignments
) {
}
