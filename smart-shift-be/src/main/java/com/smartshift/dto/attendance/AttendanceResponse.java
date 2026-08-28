package com.smartshift.dto.attendance;

import com.smartshift.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AttendanceResponse(
    Long id,
    Long shiftAssignmentId,
    Long workShiftId,
    Long employeeId,
    String employeeCode,
    String employeeName,
    Long locationId,
    String locationName,
    String timezone,
    LocalDate workDate,
    Instant shiftStartAt,
    Instant shiftEndAt,
    Instant checkInAt,
    BigDecimal checkInLatitude,
    BigDecimal checkInLongitude,
    BigDecimal checkInAccuracyMeters,
    BigDecimal checkInDistanceMeters,
    Instant checkOutAt,
    BigDecimal checkOutLatitude,
    BigDecimal checkOutLongitude,
    BigDecimal checkOutAccuracyMeters,
    BigDecimal checkOutDistanceMeters,
    Short breakMinutes,
    Integer actualMinutes,
    Integer lateMinutes,
    Integer earlyLeaveMinutes,
    Integer overtimeMinutes,
    AttendanceStatus status,
    String note,
    Long approvedById,
    String approvedByName,
    Instant approvedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
