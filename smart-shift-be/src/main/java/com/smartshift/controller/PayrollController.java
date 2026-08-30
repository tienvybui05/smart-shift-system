package com.smartshift.controller;

import com.smartshift.dto.payroll.PayrollBonusRequest;
import com.smartshift.dto.payroll.PayrollCalculationRequest;
import com.smartshift.dto.payroll.PayrollRecordResponse;
import com.smartshift.service.PayrollService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping("/me")
    public ResponseEntity<List<PayrollRecordResponse>> getMyPayrollRecords(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        Authentication authentication
    ) {
        return ResponseEntity.ok(payrollService.getMyPayrollRecords(
            authentication.getName(),
            startDate,
            endDate
        ));
    }

    @GetMapping
    public ResponseEntity<List<PayrollRecordResponse>> getPayrollRecords(
        @RequestParam LocalDate startDate,
        @RequestParam LocalDate endDate,
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(payrollService.getPayrollRecords(
            authentication.getName(),
            locationId,
            startDate,
            endDate
        ));
    }

    @PostMapping("/calculate")
    public ResponseEntity<List<PayrollRecordResponse>> calculatePayroll(
        @Valid @RequestBody PayrollCalculationRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(payrollService.calculatePayroll(
            authentication.getName(),
            request
        ));
    }

    @PatchMapping("/{id}/bonus")
    public ResponseEntity<PayrollRecordResponse> updateBonus(
        @PathVariable
        @Positive(message = "Id bảng lương phải lớn hơn 0")
        Long id,
        @Valid @RequestBody PayrollBonusRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(payrollService.updateBonus(
            authentication.getName(),
            id,
            request
        ));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PayrollRecordResponse> confirmPayroll(
        @PathVariable
        @Positive(message = "Id bảng lương phải lớn hơn 0")
        Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(payrollService.confirmPayroll(
            authentication.getName(),
            id
        ));
    }
}
