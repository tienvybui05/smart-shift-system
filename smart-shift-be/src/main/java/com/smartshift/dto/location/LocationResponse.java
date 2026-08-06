package com.smartshift.dto.location;

import java.time.Instant;

public record LocationResponse(
    Long id,
    String code,
    String name,
    String address,
    String timezone,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}

