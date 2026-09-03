package com.smartshift.dto.shiftswap;

import com.smartshift.enums.ShiftSwapStatus;
import com.smartshift.enums.ShiftSwapType;

import java.time.Instant;

public record ShiftSwapRequestResponse(
    Long id,
    ShiftSwapType type,
    ShiftSwapStatus status,
    Long requesterUserId,
    String requesterEmployeeCode,
    String requesterFullName,
    ShiftSwapAssignmentResponse requesterAssignment,
    Long targetUserId,
    String targetEmployeeCode,
    String targetFullName,
    ShiftSwapAssignmentResponse targetAssignment,
    String reason,
    String responseNote,
    Instant respondedAt,
    Long approvedById,
    String approvedByName,
    Instant approvedAt,
    String reviewerNote,
    Instant createdAt,
    Instant updatedAt,
    boolean cancellable,
    boolean respondable,
    boolean reviewable
) {
}
