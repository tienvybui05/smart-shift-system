package com.smartshift.controller;

import com.smartshift.dto.assignment.*;
import com.smartshift.dto.schedule.ScheduleBoardResponse;
import com.smartshift.service.ScheduleBoardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/schedule-periods/{periodId}/board") @Validated @RequiredArgsConstructor
public class ScheduleBoardController {
    private final ScheduleBoardService service;
    @GetMapping public ScheduleBoardResponse board(Authentication auth,@PathVariable @Positive Long periodId) {
        return service.board(auth.getName(),periodId);
    }
    @PostMapping("/preview") public CalendarPlacementPreview preview(Authentication auth,@PathVariable @Positive Long periodId,
            @Valid @RequestBody CalendarPlacementRequest body) {
        return service.preview(auth.getName(),periodId,body);
    }
    @PostMapping("/place") public CalendarPlacementResponse place(Authentication auth,@PathVariable @Positive Long periodId,
            @Valid @RequestBody CalendarPlacementRequest body) {
        return service.place(auth.getName(),periodId,body);
    }
}
