package com.smartshift.dto.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
    String role,
    String scopeName,
    Instant generatedAt,
    LocalDate today,
    LocalDate weekStart,
    LocalDate weekEnd,
    EmployeeDashboardSummary employee,
    ManagementDashboardSummary management,
    List<DashboardShiftResponse> upcomingShifts
) {
}
