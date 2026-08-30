package com.smartshift.dto.payroll;

import com.smartshift.enums.PayrollStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PayrollRecordResponse(
    Long id,
    Long userId,
    String employeeCode,
    String employeeName,
    String positionName,
    Long locationId,
    String locationName,
    LocalDate periodStart,
    LocalDate periodEnd,
    Integer workedMinutes,
    BigDecimal hourlyRate,
    BigDecimal salaryCoefficient,
    BigDecimal baseAmount,
    BigDecimal bonusAmount,
    String bonusNote,
    BigDecimal totalAmount,
    PayrollStatus status,
    Long calculatedById,
    String calculatedByName,
    Long confirmedById,
    String confirmedByName,
    Instant confirmedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
