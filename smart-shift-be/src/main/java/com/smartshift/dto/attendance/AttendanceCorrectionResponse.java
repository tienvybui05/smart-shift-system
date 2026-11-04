package com.smartshift.dto.attendance;
import com.smartshift.enums.AttendanceCorrectionStatus;
import java.time.*;
public record AttendanceCorrectionResponse(
    Long id, Long shiftAssignmentId, Long employeeId, String employeeCode, String employeeName,
    Long locationId, String locationName, String timezone, LocalDate workDate,
    Instant shiftStartAt, Instant shiftEndAt,
    Instant checkInAt, Instant checkOutAt, Short breakMinutes, String reason,
    AttendanceCorrectionStatus status, String beforeData, String reviewedByName,
    Instant reviewedAt, String reviewNote, Instant createdAt
) {}

