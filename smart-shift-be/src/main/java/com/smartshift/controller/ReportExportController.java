package com.smartshift.controller;

import com.smartshift.enums.AttendanceStatus;
import com.smartshift.service.ReportExportService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Validated
public class ReportExportController {
    public static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ReportExportService service;

    @GetMapping("/api/attendances/export")
    public ResponseEntity<byte[]> attendance(Authentication auth,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate,
            @RequestParam(required=false) @Positive Long locationId,@RequestParam(required=false) AttendanceStatus status) {
        return file(service.attendance(auth.getName(),startDate,endDate,locationId,status),"bang_cham_cong_"+startDate+"_"+endDate+".xlsx");
    }
    @GetMapping("/api/payroll/export")
    public ResponseEntity<byte[]> payroll(Authentication auth,@RequestParam LocalDate startDate,@RequestParam LocalDate endDate,
            @RequestParam(required=false) @Positive Long locationId) {
        return file(service.payroll(auth.getName(),startDate,endDate,locationId),"bang_luong_"+startDate+"_"+endDate+".xlsx");
    }
    private ResponseEntity<byte[]> file(byte[] bytes,String filename) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX)).contentLength(bytes.length)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.PRAGMA,"no-cache")
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
