package com.smartshift.dto.assignment;

import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.List;

public record AssignmentCandidateResponse(
    Long userId,
    String employeeCode,
    String fullName,
    EmploymentType employmentType,
    AvailabilityType availabilityType,
    boolean assigned,
    boolean eligible,
    List<String> ineligibilityReasons,
    BigDecimal projectedDailyHours,
    BigDecimal maxDailyHours,
    BigDecimal projectedWeeklyHours,
    BigDecimal maxWeeklyHours
) {
}
