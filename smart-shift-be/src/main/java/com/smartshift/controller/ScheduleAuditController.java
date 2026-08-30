package com.smartshift.controller;

import com.smartshift.dto.audit.ScheduleAuditResponse;
import com.smartshift.enums.ScheduleAuditAction;
import com.smartshift.service.ScheduleAuditService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/schedule-audits")
@RequiredArgsConstructor
public class ScheduleAuditController {

    private final ScheduleAuditService scheduleAuditService;

    @GetMapping
    public ResponseEntity<List<ScheduleAuditResponse>> getAuditLogs(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        @RequestParam(required = false)
        @Positive(message = "Id kỳ xếp lịch phải lớn hơn 0")
        Long schedulePeriodId,
        @RequestParam(required = false) ScheduleAuditAction action,
        Authentication authentication
    ) {
        return ResponseEntity.ok(scheduleAuditService.getAuditLogs(
            authentication.getName(),
            locationId,
            schedulePeriodId,
            action,
            startDate,
            endDate
        ));
    }
}
