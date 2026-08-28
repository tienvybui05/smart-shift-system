package com.smartshift.mapper;

import com.smartshift.dto.attendance.AttendanceResponse;
import com.smartshift.entity.Attendance;
import com.smartshift.entity.Location;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;

@Component
public class AttendanceMapper {

    public AttendanceResponse toResponse(Attendance attendance) {
        ShiftAssignment assignment = attendance.getShiftAssignment();
        WorkShift workShift = assignment.getWorkShift();
        User employee = assignment.getUser();
        Location location = workShift.getSchedulePeriod().getLocation();
        User approvedBy = attendance.getApprovedBy();

        return new AttendanceResponse(
            attendance.getId(),
            assignment.getId(),
            workShift.getId(),
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            location.getId(),
            location.getName(),
            location.getTimezone(),
            workShift.getStartAt()
                .atZone(ZoneId.of(location.getTimezone()))
                .toLocalDate(),
            workShift.getStartAt(),
            workShift.getEndAt(),
            attendance.getCheckInAt(),
            attendance.getCheckInLatitude(),
            attendance.getCheckInLongitude(),
            attendance.getCheckInAccuracyMeters(),
            attendance.getCheckInDistanceMeters(),
            attendance.getCheckOutAt(),
            attendance.getCheckOutLatitude(),
            attendance.getCheckOutLongitude(),
            attendance.getCheckOutAccuracyMeters(),
            attendance.getCheckOutDistanceMeters(),
            attendance.getBreakMinutes(),
            calculateActualMinutes(attendance),
            attendance.getLateMinutes(),
            attendance.getEarlyLeaveMinutes(),
            attendance.getOvertimeMinutes(),
            attendance.getStatus(),
            attendance.getNote(),
            approvedBy == null ? null : approvedBy.getId(),
            approvedBy == null ? null : approvedBy.getFullName(),
            attendance.getApprovedAt(),
            attendance.getCreatedAt(),
            attendance.getUpdatedAt()
        );
    }

    private Integer calculateActualMinutes(Attendance attendance) {
        if (
            attendance.getCheckInAt() == null
                || attendance.getCheckOutAt() == null
        ) {
            return null;
        }
        long elapsedMinutes = Duration.between(
            attendance.getCheckInAt(),
            attendance.getCheckOutAt()
        ).toMinutes();
        return Math.toIntExact(Math.max(
            0,
            elapsedMinutes - attendance.getBreakMinutes()
        ));
    }
}
