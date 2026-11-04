package com.smartshift.dto.schedule;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** null clears the optional weekly budget; zero is an explicitly configured zero budget. */
public record WeeklyBudgetRequest(
    @DecimalMin(value="0.0", message="Ngân sách tuần không được âm")
    @Digits(integer=13, fraction=2, message="Ngân sách tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    BigDecimal weeklyBudget
) {}
