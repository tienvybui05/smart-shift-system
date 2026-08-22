package com.smartshift.mapper;

import com.smartshift.dto.assignment.ShiftAssignmentResponse;
import com.smartshift.dto.assignment.MyWorkScheduleResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentSource;
import com.smartshift.enums.AssignmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class ShiftAssignmentMapper {

    public ShiftAssignment toEntity(
        WorkShift workShift,
        User employee,
        User assignedBy,
        String note
    ) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setWorkShift(workShift);
        assignment.setUser(employee);
        assignment.setPosition(employee.getPosition());
        assignment.setAssignmentSource(AssignmentSource.MANUAL);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedBy(assignedBy);
        assignment.setNote(normalizeNullableText(note));
        return assignment;
    }

    public ShiftAssignment toAutoEntity(
        WorkShift workShift,
        User employee,
        User generatedBy,
        BigDecimal score,
        String note
    ) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setWorkShift(workShift);
        assignment.setUser(employee);
        assignment.setPosition(employee.getPosition());
        assignment.setAssignmentSource(AssignmentSource.AUTO);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setScore(score);
        assignment.setAssignedBy(generatedBy);
        assignment.setNote(normalizeNullableText(note));
        return assignment;
    }

    public ShiftAssignment toClaimEntity(
        WorkShift workShift,
        User employee,
        User reviewedBy,
        String note
    ) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setWorkShift(workShift);
        assignment.setUser(employee);
        assignment.setPosition(employee.getPosition());
        assignment.setAssignmentSource(AssignmentSource.CLAIM);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedBy(reviewedBy);
        assignment.setNote(normalizeNullableText(note));
        return assignment;
    }

    public ShiftAssignmentResponse toResponse(ShiftAssignment assignment) {
        User employee = assignment.getUser();
        User assignedBy = assignment.getAssignedBy();
        return new ShiftAssignmentResponse(
            assignment.getId(),
            assignment.getWorkShift().getId(),
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            assignment.getPosition().getId(),
            assignment.getPosition().getCode(),
            assignment.getPosition().getName(),
            assignment.getAssignmentSource(),
            assignment.getStatus(),
            assignedBy == null ? null : assignedBy.getId(),
            assignedBy == null ? null : assignedBy.getFullName(),
            assignment.getAssignedAt(),
            assignment.getNote()
        );
    }

    public MyWorkScheduleResponse toMyScheduleResponse(
        ShiftAssignment assignment
    ) {
        WorkShift workShift = assignment.getWorkShift();
        SchedulePeriod schedulePeriod = workShift.getSchedulePeriod();
        ZoneId zoneId = ZoneId.of(
            schedulePeriod.getLocation().getTimezone()
        );
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        long workMinutes = Duration.between(
            workShift.getStartAt(),
            workShift.getEndAt()
        ).toMinutes() - workShift.getBreakMinutes();

        return new MyWorkScheduleResponse(
            assignment.getId(),
            workShift.getId(),
            schedulePeriod.getId(),
            schedulePeriod.getName(),
            schedulePeriod.getStatus(),
            schedulePeriod.getLocation().getId(),
            schedulePeriod.getLocation().getName(),
            workShift.getShiftTemplate() == null
                ? "Ca tùy chỉnh"
                : workShift.getShiftTemplate().getName(),
            workShift.getShiftTemplate() == null
                ? null
                : workShift.getShiftTemplate().getColorCode(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            workShift.getBreakMinutes(),
            workMinutes,
            assignment.getPosition().getId(),
            assignment.getPosition().getName(),
            assignment.getAssignmentSource(),
            assignment.getStatus(),
            assignment.getNote()
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
