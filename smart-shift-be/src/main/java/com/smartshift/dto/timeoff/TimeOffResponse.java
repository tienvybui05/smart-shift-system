package com.smartshift.dto.timeoff;

import com.smartshift.enums.LeaveType;
import com.smartshift.enums.TimeOffStatus;

import java.time.Instant;

public record TimeOffResponse(
    Long id,
    Long userId,
    String employeeCode,
    String userFullName,
    Long locationId,
    String locationName,
    Instant startAt,
    Instant endAt,
    LeaveType leaveType,
    String reason,
    TimeOffStatus status,
    Long approvedById,
    String approvedByName,
    Instant approvedAt,
    Instant createdAt
) {
}
