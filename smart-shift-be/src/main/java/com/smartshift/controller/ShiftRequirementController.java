package com.smartshift.controller;

import com.smartshift.dto.requirement.SaveShiftRequirementsRequest;
import com.smartshift.dto.requirement.ShiftRequirementSummaryResponse;
import com.smartshift.service.SchedulingAccessService;
import com.smartshift.service.ShiftRequirementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/shift-requirements")
@RequiredArgsConstructor
public class ShiftRequirementController {

    private final ShiftRequirementService shiftRequirementService;
    private final SchedulingAccessService schedulingAccessService;

    @GetMapping
    public ResponseEntity<ShiftRequirementSummaryResponse> getRequirements(
        @RequestParam
        @Positive(message = "Id ca làm phải lớn hơn 0")
        Long workShiftId,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(
            authentication.getName(),
            workShiftId
        );
        return ResponseEntity.ok(
            shiftRequirementService.getRequirements(workShiftId)
        );
    }

    @PutMapping("/work-shifts/{workShiftId}")
    public ResponseEntity<ShiftRequirementSummaryResponse> saveRequirements(
        @PathVariable Long workShiftId,
        @Valid @RequestBody SaveShiftRequirementsRequest request,
        Authentication authentication
    ) {
        schedulingAccessService.requireWorkShift(
            authentication.getName(),
            workShiftId
        );
        return ResponseEntity.ok(
            shiftRequirementService.saveRequirements(workShiftId, request)
        );
    }
}
