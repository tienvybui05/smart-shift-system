package com.smartshift.controller;

import com.smartshift.dto.shiftswap.ShiftSwapCandidateResponse;
import com.smartshift.dto.shiftswap.ShiftSwapCreateRequest;
import com.smartshift.dto.shiftswap.ShiftSwapRequestResponse;
import com.smartshift.dto.shiftswap.ShiftSwapRespondRequest;
import com.smartshift.dto.shiftswap.ShiftSwapReviewRequest;
import com.smartshift.enums.ShiftSwapStatus;
import com.smartshift.service.ShiftSwapService;
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
@RequestMapping("/api/shift-swap-requests")
@RequiredArgsConstructor
public class ShiftSwapController {

    private final ShiftSwapService shiftSwapService;

    @GetMapping("/me")
    public ResponseEntity<List<ShiftSwapRequestResponse>> getMyRequests(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            shiftSwapService.getMyRequests(authentication.getName())
        );
    }

    @GetMapping("/available")
    public ResponseEntity<List<ShiftSwapRequestResponse>> getAvailableGiveaways(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            shiftSwapService.getAvailableGiveaways(authentication.getName())
        );
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<ShiftSwapCandidateResponse>> getSwapCandidates(
        @RequestParam @Positive Long requesterAssignmentId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(shiftSwapService.getSwapCandidates(
            authentication.getName(),
            requesterAssignmentId
        ));
    }

    @PostMapping
    public ResponseEntity<ShiftSwapRequestResponse> createRequest(
        @Valid @RequestBody ShiftSwapCreateRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            shiftSwapService.createRequest(authentication.getName(), request)
        );
    }

    @PatchMapping("/me/{id}/respond")
    public ResponseEntity<ShiftSwapRequestResponse> respondToRequest(
        @PathVariable @Positive Long id,
        @Valid @RequestBody ShiftSwapRespondRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(shiftSwapService.respondToRequest(
            authentication.getName(),
            id,
            request
        ));
    }

    @PatchMapping("/me/{id}/cancel")
    public ResponseEntity<ShiftSwapRequestResponse> cancelRequest(
        @PathVariable @Positive Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(shiftSwapService.cancelRequest(
            authentication.getName(),
            id
        ));
    }

    @GetMapping
    public ResponseEntity<List<ShiftSwapRequestResponse>> getRequestsForReview(
        @RequestParam(required = false) ShiftSwapStatus status,
        @RequestParam(required = false) @Positive Long locationId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(shiftSwapService.getRequestsForReview(
            authentication.getName(),
            status,
            locationId
        ));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<ShiftSwapRequestResponse> reviewRequest(
        @PathVariable @Positive Long id,
        @Valid @RequestBody ShiftSwapReviewRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(shiftSwapService.reviewRequest(
            authentication.getName(),
            id,
            request
        ));
    }
}
