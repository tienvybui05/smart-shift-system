package com.smartshift.dto.payroll;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayrollBonusRequest(
    @NotNull(message = "Tiền thưởng không được để trống")
    @DecimalMin(value = "0.0", message = "Tiền thưởng không được âm")
    @Digits(integer = 13, fraction = 2, message = "Tiền thưởng không đúng định dạng")
    BigDecimal bonusAmount,

    @Size(max = 500, message = "Ghi chú thưởng không được vượt quá 500 ký tự")
    String bonusNote
) {
}
