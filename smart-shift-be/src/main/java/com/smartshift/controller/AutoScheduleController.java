package com.smartshift.controller;

import com.smartshift.dto.autoschedule.AutoScheduleRequest;
import com.smartshift.dto.autoschedule.AutoScheduleResponse;
import com.smartshift.service.AutoScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class AutoScheduleController {

    private final AutoScheduleService autoScheduleService;

    @PostMapping("/generate")
    public ResponseEntity<AutoScheduleResponse> generate(
        @Valid @RequestBody AutoScheduleRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            autoScheduleService.generate(request, authentication.getName())
        );
    }
}
