package com.smartshift.dto.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScheduleBudgetResponse(Long schedulePeriodId, Long locationId, String locationName,
    String timezone, BigDecimal weeklyBudget, BigDecimal periodCost, boolean overBudget,
    List<Week> weeks) {
    public ScheduleBudgetResponse { weeks=List.copyOf(weeks); }
    public record Week(LocalDate startDate, LocalDate endDate, BigDecimal periodCost,
        BigDecimal otherPeriodsCost, BigDecimal totalCost, BigDecimal excess,
        BigDecimal remaining, BigDecimal utilizationPercent, int assignedShifts) {}
}
