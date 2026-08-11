package com.smartshift.dto.shift;

import java.time.LocalTime;

public record ShiftTemplateResponse(
    Long id,
    Long locationId,
    String locationCode,
    String locationName,
    String name,
    LocalTime startTime,
    LocalTime endTime,
    Short breakMinutes,
    String colorCode,
    boolean active,
    boolean overnight,
    long durationMinutes
) {
}
