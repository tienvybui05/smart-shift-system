package com.smartshift.dto.autoschedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AutoScheduleResponse(
    Long schedulePeriodId,
    Instant generatedAt,
    int activeShifts,
    int requirementsProcessed,
    int totalRequiredEmployees,
    int assignedEmployeesBefore,
    int assignmentsCreated,
    int assignedEmployeesAfter,
    int fullyStaffedShifts,
    int understaffedShifts,
    int unfilledPositions,
    int preferredAssignments,
    BigDecimal coveragePercentage,
    BigDecimal preferencePercentage,
    BigDecimal qualityScore,
    List<String> warnings,
    List<AutoScheduleAssignmentResponse> assignments,
    List<AutoScheduleShortageResponse> shortages
) {

    public AutoScheduleResponse {
        warnings = List.copyOf(warnings);
        assignments = List.copyOf(assignments);
        shortages = List.copyOf(shortages);
    }
}
