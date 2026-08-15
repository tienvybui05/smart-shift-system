package com.smartshift.dto.assignment;

import com.smartshift.enums.AssignmentSource;
import com.smartshift.enums.AssignmentStatus;

import java.time.Instant;

public record ShiftAssignmentResponse(
    Long id,
    Long workShiftId,
    Long userId,
    String employeeCode,
    String fullName,
    Long positionId,
    String positionCode,
    String positionName,
    AssignmentSource assignmentSource,
    AssignmentStatus status,
    Long assignedById,
    String assignedByName,
    Instant assignedAt,
    String note
) {
}
