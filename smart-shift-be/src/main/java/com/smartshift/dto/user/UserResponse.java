package com.smartshift.dto.user;

import com.smartshift.enums.EmploymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record UserResponse(
    Long id,
    String employeeCode,
    String username,
    String fullName,
    String email,
    String phoneNumber,
    Long roleId,
    String roleName,
    Long locationId,
    String locationCode,
    String locationName,
    Long positionId,
    String positionCode,
    String positionName,
    EmploymentType employmentType,
    LocalDate hireDate,
    BigDecimal minHoursPerWeek,
    BigDecimal maxHoursPerWeek,
    BigDecimal maxHoursPerDay,
    BigDecimal minRestHours,
    Short maxConsecutiveDays,
    BigDecimal hourlyRate,
    BigDecimal salaryCoefficient,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
