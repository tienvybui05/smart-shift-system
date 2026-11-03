package com.smartshift.controller;

import com.smartshift.dto.attendance.AttendanceApprovalRequest;
import com.smartshift.dto.attendance.AttendanceGpsRequest;
import com.smartshift.dto.attendance.AttendanceResponse;
import com.smartshift.enums.AttendanceStatus;
import com.smartshift.service.AttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(
        @Valid @RequestBody AttendanceGpsRequest request,
        Authentication authentication,
        jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(
            attendanceService.checkIn(authentication.getName(), request, clientIp)
        );
    }

    @PostMapping("/check-out")
    public ResponseEntity<AttendanceResponse> checkOut(
        @Valid @RequestBody AttendanceGpsRequest request,
        Authentication authentication,
        jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        return ResponseEntity.ok(
            attendanceService.checkOut(authentication.getName(), request, clientIp)
        );
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/me")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendances(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            attendanceService.getMyAttendances(
                authentication.getName(),
                startDate,
                endDate
            )
        );
    }

    @GetMapping
    public ResponseEntity<List<AttendanceResponse>> getAttendances(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        @RequestParam(required = false) AttendanceStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            attendanceService.getAttendances(
                authentication.getName(),
                startDate,
                endDate,
                locationId,
                status
            )
        );
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<AttendanceResponse> approveAttendance(
        @PathVariable
        @Positive(message = "Id chấm công phải lớn hơn 0")
        Long id,
        @Valid @RequestBody AttendanceApprovalRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            attendanceService.approveAttendance(
                authentication.getName(),
                id,
                request
            )
        );
    }
}
