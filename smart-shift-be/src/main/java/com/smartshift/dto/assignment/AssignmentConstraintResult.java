package com.smartshift.dto.assignment;

import com.smartshift.enums.AvailabilityType;

import java.math.BigDecimal;
import java.util.List;

public record AssignmentConstraintResult(
    AvailabilityType availabilityType,
    List<String> violations,
    BigDecimal projectedDailyHours,
    BigDecimal projectedWeeklyHours
) {

    public AssignmentConstraintResult {
        violations = List.copyOf(violations);
    }

    public boolean eligible() {
        return violations.isEmpty();
    }
}
