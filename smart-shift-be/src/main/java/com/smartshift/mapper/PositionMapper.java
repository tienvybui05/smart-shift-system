package com.smartshift.mapper;

import com.smartshift.dto.position.PositionRequest;
import com.smartshift.dto.position.PositionResponse;
import com.smartshift.entity.Position;
import org.springframework.stereotype.Component;

@Component
public class PositionMapper {

    public Position toEntity(PositionRequest request) {
        Position position = new Position();
        updateEntity(request, position);
        return position;
    }

    public void updateEntity(PositionRequest request, Position position) {
        position.setCode(request.code().trim());
        position.setName(request.name().trim());
        position.setDescription(normalizeNullableText(request.description()));
        position.setActive(request.active());
    }

    public PositionResponse toResponse(Position position) {
        return new PositionResponse(
            position.getId(),
            position.getCode(),
            position.getName(),
            position.getDescription(),
            position.isActive()
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

