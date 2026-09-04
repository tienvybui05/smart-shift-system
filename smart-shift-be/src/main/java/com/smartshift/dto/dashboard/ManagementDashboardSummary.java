package com.smartshift.dto.dashboard;

import java.math.BigDecimal;

public record ManagementDashboardSummary(
    long activeEmployees,
    long inactiveEmployees,
    long shiftsToday,
    long staffedShiftsToday,
    long understaffedShiftsToday,
    long checkedInToday,
    long lateToday,
    long absentToday,
    long missingCheckInsToday,
    long pendingTimeOff,
    long pendingShiftSwaps,
    long pendingOpenShiftClaims,
    long scheduledMinutesThisWeek,
    long workedMinutesThisWeek,
    BigDecimal estimatedLaborCostThisMonth
) {
}
