package com.smartshift.service.impl;

import com.smartshift.repository.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceAbsenceReconciliationServiceTest {

    private static final Instant NOW = Instant.parse(
        "2026-08-28T10:00:00Z"
    );

    @Mock
    private AttendanceRepository attendanceRepository;

    private AttendanceAbsenceReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceAbsenceReconciliationService(
            attendanceRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void reconcileEndedShiftsUsesServerClockAndReturnsCreatedCount() {
        when(attendanceRepository.createAbsencesForEndedShifts(NOW))
            .thenReturn(2);

        int createdCount = service.reconcileEndedShifts();

        assertThat(createdCount).isEqualTo(2);
        verify(attendanceRepository).createAbsencesForEndedShifts(NOW);
    }
}
