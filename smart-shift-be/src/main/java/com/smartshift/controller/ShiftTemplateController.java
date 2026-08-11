package com.smartshift.controller;

import com.smartshift.dto.shift.ShiftTemplateRequest;
import com.smartshift.dto.shift.ShiftTemplateResponse;
import com.smartshift.dto.shift.ShiftTemplateStatusRequest;
import com.smartshift.service.ShiftTemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/shift-templates")
@RequiredArgsConstructor
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;

    @GetMapping
    public ResponseEntity<List<ShiftTemplateResponse>> getShiftTemplates(
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(
            shiftTemplateService.getShiftTemplates(locationId, active)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftTemplateResponse> getShiftTemplateById(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(
            shiftTemplateService.getShiftTemplateById(id)
        );
    }

    @PostMapping
    public ResponseEntity<ShiftTemplateResponse> createShiftTemplate(
        @Valid @RequestBody ShiftTemplateRequest request
    ) {
        ShiftTemplateResponse response = shiftTemplateService
            .createShiftTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShiftTemplateResponse> updateShiftTemplate(
        @PathVariable Long id,
        @Valid @RequestBody ShiftTemplateRequest request
    ) {
        return ResponseEntity.ok(
            shiftTemplateService.updateShiftTemplate(id, request)
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ShiftTemplateResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody ShiftTemplateStatusRequest request
    ) {
        return ResponseEntity.ok(
            shiftTemplateService.updateStatus(id, request.active())
        );
    }
}
