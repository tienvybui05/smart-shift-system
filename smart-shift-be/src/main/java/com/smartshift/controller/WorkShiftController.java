package com.smartshift.controller;

import com.smartshift.dto.workshift.GenerateWorkShiftsRequest;
import com.smartshift.dto.workshift.GenerateWorkShiftsResponse;
import com.smartshift.dto.workshift.WorkShiftRequest;
import com.smartshift.dto.workshift.WorkShiftResponse;
import com.smartshift.dto.workshift.WorkShiftStatusRequest;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.service.SchedulingAccessService;
import com.smartshift.service.WorkShiftService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/work-shifts")
@RequiredArgsConstructor
public class WorkShiftController {

    private final WorkShiftService workShiftService;
    private final SchedulingAccessService schedulingAccessService;

    @GetMapping
    public ResponseEntity<List<WorkShiftResponse>> getWorkShifts(
        @RequestParam
        @Positive(message = "Id kỳ xếp lịch phải lớn hơn 0")
        Long schedulePeriodId,
        @RequestParam(required = false) WorkShiftStatus status,
        Authentication authentication
    ) {
        schedulingAccessService.requireSchedulePeriod(
            authentication.getName(),
            schedulePeriodId
        );
        return ResponseEntity.ok(
            workShiftService.getWorkShifts(schedulePeriodId, status)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkShiftResponse> getWorkShiftById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(authentication.getName(), id);
        return ResponseEntity.ok(workShiftService.getWorkShiftById(id));
    }

    @PostMapping
    public ResponseEntity<WorkShiftResponse> createWorkShift(
        @Valid @RequestBody WorkShiftRequest request,
        Authentication authentication
    ) {
        schedulingAccessService.requireSchedulePeriod(
            authentication.getName(),
            request.schedulePeriodId()
        );
        schedulingAccessService.requireShiftTemplate(
            authentication.getName(),
            request.shiftTemplateId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(workShiftService.createWorkShift(request));
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateWorkShiftsResponse> generateWorkShifts(
        @Valid @RequestBody GenerateWorkShiftsRequest request,
        Authentication authentication
    ) {
        schedulingAccessService.requireSchedulePeriod(
            authentication.getName(),
            request.schedulePeriodId()
        );
        schedulingAccessService.requireShiftTemplates(
            authentication.getName(),
            request.shiftTemplateIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(workShiftService.generateWorkShifts(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkShiftResponse> updateWorkShift(
        @PathVariable Long id,
        @Valid @RequestBody WorkShiftRequest request,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(authentication.getName(), id);
        schedulingAccessService.requireSchedulePeriod(
            authentication.getName(),
            request.schedulePeriodId()
        );
        schedulingAccessService.requireShiftTemplate(
            authentication.getName(),
            request.shiftTemplateId()
        );
        return ResponseEntity.ok(
            workShiftService.updateWorkShift(id, request)
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkShiftResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody WorkShiftStatusRequest request,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(authentication.getName(), id);
        return ResponseEntity.ok(
            workShiftService.updateStatus(id, request.status())
        );
    }
}
