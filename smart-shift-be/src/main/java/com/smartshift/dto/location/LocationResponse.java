package com.smartshift.dto.location;

import java.math.BigDecimal;
import java.time.Instant;

public record LocationResponse(
    Long id,
    String code,
    String name,
    String address,
    String timezone,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer attendanceRadiusMeters,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
