package com.smartshift.dto.audit;

import com.smartshift.enums.ScheduleAuditAction;
import com.smartshift.enums.ScheduleAuditTargetType;

import java.time.Instant;

public record ScheduleAuditResponse(
    Long id,
    Long locationId,
    String locationName,
    Long schedulePeriodId,
    String schedulePeriodName,
    Long workShiftId,
    ScheduleAuditAction action,
    ScheduleAuditTargetType targetType,
    Long targetId,
    Long actorId,
    String actorName,
    String reason,
    String beforeData,
    String afterData,
    Instant createdAt
) {
}
