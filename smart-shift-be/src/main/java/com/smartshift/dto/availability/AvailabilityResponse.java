package com.smartshift.dto.availability;

import com.smartshift.enums.AvailabilityType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityResponse(
    Long id,
    Long userId,
    String employeeCode,
    String fullName,
    Long locationId,
    String locationName,
    Long positionId,
    String positionName,
    LocalDate availableDate,
    LocalTime startTime,
    LocalTime endTime,
    AvailabilityType availabilityType,
    String note,
    Instant createdAt,
    boolean editable
) {
}
