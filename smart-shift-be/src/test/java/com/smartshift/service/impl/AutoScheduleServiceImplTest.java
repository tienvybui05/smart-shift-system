package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.autoschedule.AutoScheduleRequest;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.Role;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentSource;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.EmploymentType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.ScheduleAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoScheduleServiceImplTest {

    private static final Long PERIOD_ID = 10L;
    private static final String ADMIN_USERNAME = "admin";

    @Mock
    private SchedulePeriodRepository schedulePeriodRepository;

    @Mock
    private WorkShiftRepository workShiftRepository;

    @Mock
    private ShiftRequirementRepository shiftRequirementRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssignmentConstraintService assignmentConstraintService;

    @Mock
    private ScheduleAuditService scheduleAuditService;

    private AutoScheduleServiceImpl autoScheduleService;
    private Location location;
    private Position position;
    private SchedulePeriod schedulePeriod;
    private WorkShift workShift;
    private ShiftRequirement requirement;
    private User admin;

    @BeforeEach
    void setUp() {
        autoScheduleService = new AutoScheduleServiceImpl(
            schedulePeriodRepository,
            workShiftRepository,
            shiftRequirementRepository,
            shiftAssignmentRepository,
            userRepository,
            new ShiftAssignmentMapper(),
            assignmentConstraintService,
            scheduleAuditService
        );
        location = location(1L);
        position = position(2L);
        admin = user(3L, "Quản trị", position, "ROLE_ADMIN");
        schedulePeriod = schedulePeriod(location, admin);
        workShift = workShift(schedulePeriod, Instant.parse("2100-01-04T01:00:00Z"));
        requirement = requirement(workShift, position, 1, 2, 1);
    }

    @Test
    void fillsOnlyTheShortageAndPrefersPreferredAvailability() {
        requirement.setMinEmployees((short) 2);
        User existingEmployee = user(
            20L,
            "Nhân viên cũ",
            position,
            "ROLE_EMPLOYEE"
        );
        User availableEmployee = user(
            21L,
            "An",
            position,
            "ROLE_EMPLOYEE"
        );
        User preferredEmployee = user(
            22L,
            "Bình",
            position,
            "ROLE_EMPLOYEE"
        );
        ShiftAssignment existingAssignment = assignment(
            100L,
            workShift,
            existingEmployee,
            AssignmentSource.MANUAL
        );
        stubBase(
            List.of(existingEmployee, availableEmployee, preferredEmployee),
            List.of(existingAssignment)
        );
        when(assignmentConstraintService.evaluate(
            eq(availableEmployee),
            eq(workShift),
            eq(position)
        )).thenReturn(eligible(AvailabilityType.AVAILABLE, "8.00"));
        when(assignmentConstraintService.evaluate(
            eq(preferredEmployee),
            eq(workShift),
            eq(position)
        )).thenReturn(eligible(AvailabilityType.PREFERRED, "8.00"));
        stubSavedAssignmentIds();

        var result = autoScheduleService.generate(
            new AutoScheduleRequest(PERIOD_ID),
            ADMIN_USERNAME
        );

        ArgumentCaptor<ShiftAssignment> assignmentCaptor =
            ArgumentCaptor.forClass(ShiftAssignment.class);
        verify(shiftAssignmentRepository).saveAndFlush(
            assignmentCaptor.capture()
        );
        ShiftAssignment saved = assignmentCaptor.getValue();
        assertEquals(preferredEmployee, saved.getUser());
        assertEquals(AssignmentSource.AUTO, saved.getAssignmentSource());
        assertEquals(AssignmentStatus.ASSIGNED, saved.getStatus());
        assertEquals(admin, saved.getAssignedBy());
        assertTrue(saved.getScore().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(1, result.assignedEmployeesBefore());
        assertEquals(1, result.assignmentsCreated());
        assertEquals(2, result.assignedEmployeesAfter());
        assertEquals(1, result.preferredAssignments());
        assertEquals(new BigDecimal("100.00"), result.coveragePercentage());
        assertEquals(WorkShiftStatus.FILLED, workShift.getStatus());
    }

    @Test
    void reportsShortageReasonsWhenEveryCandidateIsIneligible() {
        User employee = user(
            20L,
            "Nhân viên bận",
            position,
            "ROLE_EMPLOYEE"
        );
        stubBase(List.of(employee), List.of());
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(ineligible("Nhân viên không rảnh trọn ca"));

        var result = autoScheduleService.generate(
            new AutoScheduleRequest(PERIOD_ID),
            ADMIN_USERNAME
        );

        verify(shiftAssignmentRepository, never()).saveAndFlush(any());
        assertEquals(0, result.assignmentsCreated());
        assertEquals(1, result.unfilledPositions());
        assertEquals(1, result.understaffedShifts());
        assertEquals(1, result.shortages().size());
        assertEquals(1, result.shortages().get(0).missingEmployees());
        assertTrue(result.shortages().get(0).reasons().stream()
            .anyMatch(reason -> reason.contains("không rảnh")));
        assertEquals(WorkShiftStatus.OPEN, workShift.getStatus());
    }

    @Test
    void isIdempotentWhenMinimumStaffingIsAlreadyMet() {
        User existingEmployee = user(
            20L,
            "Nhân viên cũ",
            position,
            "ROLE_EMPLOYEE"
        );
        ShiftAssignment existingAssignment = assignment(
            100L,
            workShift,
            existingEmployee,
            AssignmentSource.MANUAL
        );
        stubBase(List.of(existingEmployee), List.of(existingAssignment));

        var result = autoScheduleService.generate(
            new AutoScheduleRequest(PERIOD_ID),
            ADMIN_USERNAME
        );

        verify(shiftAssignmentRepository, never()).saveAndFlush(any());
        assertEquals(0, result.assignmentsCreated());
        assertEquals(1, result.assignedEmployeesBefore());
        assertEquals(1, result.assignedEmployeesAfter());
        assertEquals(0, result.shortages().size());
        assertEquals(new BigDecimal("100.00"), result.coveragePercentage());
        assertEquals(WorkShiftStatus.FILLED, workShift.getStatus());
    }

    @Test
    void doesNotAssignAStartedShift() {
        workShift.setStartAt(Instant.parse("2020-01-01T01:00:00Z"));
        workShift.setEndAt(Instant.parse("2020-01-01T09:00:00Z"));
        User employee = user(
            20L,
            "Nhân viên",
            position,
            "ROLE_EMPLOYEE"
        );
        stubBase(List.of(employee), List.of());

        var result = autoScheduleService.generate(
            new AutoScheduleRequest(PERIOD_ID),
            ADMIN_USERNAME
        );

        verify(assignmentConstraintService, never()).evaluate(any(), any(), any());
        verify(shiftAssignmentRepository, never()).saveAndFlush(any());
        assertEquals(1, result.shortages().size());
        assertTrue(result.shortages().get(0).reasons().stream()
            .anyMatch(reason -> reason.contains("đã bắt đầu")));
    }

    @Test
    void rejectsAPeriodThatIsNotDraft() {
        schedulePeriod.setStatus(SchedulePeriodStatus.PUBLISHED);
        when(schedulePeriodRepository.findByIdForUpdate(PERIOD_ID))
            .thenReturn(Optional.of(schedulePeriod));

        assertThrows(
            BusinessRuleException.class,
            () -> autoScheduleService.generate(
                new AutoScheduleRequest(PERIOD_ID),
                ADMIN_USERNAME
            )
        );

        verify(workShiftRepository, never()).search(any(), any());
        verify(shiftAssignmentRepository, never()).saveAndFlush(any());
    }

    private void stubBase(
        List<User> employees,
        List<ShiftAssignment> existingAssignments
    ) {
        when(schedulePeriodRepository.findByIdForUpdate(PERIOD_ID))
            .thenReturn(Optional.of(schedulePeriod));
        when(userRepository.findByUsername(ADMIN_USERNAME))
            .thenReturn(Optional.of(admin));
        when(workShiftRepository.search(PERIOD_ID, null))
            .thenReturn(List.of(workShift));
        when(userRepository.findAllSchedulableByLocationForUpdate(
            location.getId()
        )).thenReturn(employees);
        when(shiftRequirementRepository.findAllByWorkShiftId(workShift.getId()))
            .thenReturn(List.of(requirement));
        when(shiftAssignmentRepository
            .findAllDetailedByWorkShiftIdAndStatusIn(
                eq(workShift.getId()),
                any()
            )).thenReturn(existingAssignments);
    }

    private void stubSavedAssignmentIds() {
        AtomicLong ids = new AtomicLong(500L);
        when(shiftAssignmentRepository.saveAndFlush(any()))
            .thenAnswer(invocation -> {
                ShiftAssignment assignment = invocation.getArgument(0);
                assignment.setId(ids.getAndIncrement());
                return assignment;
            });
    }

    private AssignmentConstraintResult eligible(
        AvailabilityType availabilityType,
        String projectedWeeklyHours
    ) {
        return new AssignmentConstraintResult(
            availabilityType,
            List.of(),
            new BigDecimal("8.00"),
            new BigDecimal(projectedWeeklyHours)
        );
    }

    private AssignmentConstraintResult ineligible(String reason) {
        return new AssignmentConstraintResult(
            null,
            List.of(reason),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2)
        );
    }

    private SchedulePeriod schedulePeriod(Location resultLocation, User createdBy) {
        SchedulePeriod result = new SchedulePeriod();
        result.setId(PERIOD_ID);
        result.setLocation(resultLocation);
        result.setName("Kỳ test");
        result.setStartDate(LocalDate.of(2100, 1, 4));
        result.setEndDate(LocalDate.of(2100, 1, 10));
        result.setStatus(SchedulePeriodStatus.DRAFT);
        result.setCreatedBy(createdBy);
        return result;
    }

    private WorkShift workShift(SchedulePeriod period, Instant startAt) {
        WorkShift result = new WorkShift();
        result.setId(30L);
        result.setSchedulePeriod(period);
        result.setStartAt(startAt);
        result.setEndAt(startAt.plusSeconds(8 * 60 * 60));
        result.setBreakMinutes((short) 0);
        result.setStatus(WorkShiftStatus.OPEN);
        return result;
    }

    private ShiftRequirement requirement(
        WorkShift shift,
        Position resultPosition,
        int minEmployees,
        int maxEmployees,
        int priority
    ) {
        ShiftRequirement result = new ShiftRequirement();
        result.setId(40L);
        result.setWorkShift(shift);
        result.setPosition(resultPosition);
        result.setMinEmployees((short) minEmployees);
        result.setMaxEmployees((short) maxEmployees);
        result.setPriority((short) priority);
        return result;
    }

    private ShiftAssignment assignment(
        Long id,
        WorkShift shift,
        User employee,
        AssignmentSource source
    ) {
        ShiftAssignment result = new ShiftAssignment();
        result.setId(id);
        result.setWorkShift(shift);
        result.setUser(employee);
        result.setPosition(employee.getPosition());
        result.setAssignmentSource(source);
        result.setStatus(AssignmentStatus.ASSIGNED);
        return result;
    }

    private User user(
        Long id,
        String fullName,
        Position resultPosition,
        String roleName
    ) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);

        User result = new User();
        result.setId(id);
        result.setEmployeeCode("EMP" + id);
        result.setUsername("user" + id);
        result.setFullName(fullName);
        result.setRole(role);
        result.setLocation(location);
        result.setPosition(resultPosition);
        result.setEmploymentType(EmploymentType.FULL_TIME);
        result.setHireDate(LocalDate.of(2020, 1, 1));
        result.setMinHoursPerWeek(new BigDecimal("40.00"));
        result.setMaxHoursPerWeek(new BigDecimal("48.00"));
        result.setMaxHoursPerDay(new BigDecimal("12.00"));
        result.setMinRestHours(new BigDecimal("8.00"));
        result.setMaxConsecutiveDays((short) 6);
        result.setActive(true);
        return result;
    }

    private Location location(Long id) {
        Location result = new Location();
        result.setId(id);
        result.setCode("LOC" + id);
        result.setName("Chi nhánh " + id);
        result.setTimezone(ZoneOffset.UTC.getId());
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
