package com.smartshift.service;

import com.smartshift.dto.attendance.AttendanceApprovalRequest;
import com.smartshift.dto.attendance.AttendanceGpsRequest;
import com.smartshift.dto.attendance.AttendanceResponse;
import com.smartshift.enums.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    AttendanceResponse checkIn(
        String username,
        AttendanceGpsRequest request,
        String clientIp
    );

    default AttendanceResponse checkIn(
        String username,
        AttendanceGpsRequest request
    ) {
        return checkIn(username, request, null);
    }

    AttendanceResponse checkOut(
        String username,
        AttendanceGpsRequest request,
        String clientIp
    );

    default AttendanceResponse checkOut(
        String username,
        AttendanceGpsRequest request
    ) {
        return checkOut(username, request, null);
    }

    List<AttendanceResponse> getMyAttendances(
        String username,
        LocalDate startDate,
        LocalDate endDate
    );

    List<AttendanceResponse> getAttendances(
        String username,
        LocalDate startDate,
        LocalDate endDate,
        Long locationId,
        AttendanceStatus status
    );

    AttendanceResponse approveAttendance(
        String username,
        Long attendanceId,
        AttendanceApprovalRequest request
    );
}
