package com.smartshift.dto.workshift;

import com.smartshift.enums.WorkShiftStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record WorkShiftResponse(
    Long id,
    Long schedulePeriodId,
    String schedulePeriodName,
    Long locationId,
    String locationCode,
    String locationName,
    Long shiftTemplateId,
    String shiftTemplateName,
    String colorCode,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    Instant startAt,
    Instant endAt,
    Short breakMinutes,
    long durationMinutes,
    boolean overnight,
    WorkShiftStatus status,
    String note,
    Instant createdAt,
    Instant updatedAt
) {
}
