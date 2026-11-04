package com.smartshift.service;

import com.smartshift.dto.schedule.WeeklyBudgetRequest;
import com.smartshift.entity.Location;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SchedulingBudgetServiceTest {
    @Mock SchedulingAccessService access;
    @Mock LocationRepository locations;
    @Mock SchedulePeriodRepository periods;
    @Mock ShiftAssignmentRepository assignments;
    SchedulingBudgetService service;
    Location location;
    SchedulePeriod period;

    @BeforeEach void setup() {
        service=new SchedulingBudgetService(access,locations,periods,assignments);
        location=new Location(); location.setId(1L); location.setName("QA");
        location.setTimezone("Asia/Ho_Chi_Minh"); location.setWeeklyBudget(new BigDecimal("300000.00"));
        period=new SchedulePeriod(); period.setId(2L); period.setLocation(location);
        period.setStartDate(LocalDate.of(2025,12,31)); period.setEndDate(LocalDate.of(2026,1,6));
    }
    void rows(List<ShiftAssignmentRepository.BudgetAssignment> rows) {
        when(periods.findById(2L)).thenReturn(Optional.of(period));
        when(assignments.findBudgetAssignments(eq(1L),any(),any())).thenReturn(rows);
    }
    ShiftAssignmentRepository.BudgetAssignment row(long periodId,String start,int minutes,int pause,String rate,String coefficient) {
        var row=mock(ShiftAssignmentRepository.BudgetAssignment.class);
        Instant time=LocalDateTime.parse(start).atZone(ZoneId.of(location.getTimezone())).toInstant();
        when(row.getPeriodId()).thenReturn(periodId); when(row.getStartAt()).thenReturn(time);
        when(row.getEndAt()).thenReturn(time.plusSeconds(minutes*60L)); when(row.getBreakMinutes()).thenReturn((short)pause);
        when(row.getBasePayAmount()).thenReturn(new BigDecimal(rate));
        when(row.getSalaryCoefficient()).thenReturn(new BigDecimal(coefficient));
        return row;
    }
    @Test void includesOtherPeriodsAndChargesOvernightToItsLocalStartWeekAcrossYear() {
        rows(List.of(row(2,"2026-01-04T22:00",480,30,"30000","1.2"),
            row(3,"2025-12-29T08:00",240,0,"10000","1"),row(2,"2026-01-05T08:00",240,0,"30000","1")));
        var report=service.report("manager",2L);
        assertEquals(new BigDecimal("390000.00"),report.periodCost());
        assertTrue(report.overBudget()); assertEquals(2,report.weeks().size());
        var first=report.weeks().get(0);
        assertEquals(LocalDate.of(2025,12,29),first.startDate());
        assertEquals(new BigDecimal("270000.00"),first.periodCost());
        assertEquals(new BigDecimal("40000.00"),first.otherPeriodsCost());
        assertEquals(new BigDecimal("310000.00"),first.totalCost());
        assertEquals(new BigDecimal("10000.00"),first.excess());
        assertEquals(new BigDecimal("103.33"),first.utilizationPercent());
        assertEquals(new BigDecimal("180000.00"),report.weeks().get(1).remaining());
        verify(assignments).findBudgetAssignments(1L,Instant.parse("2025-12-28T17:00:00Z"),Instant.parse("2026-01-11T17:00:00Z"));
    }
    @Test void nullBudgetIsUnconfiguredWhileZeroBudgetIsARealLimit() {
        rows(List.of(row(2,"2026-01-01T08:00",60,0,"30000","1")));
        location.setWeeklyBudget(null);
        var absent=service.report("manager",2L);
        assertFalse(absent.overBudget()); assertNull(absent.weeks().get(0).remaining());
        location.setWeeklyBudget(new BigDecimal("0.00"));
        var zero=service.report("manager",2L);
        assertTrue(zero.overBudget()); assertNull(zero.weeks().get(0).utilizationPercent());
        assertEquals(new BigDecimal("30000.00"),zero.weeks().get(0).excess());
    }
    @Test void emptyWeeksRemainVisibleAndAreNotProrated() {
        rows(List.of());
        var result=service.report("manager",2L);
        assertEquals(2,result.weeks().size()); assertFalse(result.overBudget());
        assertEquals(new BigDecimal("300000.00"),result.weeks().get(0).remaining());
        assertEquals(new BigDecimal("0.00"),result.periodCost());
    }
    @Test void moneyIsRoundedPerAssignmentUsingDecimalArithmetic() {
        rows(List.of(row(2,"2026-01-01T08:00",1,0,"100","1"),
            row(2,"2026-01-01T09:00",1,0,"100","1"),row(2,"2026-01-01T10:00",1,0,"100","1")));
        assertEquals(new BigDecimal("5.01"),service.report("manager",2L).periodCost());
    }
    @Test void budgetUpdateOnlyChangesBudgetAndSupportsClear() {
        location.setWifiSsid("Keep network settings");
        when(locations.findByIdForUpdate(1L)).thenReturn(Optional.of(location));
        when(locations.save(location)).thenReturn(location);
        assertEquals(new BigDecimal("0.00"),service.update("manager",1L,new WeeklyBudgetRequest(BigDecimal.ZERO)).weeklyBudget());
        assertNull(service.update("manager",1L,new WeeklyBudgetRequest(null)).weeklyBudget());
        assertEquals("Keep network settings",location.getWifiSsid());
    }
    @Test void scopeIsCheckedBeforeReadingOrChangingBudgets() {
        doThrow(new AccessDeniedException("Other branch")).when(access).requireLocation("outsider",1L);
        assertThrows(AccessDeniedException.class,()->service.update("outsider",1L,new WeeklyBudgetRequest(BigDecimal.ONE)));
        verifyNoInteractions(locations,periods,assignments);
    }
}
