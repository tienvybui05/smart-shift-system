package com.smartshift.mapper;

import com.smartshift.dto.availability.AvailabilityRequest;
import com.smartshift.dto.availability.AvailabilityResponse;
import com.smartshift.entity.EmployeeAvailability;
import com.smartshift.entity.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class AvailabilityMapper {

    public EmployeeAvailability toEntity(
        AvailabilityRequest request,
        User user
    ) {
        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setUser(user);
        updateEntity(request, availability);
        return availability;
    }

    public void updateEntity(
        AvailabilityRequest request,
        EmployeeAvailability availability
    ) {
        availability.setAvailableDate(request.availableDate());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setAvailabilityType(request.availabilityType());
        availability.setNote(normalizeNullableText(request.note()));
    }

    public AvailabilityResponse toResponse(
        EmployeeAvailability availability
    ) {
        User user = availability.getUser();
        return new AvailabilityResponse(
            availability.getId(),
            user.getId(),
            user.getEmployeeCode(),
            user.getFullName(),
            user.getLocation().getId(),
            user.getLocation().getName(),
            user.getPosition().getId(),
            user.getPosition().getName(),
            availability.getAvailableDate(),
            availability.getStartTime(),
            availability.getEndTime(),
            availability.getAvailabilityType(),
            availability.getNote(),
            availability.getCreatedAt(),
            !availability.getAvailableDate().isBefore(
                LocalDate.now(ZoneId.of(user.getLocation().getTimezone()))
            )
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
