package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.mapper.SchedulePeriodMapper;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulePublicationConstraintTest {

    @Mock
    private SchedulePeriodRepository schedulePeriodRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkShiftRepository workShiftRepository;

    @Mock
    private ShiftRequirementRepository shiftRequirementRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private SchedulePeriodMapper schedulePeriodMapper;

    @Mock
    private AssignmentConstraintService assignmentConstraintService;

    @Mock
    private NotificationService notificationService;

    private SchedulePeriodServiceImpl schedulePeriodService;
    private SchedulePeriod schedulePeriod;
    private WorkShift workShift;
    private Position position;
    private User employee;
    private ShiftRequirement requirement;
    private ShiftAssignment assignment;

    @BeforeEach
    void setUp() {
        schedulePeriodService = new SchedulePeriodServiceImpl(
            schedulePeriodRepository,
            locationRepository,
            userRepository,
            workShiftRepository,
            shiftRequirementRepository,
            shiftAssignmentRepository,
            schedulePeriodMapper,
            assignmentConstraintService,
            notificationService
        );

        Location location = new Location();
        location.setId(1L);
        location.setCode("HCM");
        location.setName("Chi nhánh Hồ Chí Minh");
        location.setTimezone("Asia/Ho_Chi_Minh");
        location.setActive(true);

        position = new Position();
        position.setId(2L);
        position.setCode("CASHIER");
        position.setName("Thu ngân");
        position.setActive(true);

        employee = new User();
        employee.setId(3L);
        employee.setEmployeeCode("EMP003");
        employee.setFullName("Nguyễn Văn A");
        employee.setLocation(location);
        employee.setPosition(position);
        employee.setActive(true);

        schedulePeriod = new SchedulePeriod();
        schedulePeriod.setId(4L);
        schedulePeriod.setName("Tuần kiểm thử");
        schedulePeriod.setLocation(location);
        schedulePeriod.setStartDate(LocalDate.of(2026, 8, 24));
        schedulePeriod.setEndDate(LocalDate.of(2026, 8, 30));
        schedulePeriod.setStatus(SchedulePeriodStatus.DRAFT);

        workShift = new WorkShift();
        workShift.setId(5L);
        workShift.setSchedulePeriod(schedulePeriod);
        workShift.setStartAt(Instant.parse("2026-08-24T01:00:00Z"));
        workShift.setEndAt(Instant.parse("2026-08-24T09:00:00Z"));
        workShift.setBreakMinutes((short) 0);
        workShift.setStatus(WorkShiftStatus.FILLED);

        requirement = new ShiftRequirement();
        requirement.setId(6L);
        requirement.setWorkShift(workShift);
        requirement.setPosition(position);
        requirement.setMinEmployees((short) 1);
        requirement.setMaxEmployees((short) 1);

        assignment = new ShiftAssignment();
        assignment.setId(7L);
        assignment.setWorkShift(workShift);
        assignment.setUser(employee);
        assignment.setPosition(position);

        when(schedulePeriodRepository.findById(schedulePeriod.getId()))
            .thenReturn(Optional.of(schedulePeriod));
        when(workShiftRepository.search(schedulePeriod.getId(), null))
            .thenReturn(List.of(workShift));
        when(shiftRequirementRepository.findAllByWorkShiftId(workShift.getId()))
            .thenReturn(List.of(requirement));
    }

    @Test
    void invalidAssignmentBlocksPublicationAndDoesNotMeetStaffing() {
        stubAssignments(List.of(assignment));
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(new AssignmentConstraintResult(
            null,
            List.of("Nhân viên có đơn nghỉ đã được duyệt trong thời gian của ca"),
            new BigDecimal("8.00"),
            new BigDecimal("8.00")
        ));

        var result = schedulePeriodService.checkPublication(
            schedulePeriod.getId()
        );

        assertFalse(result.canPublish());
        assertEquals(1, result.invalidAssignments());
        assertEquals(1, result.understaffedShifts());
        assertTrue(result.shiftIssues().stream().anyMatch(
            issue -> issue.issueCode().equals("INVALID_ASSIGNMENT")
        ));
        assertTrue(result.shiftIssues().stream().anyMatch(
            issue -> issue.issueCode().equals("UNDERSTAFFED")
        ));
    }

    @Test
    void validAssignmentCanSatisfyPublicationRequirement() {
        stubAssignments(List.of(assignment));
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(new AssignmentConstraintResult(
            AvailabilityType.AVAILABLE,
            List.of(),
            new BigDecimal("8.00"),
            new BigDecimal("8.00")
        ));

        var result = schedulePeriodService.checkPublication(
            schedulePeriod.getId()
        );

        assertTrue(result.canPublish());
        assertEquals(0, result.invalidAssignments());
        assertEquals(0, result.understaffedShifts());
        assertTrue(result.shiftIssues().isEmpty());
    }

    @Test
    void overstaffedPositionBlocksPublication() {
        User secondEmployee = new User();
        secondEmployee.setId(30L);
        secondEmployee.setEmployeeCode("EMP030");
        secondEmployee.setFullName("Trần Văn B");
        secondEmployee.setLocation(employee.getLocation());
        secondEmployee.setPosition(position);
        secondEmployee.setActive(true);

        ShiftAssignment secondAssignment = new ShiftAssignment();
        secondAssignment.setId(70L);
        secondAssignment.setWorkShift(workShift);
        secondAssignment.setUser(secondEmployee);
        secondAssignment.setPosition(position);

        stubAssignments(List.of(assignment, secondAssignment));
        AssignmentConstraintResult validResult = new AssignmentConstraintResult(
            AvailabilityType.AVAILABLE,
            List.of(),
            new BigDecimal("8.00"),
            new BigDecimal("8.00")
        );
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(validResult);
        when(assignmentConstraintService.evaluate(
            secondEmployee,
            workShift,
            position
        )).thenReturn(validResult);

        var result = schedulePeriodService.checkPublication(
            schedulePeriod.getId()
        );

        assertFalse(result.canPublish());
        assertEquals(0, result.invalidAssignments());
        assertTrue(result.shiftIssues().stream().anyMatch(
            issue -> issue.issueCode().equals("OVERSTAFFED")
        ));
    }

    private void stubAssignments(List<ShiftAssignment> assignments) {
        when(shiftAssignmentRepository
            .findAllDetailedByWorkShiftIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(workShift.getId()),
                anyList()
            )).thenReturn(assignments);
    }
}
