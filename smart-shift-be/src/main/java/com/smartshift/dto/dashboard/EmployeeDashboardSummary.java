package com.smartshift.dto.dashboard;

import java.math.BigDecimal;

public record EmployeeDashboardSummary(
    long shiftsThisWeek,
    long scheduledMinutesThisWeek,
    long workedMinutesThisMonth,
    int onTimeRate,
    long pendingRequests,
    long availableOpenShifts,
    long unreadNotifications,
    BigDecimal estimatedPayThisMonth
) {
}
