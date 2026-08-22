package com.smartshift.dto.openshift;

import com.smartshift.enums.AvailabilityType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AvailableOpenShiftResponse(
    Long workShiftId,
    Long schedulePeriodId,
    String schedulePeriodName,
    Long locationId,
    String locationName,
    String shiftName,
    String colorCode,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean endsNextDay,
    short breakMinutes,
    long workMinutes,
    Long positionId,
    String positionName,
    int minimumEmployees,
    int assignedEmployees,
    int missingEmployees,
    long pendingClaims,
    AvailabilityType availabilityType,
    BigDecimal projectedDailyHours,
    BigDecimal maximumDailyHours,
    BigDecimal projectedWeeklyHours,
    BigDecimal maximumWeeklyHours,
    Long myPendingClaimId
) {
}
