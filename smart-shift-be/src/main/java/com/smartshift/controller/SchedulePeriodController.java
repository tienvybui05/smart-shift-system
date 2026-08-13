package com.smartshift.controller;

import com.smartshift.dto.schedule.SchedulePeriodRequest;
import com.smartshift.dto.schedule.SchedulePeriodResponse;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.service.SchedulePeriodService;
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

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/schedule-periods")
@RequiredArgsConstructor
public class SchedulePeriodController {

    private final SchedulePeriodService schedulePeriodService;

    @GetMapping
    public ResponseEntity<List<SchedulePeriodResponse>> getSchedulePeriods(
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        @RequestParam(required = false) SchedulePeriodStatus status
    ) {
        return ResponseEntity.ok(
            schedulePeriodService.getSchedulePeriods(locationId, status)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SchedulePeriodResponse> getSchedulePeriodById(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(
            schedulePeriodService.getSchedulePeriodById(id)
        );
    }

    @PostMapping
    public ResponseEntity<SchedulePeriodResponse> createSchedulePeriod(
        @Valid @RequestBody SchedulePeriodRequest request,
        Authentication authentication
    ) {
        SchedulePeriodResponse response = schedulePeriodService
            .createSchedulePeriod(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SchedulePeriodResponse> updateSchedulePeriod(
        @PathVariable Long id,
        @Valid @RequestBody SchedulePeriodRequest request
    ) {
        return ResponseEntity.ok(
            schedulePeriodService.updateSchedulePeriod(id, request)
        );
    }
}
