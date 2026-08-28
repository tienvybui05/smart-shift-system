package com.smartshift.scheduler;

import com.smartshift.service.impl.AttendanceAbsenceReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.attendance.absence-reconciliation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AttendanceAbsenceScheduler {

    private final AttendanceAbsenceReconciliationService reconciliationService;

    @Scheduled(
        fixedDelayString = "${app.attendance.absence-reconciliation.fixed-delay-ms:900000}",
        initialDelayString = "${app.attendance.absence-reconciliation.initial-delay-ms:10000}"
    )
    public void reconcileAbsences() {
        int createdCount = reconciliationService.reconcileEndedShifts();
        if (createdCount > 0) {
            log.info(
                "Created {} absent attendance record(s) for ended shifts",
                createdCount
            );
        }
    }
}
