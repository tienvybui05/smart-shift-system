package com.smartshift.controller;
import com.smartshift.dto.attendance.*;
import com.smartshift.enums.AttendanceCorrectionStatus;
import com.smartshift.service.impl.AttendanceCorrectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/attendance-corrections") @RequiredArgsConstructor @Validated
public class AttendanceCorrectionController {
    private final AttendanceCorrectionService service;

    @GetMapping("/me")
    public List<AttendanceCorrectionResponse> mine(Authentication a,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate) {
        return service.list(a.getName(),true,null,null,startDate,endDate);
    }
    @PostMapping("/me")
    public ResponseEntity<AttendanceCorrectionResponse> create(Authentication a,@Valid @RequestBody AttendanceCorrectionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(a.getName(),body));
    }
    @PatchMapping("/me/{id}/cancel")
    public AttendanceCorrectionResponse cancel(Authentication a,@PathVariable @Positive Long id) { return service.cancel(a.getName(),id); }

    @GetMapping
    public List<AttendanceCorrectionResponse> list(Authentication a,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate,
            @RequestParam(required=false) @Positive Long locationId,@RequestParam(required=false) AttendanceCorrectionStatus status) {
        return service.list(a.getName(),false,locationId,status,startDate,endDate);
    }
    @PatchMapping("/{id}/review")
    public AttendanceCorrectionResponse review(Authentication a,@PathVariable @Positive Long id,@Valid @RequestBody AttendanceCorrectionReview body) {
        return service.review(a.getName(),id,body);
    }
    @GetMapping("/me/history")
    public List<AttendanceHistoryResponse> myHistory(Authentication a,@RequestParam @Positive Long shiftAssignmentId) {
        return service.history(a.getName(),shiftAssignmentId,true);
    }
    @GetMapping("/history")
    public List<AttendanceHistoryResponse> history(Authentication a,@RequestParam @Positive Long shiftAssignmentId) {
        return service.history(a.getName(),shiftAssignmentId,false);
    }
}

