package com.smartshift.dto.position;

public record PositionResponse(
    Long id,
    String code,
    String name,
    String description,
    boolean active
) {
}

