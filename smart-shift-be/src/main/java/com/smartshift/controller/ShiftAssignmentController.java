package com.smartshift.controller;

import com.smartshift.dto.assignment.AssignmentCandidateResponse;
import com.smartshift.dto.assignment.MyWorkScheduleResponse;
import com.smartshift.dto.assignment.ShiftAssignmentRequest;
import com.smartshift.dto.assignment.ShiftAssignmentSummaryResponse;
import com.smartshift.service.SchedulingAccessService;
import com.smartshift.service.ShiftAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/shift-assignments")
@RequiredArgsConstructor
public class ShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;
    private final SchedulingAccessService schedulingAccessService;

    @GetMapping("/me")
    public ResponseEntity<List<MyWorkScheduleResponse>> getMySchedule(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            shiftAssignmentService.getMySchedule(
                authentication.getName(),
                startDate,
                endDate
            )
        );
    }

    @GetMapping("/work-shifts/{workShiftId}")
    public ResponseEntity<ShiftAssignmentSummaryResponse> getSummary(
        @PathVariable Long workShiftId,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(
            authentication.getName(),
            workShiftId
        );
        return ResponseEntity.ok(
            shiftAssignmentService.getSummary(workShiftId)
        );
    }

    @GetMapping("/work-shifts/{workShiftId}/candidates")
    public ResponseEntity<List<AssignmentCandidateResponse>> getCandidates(
        @PathVariable Long workShiftId,
        @RequestParam
        @Positive(message = "Id vị trí phải lớn hơn 0")
        Long positionId,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(
            authentication.getName(),
            workShiftId
        );
        return ResponseEntity.ok(
            shiftAssignmentService.getCandidates(workShiftId, positionId)
        );
    }

    @PostMapping("/work-shifts/{workShiftId}")
    public ResponseEntity<ShiftAssignmentSummaryResponse> assignEmployee(
        @PathVariable Long workShiftId,
        @Valid @RequestBody ShiftAssignmentRequest request,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(
            authentication.getName(),
            workShiftId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
            shiftAssignmentService.assignEmployee(
                workShiftId,
                request,
                authentication.getName()
            )
        );
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<ShiftAssignmentSummaryResponse> removeAssignment(
        @PathVariable Long assignmentId,
        Authentication authentication
    ) {
        schedulingAccessService.requireAssignment(
            authentication.getName(),
            assignmentId
        );
        return ResponseEntity.ok(
            shiftAssignmentService.removeAssignment(
                assignmentId,
                authentication.getName()
            )
        );
    }
}
