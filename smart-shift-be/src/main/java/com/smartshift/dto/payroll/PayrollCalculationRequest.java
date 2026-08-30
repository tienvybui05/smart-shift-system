package com.smartshift.dto.payroll;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record PayrollCalculationRequest(
    @NotNull(message = "Chi nhánh không được để trống")
    @Positive(message = "Id chi nhánh phải lớn hơn 0")
    Long locationId,

    @NotNull(message = "Ngày bắt đầu không được để trống")
    LocalDate startDate,

    @NotNull(message = "Ngày kết thúc không được để trống")
    LocalDate endDate
) {
}
