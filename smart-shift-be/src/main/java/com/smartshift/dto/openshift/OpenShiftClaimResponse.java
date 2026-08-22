package com.smartshift.dto.openshift;

import com.smartshift.enums.OpenShiftClaimStatus;
import com.smartshift.enums.SchedulePeriodStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record OpenShiftClaimResponse(
    Long id,
    Long workShiftId,
    String shiftName,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean endsNextDay,
    Long schedulePeriodId,
    String schedulePeriodName,
    SchedulePeriodStatus schedulePeriodStatus,
    Long locationId,
    String locationName,
    Long userId,
    String employeeCode,
    String userFullName,
    Long positionId,
    String positionName,
    OpenShiftClaimStatus status,
    String reason,
    Long reviewedById,
    String reviewedByName,
    Instant reviewedAt,
    String reviewerNote,
    Long assignmentId,
    Instant createdAt,
    boolean cancellable
) {
}
