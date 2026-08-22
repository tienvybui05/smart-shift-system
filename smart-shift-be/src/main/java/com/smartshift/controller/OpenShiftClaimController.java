package com.smartshift.controller;

import com.smartshift.dto.openshift.AvailableOpenShiftResponse;
import com.smartshift.dto.openshift.OpenShiftClaimRequest;
import com.smartshift.dto.openshift.OpenShiftClaimResponse;
import com.smartshift.dto.openshift.OpenShiftClaimReviewRequest;
import com.smartshift.enums.OpenShiftClaimStatus;
import com.smartshift.service.OpenShiftClaimService;
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
@RequestMapping("/api/open-shift-claims")
@RequiredArgsConstructor
public class OpenShiftClaimController {

    private final OpenShiftClaimService openShiftClaimService;

    @GetMapping("/available")
    public ResponseEntity<List<AvailableOpenShiftResponse>> getAvailableShifts(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            openShiftClaimService.getAvailableShifts(authentication.getName())
        );
    }

    @GetMapping("/me")
    public ResponseEntity<List<OpenShiftClaimResponse>> getMyClaims(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            openShiftClaimService.getMyClaims(authentication.getName())
        );
    }

    @PostMapping
    public ResponseEntity<OpenShiftClaimResponse> createClaim(
        @Valid @RequestBody OpenShiftClaimRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            openShiftClaimService.createClaim(
                authentication.getName(),
                request
            )
        );
    }

    @PatchMapping("/me/{id}/cancel")
    public ResponseEntity<OpenShiftClaimResponse> cancelClaim(
        @PathVariable
        @Positive(message = "Id yêu cầu nhận ca phải lớn hơn 0")
        Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            openShiftClaimService.cancelClaim(id, authentication.getName())
        );
    }

    @GetMapping
    public ResponseEntity<List<OpenShiftClaimResponse>> getClaims(
        @RequestParam(required = false) OpenShiftClaimStatus status,
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            openShiftClaimService.getClaims(
                status,
                locationId,
                authentication.getName()
            )
        );
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<OpenShiftClaimResponse> reviewClaim(
        @PathVariable
        @Positive(message = "Id yêu cầu nhận ca phải lớn hơn 0")
        Long id,
        @Valid @RequestBody OpenShiftClaimReviewRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            openShiftClaimService.reviewClaim(
                id,
                request,
                authentication.getName()
            )
        );
    }
}
