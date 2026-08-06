package com.smartshift.mapper;

import com.smartshift.dto.location.LocationRequest;
import com.smartshift.dto.location.LocationResponse;
import com.smartshift.entity.Location;
import org.springframework.stereotype.Component;

@Component
public class LocationMapper {

    public Location toEntity(LocationRequest request) {
        Location location = new Location();
        updateEntity(request, location);
        return location;
    }

    public void updateEntity(LocationRequest request, Location location) {
        location.setCode(request.code().trim());
        location.setName(request.name().trim());
        location.setAddress(normalizeNullableText(request.address()));
        location.setTimezone(request.timezone().trim());
        location.setActive(request.active());
    }

    public LocationResponse toResponse(Location location) {
        return new LocationResponse(
            location.getId(),
            location.getCode(),
            location.getName(),
            location.getAddress(),
            location.getTimezone(),
            location.isActive(),
            location.getCreatedAt(),
            location.getUpdatedAt()
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

