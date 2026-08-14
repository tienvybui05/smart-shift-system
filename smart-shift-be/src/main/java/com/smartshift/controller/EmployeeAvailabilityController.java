package com.smartshift.controller;

import com.smartshift.dto.availability.AvailabilityRequest;
import com.smartshift.dto.availability.AvailabilityResponse;
import com.smartshift.service.EmployeeAvailabilityService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/employee-availabilities")
@RequiredArgsConstructor
public class EmployeeAvailabilityController {

    private final EmployeeAvailabilityService availabilityService;

    @GetMapping("/me")
    public ResponseEntity<List<AvailabilityResponse>> getMyAvailabilities(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            availabilityService.getMyAvailabilities(
                authentication.getName(),
                startDate,
                endDate
            )
        );
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<AvailabilityResponse>> getUserAvailabilities(
        @PathVariable
        @Positive(message = "Id nhân viên phải lớn hơn 0")
        Long userId,
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate
    ) {
        return ResponseEntity.ok(
            availabilityService.getUserAvailabilities(
                userId,
                startDate,
                endDate
            )
        );
    }

    @PostMapping("/me")
    public ResponseEntity<AvailabilityResponse> createMyAvailability(
        @Valid @RequestBody AvailabilityRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            availabilityService.createMyAvailability(
                authentication.getName(),
                request
            )
        );
    }

    @PutMapping("/me/{id}")
    public ResponseEntity<AvailabilityResponse> updateMyAvailability(
        @PathVariable Long id,
        @Valid @RequestBody AvailabilityRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            availabilityService.updateMyAvailability(
                id,
                authentication.getName(),
                request
            )
        );
    }

    @DeleteMapping("/me/{id}")
    public ResponseEntity<Void> deleteMyAvailability(
        @PathVariable Long id,
        Authentication authentication
    ) {
        availabilityService.deleteMyAvailability(
            id,
            authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }
}
