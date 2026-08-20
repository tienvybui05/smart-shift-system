package com.smartshift.controller;

import com.smartshift.dto.timeoff.TimeOffRequest;
import com.smartshift.dto.timeoff.TimeOffResponse;
import com.smartshift.dto.timeoff.TimeOffReviewRequest;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.service.TimeOffService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/time-off-requests")
@RequiredArgsConstructor
public class TimeOffController {

    private final TimeOffService timeOffService;

    @GetMapping("/me")
    public ResponseEntity<List<TimeOffResponse>> getMyRequests(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            timeOffService.getMyRequests(authentication.getName())
        );
    }

    @PostMapping("/me")
    public ResponseEntity<TimeOffResponse> createMyRequest(
        @Valid @RequestBody TimeOffRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            timeOffService.createMyRequest(authentication.getName(), request)
        );
    }

    @PatchMapping("/me/{id}/cancel")
    public ResponseEntity<TimeOffResponse> cancelMyRequest(
        @PathVariable
        @Positive(message = "Id đơn nghỉ phải lớn hơn 0")
        Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            timeOffService.cancelMyRequest(id, authentication.getName())
        );
    }

    @GetMapping
    public ResponseEntity<List<TimeOffResponse>> getRequests(
        @RequestParam(required = false) TimeOffStatus status,
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            timeOffService.getRequests(
                status,
                locationId,
                authentication.getName()
            )
        );
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<TimeOffResponse> reviewRequest(
        @PathVariable
        @Positive(message = "Id đơn nghỉ phải lớn hơn 0")
        Long id,
        @Valid @RequestBody TimeOffReviewRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            timeOffService.reviewRequest(
                id,
                request,
                authentication.getName()
            )
        );
    }
}
