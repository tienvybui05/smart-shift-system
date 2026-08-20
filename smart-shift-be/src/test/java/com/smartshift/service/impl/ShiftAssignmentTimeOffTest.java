package com.smartshift.service.impl;

import com.smartshift.entity.EmployeeAvailability;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.EmploymentType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.EmployeeAvailabilityRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftAssignmentTimeOffTest {

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private ShiftRequirementRepository shiftRequirementRepository;

    @Mock
    private EmployeeAvailabilityRepository availabilityRepository;

    @Mock
    private TimeOffRequestRepository timeOffRequestRepository;

    @Mock
    private WorkShiftRepository workShiftRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShiftAssignmentMapper shiftAssignmentMapper;

    private ShiftAssignmentServiceImpl shiftAssignmentService;

    @BeforeEach
    void setUp() {
        shiftAssignmentService = new ShiftAssignmentServiceImpl(
            shiftAssignmentRepository,
            shiftRequirementRepository,
            availabilityRepository,
            timeOffRequestRepository,
            workShiftRepository,
            userRepository,
            shiftAssignmentMapper
        );
    }

    @Test
    void approvedTimeOffMakesOtherwiseEligibleCandidateIneligible() {
        LocalDate workDate = LocalDate.now(ZoneOffset.UTC).plusDays(2);
        Instant startAt = workDate.atTime(8, 0).toInstant(ZoneOffset.UTC);
        Instant endAt = workDate.atTime(16, 0).toInstant(ZoneOffset.UTC);

        Location location = new Location();
        location.setId(1L);
        location.setName("Chi nhánh 1");
        location.setTimezone("UTC");

        Position position = new Position();
        position.setId(2L);
        position.setCode("CASHIER");
        position.setName("Thu ngân");

        User employee = new User();
        employee.setId(3L);
        employee.setEmployeeCode("EMP003");
        employee.setFullName("Nhân viên 3");
        employee.setLocation(location);
        employee.setPosition(position);
        employee.setEmploymentType(EmploymentType.FULL_TIME);
        employee.setMaxHoursPerDay(new BigDecimal("8.00"));
        employee.setMaxHoursPerWeek(new BigDecimal("48.00"));
        employee.setMinRestHours(new BigDecimal("12.00"));
        employee.setActive(true);

        SchedulePeriod schedulePeriod = new SchedulePeriod();
        schedulePeriod.setId(4L);
        schedulePeriod.setLocation(location);
        schedulePeriod.setStatus(SchedulePeriodStatus.DRAFT);

        WorkShift workShift = new WorkShift();
        workShift.setId(5L);
        workShift.setSchedulePeriod(schedulePeriod);
        workShift.setStartAt(startAt);
        workShift.setEndAt(endAt);
        workShift.setBreakMinutes((short) 0);
        workShift.setStatus(WorkShiftStatus.OPEN);

        ShiftRequirement requirement = new ShiftRequirement();
        requirement.setId(6L);
        requirement.setWorkShift(workShift);
        requirement.setPosition(position);
        requirement.setMinEmployees((short) 1);
        requirement.setMaxEmployees((short) 1);

        EmployeeAvailability availability = new EmployeeAvailability();
        availability.setUser(employee);
        availability.setAvailableDate(workDate);
        availability.setStartTime(LocalTime.of(8, 0));
        availability.setEndTime(LocalTime.of(16, 0));
        availability.setAvailabilityType(AvailabilityType.AVAILABLE);

        when(workShiftRepository.findById(workShift.getId()))
            .thenReturn(Optional.of(workShift));
        when(shiftRequirementRepository.findAllByWorkShiftId(workShift.getId()))
            .thenReturn(List.of(requirement));
        when(shiftAssignmentRepository
            .findAllDetailedByWorkShiftIdAndStatusIn(
                workShift.getId(),
                List.of(
                    com.smartshift.enums.AssignmentStatus.ASSIGNED,
                    com.smartshift.enums.AssignmentStatus.CONFIRMED
                )
            )).thenReturn(List.of());
        when(userRepository
            .findAllByLocationIdAndPositionIdAndActiveTrueOrderByFullNameAsc(
                location.getId(),
                position.getId()
            )).thenReturn(List.of(employee));
        when(timeOffRequestRepository.existsOverlappingRequest(
            employee.getId(),
            List.of(TimeOffStatus.APPROVED),
            startAt,
            endAt
        )).thenReturn(true);
        when(availabilityRepository
            .findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
                employee.getId(),
                workDate,
                workDate
            )).thenReturn(List.of(availability));
        when(shiftAssignmentRepository.findActiveAssignmentsInRange(
            eq(employee.getId()),
            anyList(),
            any(Instant.class),
            any(Instant.class)
        )).thenReturn(List.of());

        var candidates = shiftAssignmentService.getCandidates(
            workShift.getId(),
            position.getId()
        );

        assertFalse(candidates.get(0).eligible());
        assertTrue(candidates.get(0).ineligibilityReasons().stream().anyMatch(
            reason -> reason.contains("đơn nghỉ đã được duyệt")
        ));
        verify(timeOffRequestRepository).existsOverlappingRequest(
            employee.getId(),
            List.of(TimeOffStatus.APPROVED),
            startAt,
            endAt
        );
    }
}
