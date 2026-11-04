package com.smartshift.dto.schedule;

import java.math.BigDecimal;

public record WeeklyBudgetSettings(Long locationId, String locationName, String timezone,
                                   BigDecimal weeklyBudget) {}
