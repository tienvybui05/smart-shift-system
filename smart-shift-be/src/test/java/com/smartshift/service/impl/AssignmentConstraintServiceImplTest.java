package com.smartshift.service.impl;

import com.smartshift.entity.EmployeeAvailability;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.EmploymentType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.repository.EmployeeAvailabilityRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentConstraintServiceImplTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private EmployeeAvailabilityRepository availabilityRepository;

    @Mock
    private TimeOffRequestRepository timeOffRequestRepository;

    private AssignmentConstraintServiceImpl constraintService;
    private Location location;
    private Position position;
    private User employee;
    private WorkShift targetShift;

    @BeforeEach
    void setUp() {
        constraintService = new AssignmentConstraintServiceImpl(
            shiftAssignmentRepository,
            availabilityRepository,
            timeOffRequestRepository
        );
        location = location(1L);
        position = position(2L);
        employee = employee(3L, location, position);
        targetShift = workShift(
            4L,
            location,
            MONDAY,
            LocalTime.of(8, 0),
            MONDAY,
            LocalTime.of(16, 0)
        );
    }

    @Test
    void eligibleWhenAllHardConstraintsAreSatisfied() {
        stubEvaluation(
            List.of(availability(
                MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                AvailabilityType.PREFERRED
            )),
            List.of(),
            false
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertTrue(result.eligible());
        assertEquals(AvailabilityType.PREFERRED, result.availabilityType());
        assertEquals(new BigDecimal("8.00"), result.projectedDailyHours());
        assertEquals(new BigDecimal("8.00"), result.projectedWeeklyHours());
    }

    @Test
    void approvedTimeOffMakesCandidateIneligible() {
        stubEvaluation(
            List.of(availability(
                MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                AvailabilityType.AVAILABLE
            )),
            List.of(),
            true
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertFalse(result.eligible());
        assertTrue(hasViolation(result.violations(), "đơn nghỉ đã được duyệt"));
    }

    @Test
    void rejectsInactiveEmployeeWrongLocationAndWrongPosition() {
        employee.setActive(false);
        employee.setLocation(location(20L));
        employee.setPosition(position(30L));
        stubEvaluation(List.of(), List.of(), false);

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertTrue(hasViolation(result.violations(), "ngừng hoạt động"));
        assertTrue(hasViolation(result.violations(), "không thuộc chi nhánh"));
        assertTrue(hasViolation(result.violations(), "không còn phù hợp"));
    }

    @Test
    void rejectsOverlappingAssignedShift() {
        WorkShift existingShift = workShift(
            5L,
            location,
            MONDAY,
            LocalTime.of(12, 0),
            MONDAY,
            LocalTime.of(18, 0)
        );
        stubEvaluation(
            List.of(availability(
                MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                AvailabilityType.AVAILABLE
            )),
            List.of(assignment(existingShift)),
            false
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertFalse(result.eligible());
        assertTrue(hasViolation(result.violations(), "trùng thời gian"));
    }

    @Test
    void rejectsInsufficientRestBetweenShifts() {
        WorkShift previousShift = workShift(
            5L,
            location,
            MONDAY.minusDays(1),
            LocalTime.of(18, 0),
            MONDAY,
            LocalTime.of(2, 0)
        );
        stubEvaluation(
            List.of(availability(
                MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                AvailabilityType.AVAILABLE
            )),
            List.of(assignment(previousShift)),
            false
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertFalse(result.eligible());
        assertTrue(hasViolation(result.violations(), "giờ nghỉ giữa hai ca"));
    }

    @Test
    void rejectsProjectedDailyAndWeeklyHourLimits() {
        employee.setMinRestHours(BigDecimal.ZERO);
        employee.setMaxHoursPerDay(new BigDecimal("8.00"));
        employee.setMaxHoursPerWeek(new BigDecimal("10.00"));
        WorkShift earlierShift = workShift(
            5L,
            location,
            MONDAY,
            LocalTime.of(0, 0),
            MONDAY,
            LocalTime.of(4, 0)
        );
        stubEvaluation(
            List.of(availability(
                MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                AvailabilityType.AVAILABLE
            )),
            List.of(assignment(earlierShift)),
            false
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertEquals(new BigDecimal("12.00"), result.projectedDailyHours());
        assertEquals(new BigDecimal("12.00"), result.projectedWeeklyHours());
        assertTrue(hasViolation(result.violations(), "giờ làm trong ngày"));
        assertTrue(hasViolation(result.violations(), "giờ làm trong tuần"));
    }

    @Test
    void adjacentAvailabilitySlotsCoverOvernightShiftWithoutMinuteGap() {
        targetShift = workShift(
            4L,
            location,
            MONDAY,
            LocalTime.of(22, 0),
            MONDAY.plusDays(1),
            LocalTime.of(6, 0)
        );
        stubEvaluation(
            List.of(
                availability(
                    MONDAY,
                    LocalTime.of(22, 0),
                    LocalTime.of(23, 59),
                    AvailabilityType.PREFERRED
                ),
                availability(
                    MONDAY.plusDays(1),
                    LocalTime.MIDNIGHT,
                    LocalTime.of(6, 0),
                    AvailabilityType.PREFERRED
                )
            ),
            List.of(),
            false
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertTrue(result.eligible());
        assertEquals(AvailabilityType.PREFERRED, result.availabilityType());
    }

    @Test
    void unavailableOverlapOverridesPositiveAvailability() {
        stubEvaluation(
            List.of(
                availability(
                    MONDAY,
                    LocalTime.of(8, 0),
                    LocalTime.of(16, 0),
                    AvailabilityType.AVAILABLE
                ),
                availability(
                    MONDAY,
                    LocalTime.of(12, 0),
                    LocalTime.of(13, 0),
                    AvailabilityType.UNAVAILABLE
                )
            ),
            List.of(),
            false
        );

        var result = constraintService.evaluate(
            employee,
            targetShift,
            position
        );

        assertFalse(result.eligible());
        assertTrue(hasViolation(result.violations(), "toàn bộ thời gian"));
    }

    private void stubEvaluation(
        List<EmployeeAvailability> availabilities,
        List<ShiftAssignment> assignments,
        boolean hasApprovedTimeOff
    ) {
        when(timeOffRequestRepository.existsOverlappingRequest(
            eq(employee.getId()),
            eq(List.of(TimeOffStatus.APPROVED)),
            any(Instant.class),
            any(Instant.class)
        )).thenReturn(hasApprovedTimeOff);
        when(availabilityRepository
            .findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
                eq(employee.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
            )).thenReturn(availabilities);
        when(shiftAssignmentRepository.findActiveAssignmentsInRange(
            eq(employee.getId()),
            anyList(),
            any(Instant.class),
            any(Instant.class)
        )).thenReturn(assignments);
    }

    private boolean hasViolation(List<String> violations, String text) {
        return violations.stream().anyMatch(message -> message.contains(text));
    }

    private EmployeeAvailability availability(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        AvailabilityType type
    ) {
        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setUser(employee);
        availability.setAvailableDate(date);
        availability.setStartTime(startTime);
        availability.setEndTime(endTime);
        availability.setAvailabilityType(type);
        return availability;
    }

    private ShiftAssignment assignment(WorkShift workShift) {
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setWorkShift(workShift);
        assignment.setUser(employee);
        assignment.setPosition(position);
        return assignment;
    }

    private WorkShift workShift(
        Long id,
        Location shiftLocation,
        LocalDate startDate,
        LocalTime startTime,
        LocalDate endDate,
        LocalTime endTime
    ) {
        SchedulePeriod schedulePeriod = new SchedulePeriod();
        schedulePeriod.setId(10L);
        schedulePeriod.setLocation(shiftLocation);
        schedulePeriod.setStartDate(MONDAY.minusDays(7));
        schedulePeriod.setEndDate(MONDAY.plusDays(7));
        schedulePeriod.setStatus(SchedulePeriodStatus.DRAFT);

        WorkShift workShift = new WorkShift();
        workShift.setId(id);
        workShift.setSchedulePeriod(schedulePeriod);
        workShift.setStartAt(startDate.atTime(startTime)
            .toInstant(ZoneOffset.UTC));
        workShift.setEndAt(endDate.atTime(endTime)
            .toInstant(ZoneOffset.UTC));
        workShift.setBreakMinutes((short) 0);
        workShift.setStatus(WorkShiftStatus.OPEN);
        return workShift;
    }

    private User employee(
        Long id,
        Location employeeLocation,
        Position employeePosition
    ) {
        User user = new User();
        user.setId(id);
        user.setEmployeeCode("EMP" + id);
        user.setFullName("Nhân viên " + id);
        user.setLocation(employeeLocation);
        user.setPosition(employeePosition);
        user.setEmploymentType(EmploymentType.FULL_TIME);
        user.setMaxHoursPerDay(new BigDecimal("8.00"));
        user.setMaxHoursPerWeek(new BigDecimal("40.00"));
        user.setMinRestHours(new BigDecimal("12.00"));
        user.setActive(true);
        return user;
    }

    private Location location(Long id) {
        Location result = new Location();
        result.setId(id);
        result.setCode("LOC" + id);
        result.setName("Chi nhánh " + id);
        result.setTimezone("UTC");
        result.setActive(true);
        return result;
    }

    private Position position(Long id) {
        Position result = new Position();
        result.setId(id);
        result.setCode("POS" + id);
        result.setName("Vị trí " + id);
        result.setActive(true);
        return result;
    }
}
