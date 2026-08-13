package com.smartshift.dto.schedule;

import com.smartshift.enums.SchedulePeriodStatus;

import java.time.Instant;
import java.time.LocalDate;

public record SchedulePeriodResponse(
    Long id,
    Long locationId,
    String locationCode,
    String locationName,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    long totalDays,
    SchedulePeriodStatus status,
    boolean editable,
    Long createdById,
    String createdByName,
    Long publishedById,
    String publishedByName,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
