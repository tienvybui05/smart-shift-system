package com.smartshift.mapper;

import com.smartshift.dto.shift.ShiftTemplateRequest;
import com.smartshift.dto.shift.ShiftTemplateResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.ShiftTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
public class ShiftTemplateMapper {

    private static final long MINUTES_PER_DAY = 24 * 60;

    public ShiftTemplate toEntity(
        ShiftTemplateRequest request,
        Location location
    ) {
        ShiftTemplate shiftTemplate = new ShiftTemplate();
        updateEntity(request, shiftTemplate, location);
        return shiftTemplate;
    }

    public void updateEntity(
        ShiftTemplateRequest request,
        ShiftTemplate shiftTemplate,
        Location location
    ) {
        shiftTemplate.setLocation(location);
        shiftTemplate.setName(request.name().trim());
        shiftTemplate.setStartTime(request.startTime());
        shiftTemplate.setEndTime(request.endTime());
        shiftTemplate.setBreakMinutes(request.breakMinutes());
        shiftTemplate.setColorCode(normalizeColorCode(request.colorCode()));
        shiftTemplate.setActive(request.active());
    }

    public ShiftTemplateResponse toResponse(ShiftTemplate shiftTemplate) {
        boolean overnight = shiftTemplate.getEndTime()
            .isBefore(shiftTemplate.getStartTime());
        long durationMinutes = Duration.between(
            shiftTemplate.getStartTime(),
            shiftTemplate.getEndTime()
        ).toMinutes();
        if (durationMinutes <= 0) {
            durationMinutes += MINUTES_PER_DAY;
        }

        return new ShiftTemplateResponse(
            shiftTemplate.getId(),
            shiftTemplate.getLocation().getId(),
            shiftTemplate.getLocation().getCode(),
            shiftTemplate.getLocation().getName(),
            shiftTemplate.getName(),
            shiftTemplate.getStartTime(),
            shiftTemplate.getEndTime(),
            shiftTemplate.getBreakMinutes(),
            shiftTemplate.getColorCode(),
            shiftTemplate.isActive(),
            overnight,
            durationMinutes
        );
    }

    private String normalizeColorCode(String colorCode) {
        if (colorCode == null || colorCode.isBlank()) {
            return null;
        }
        return colorCode.trim().toUpperCase(Locale.ROOT);
    }
}
