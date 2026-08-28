package com.smartshift.service.impl;

import com.smartshift.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AttendanceAbsenceReconciliationService {

    private final AttendanceRepository attendanceRepository;
    private final Clock clock;

    @Transactional
    public int reconcileEndedShifts() {
        Instant reconciledAt = clock.instant();
        return attendanceRepository.createAbsencesForEndedShifts(
            reconciledAt
        );
    }
}
