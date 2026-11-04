package com.smartshift.controller;

import com.smartshift.dto.schedule.*;
import com.smartshift.service.SchedulingBudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduling-budgets")
@RequiredArgsConstructor
@Validated
public class SchedulingBudgetController {
    private final SchedulingBudgetService service;
    @GetMapping("/locations/{id}")
    public WeeklyBudgetSettings settings(Authentication auth,@PathVariable @Positive Long id) {
        return service.settings(auth.getName(),id);
    }
    @PatchMapping("/locations/{id}")
    public WeeklyBudgetSettings update(Authentication auth,@PathVariable @Positive Long id,@Valid @RequestBody WeeklyBudgetRequest body) {
        return service.update(auth.getName(),id,body);
    }
    @GetMapping("/periods/{id}")
    public ScheduleBudgetResponse report(Authentication auth,@PathVariable @Positive Long id) {
        return service.report(auth.getName(),id);
    }
}
