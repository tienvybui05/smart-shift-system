package com.smartshift.mapper;

import com.smartshift.dto.workshift.WorkShiftRequest;
import com.smartshift.dto.workshift.WorkShiftResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftTemplate;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.WorkShiftStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class WorkShiftMapper {

    public WorkShift toEntity(
        WorkShiftRequest request,
        SchedulePeriod schedulePeriod,
        ShiftTemplate shiftTemplate
    ) {
        WorkShift workShift = new WorkShift();
        workShift.setStatus(WorkShiftStatus.OPEN);
        updateEntity(request, workShift, schedulePeriod, shiftTemplate);
        return workShift;
    }

    public WorkShift fromTemplate(
        SchedulePeriod schedulePeriod,
        ShiftTemplate shiftTemplate,
        LocalDate workDate
    ) {
        WorkShift workShift = new WorkShift();
        workShift.setSchedulePeriod(schedulePeriod);
        workShift.setShiftTemplate(shiftTemplate);
        workShift.setBreakMinutes(shiftTemplate.getBreakMinutes());
        workShift.setStatus(WorkShiftStatus.OPEN);
        setTimeRange(
            workShift,
            workDate,
            shiftTemplate.getStartTime(),
            shiftTemplate.getEndTime(),
            schedulePeriod.getLocation().getTimezone()
        );
        return workShift;
    }

    public void updateEntity(
        WorkShiftRequest request,
        WorkShift workShift,
        SchedulePeriod schedulePeriod,
        ShiftTemplate shiftTemplate
    ) {
        workShift.setSchedulePeriod(schedulePeriod);
        workShift.setShiftTemplate(shiftTemplate);
        workShift.setBreakMinutes(request.breakMinutes());
        workShift.setNote(normalizeNullableText(request.note()));
        setTimeRange(
            workShift,
            request.workDate(),
            request.startTime(),
            request.endTime(),
            schedulePeriod.getLocation().getTimezone()
        );
    }

    public WorkShiftResponse toResponse(WorkShift workShift) {
        SchedulePeriod schedulePeriod = workShift.getSchedulePeriod();
        ShiftTemplate shiftTemplate = workShift.getShiftTemplate();
        ZoneId zoneId = ZoneId.of(schedulePeriod.getLocation().getTimezone());
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);

        return new WorkShiftResponse(
            workShift.getId(),
            schedulePeriod.getId(),
            schedulePeriod.getName(),
            schedulePeriod.getLocation().getId(),
            schedulePeriod.getLocation().getCode(),
            schedulePeriod.getLocation().getName(),
            shiftTemplate == null ? null : shiftTemplate.getId(),
            shiftTemplate == null ? "Ca tùy chỉnh" : shiftTemplate.getName(),
            shiftTemplate == null ? null : shiftTemplate.getColorCode(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            workShift.getStartAt(),
            workShift.getEndAt(),
            workShift.getBreakMinutes(),
            Duration.between(workShift.getStartAt(), workShift.getEndAt()).toMinutes(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            workShift.getStatus(),
            workShift.getNote(),
            workShift.getCreatedAt(),
            workShift.getUpdatedAt()
        );
    }

    private void setTimeRange(
        WorkShift workShift,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        String timezone
    ) {
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime start = ZonedDateTime.of(workDate, startTime, zoneId);
        LocalDate endDate = endTime.isAfter(startTime)
            ? workDate
            : workDate.plusDays(1);
        ZonedDateTime end = ZonedDateTime.of(endDate, endTime, zoneId);
        workShift.setStartAt(start.toInstant());
        workShift.setEndAt(end.toInstant());
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
