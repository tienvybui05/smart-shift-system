package com.smartshift.dto.assignment;

import java.math.BigDecimal;
import java.util.List;

public record CalendarPlacementPreview(boolean eligible,List<String> violations,List<String> warnings,
    BigDecimal projectedDailyHours,BigDecimal projectedWeeklyHours) {
    public CalendarPlacementPreview { violations=List.copyOf(violations); warnings=List.copyOf(warnings); }
}
