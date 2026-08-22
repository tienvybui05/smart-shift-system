package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.openshift.OpenShiftClaimRequest;
import com.smartshift.dto.openshift.OpenShiftClaimReviewRequest;
import com.smartshift.entity.Location;
import com.smartshift.entity.OpenShiftClaim;
import com.smartshift.entity.Position;
import com.smartshift.entity.Role;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.ShiftTemplate;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentSource;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.EmploymentType;
import com.smartshift.enums.OpenShiftClaimStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.mapper.OpenShiftClaimMapper;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.OpenShiftClaimRepository;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenShiftClaimServiceImplTest {

    @Mock
    private OpenShiftClaimRepository openShiftClaimRepository;

    @Mock
    private WorkShiftRepository workShiftRepository;

    @Mock
    private ShiftRequirementRepository shiftRequirementRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private SchedulePeriodRepository schedulePeriodRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssignmentConstraintService assignmentConstraintService;

    private OpenShiftClaimServiceImpl service;
    private Location location;
    private Position position;
    private SchedulePeriod period;
    private WorkShift workShift;
    private ShiftRequirement requirement;
    private User employee;
    private User manager;

    @BeforeEach
    void setUp() {
        service = new OpenShiftClaimServiceImpl(
            openShiftClaimRepository,
            workShiftRepository,
            shiftRequirementRepository,
            shiftAssignmentRepository,
            schedulePeriodRepository,
            userRepository,
            new OpenShiftClaimMapper(),
            new ShiftAssignmentMapper(),
            assignmentConstraintService
        );
        location = location(1L);
        position = position(2L);
        employee = user(3L, "employee", "ROLE_EMPLOYEE");
        manager = user(4L, "manager", "ROLE_MANAGER");
        period = period(10L);
        workShift = workShift(20L);
        requirement = requirement(30L);
    }

    @Test
    void getAvailableShiftsReturnsEligibleUnderstaffedShifts() {
        when(userRepository.findByUsername("employee"))
            .thenReturn(Optional.of(employee));
        when(openShiftClaimRepository
            .findAllByUserUsernameOrderByCreatedAtDesc("employee"))
            .thenReturn(List.of());
        when(workShiftRepository.findClaimableShifts(
            eq(location.getId()),
            eq(position.getId()),
            eq(SchedulePeriodStatus.DRAFT),
            eq(WorkShiftStatus.OPEN),
            any(Instant.class)
        )).thenReturn(List.of(workShift));
        when(shiftRequirementRepository
            .findByWorkShiftIdAndPositionId(workShift.getId(), position.getId()))
            .thenReturn(Optional.of(requirement));
        when(shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShift.getId(),
                position.getId(),
                List.of(
                    com.smartshift.enums.AssignmentStatus.ASSIGNED,
                    com.smartshift.enums.AssignmentStatus.CONFIRMED
                )
            )).thenReturn(0L);
        when(openShiftClaimRepository.countByWorkShiftIdAndStatus(
            workShift.getId(),
            OpenShiftClaimStatus.PENDING
        )).thenReturn(2L);
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(eligibleResult());

        var result = service.getAvailableShifts("employee");

        assertEquals(1, result.size());
        assertEquals(workShift.getId(), result.get(0).workShiftId());
        assertEquals(1, result.get(0).missingEmployees());
        assertEquals(2L, result.get(0).pendingClaims());
        assertEquals(AvailabilityType.PREFERRED,
            result.get(0).availabilityType());
        assertNull(result.get(0).myPendingClaimId());
    }

    @Test
    void createClaimCreatesTrimmedPendingClaim() {
        stubLockedShiftAndEmployee();
        when(shiftRequirementRepository
            .findByWorkShiftIdAndPositionId(workShift.getId(), position.getId()))
            .thenReturn(Optional.of(requirement));
        when(shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            workShift.getId(),
            employee.getId()
        )).thenReturn(Optional.empty());
        when(shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShift.getId(),
                position.getId(),
                List.of(
                    com.smartshift.enums.AssignmentStatus.ASSIGNED,
                    com.smartshift.enums.AssignmentStatus.CONFIRMED
                )
            )).thenReturn(0L);
        when(openShiftClaimRepository
            .existsByWorkShiftIdAndUserIdAndStatus(
                workShift.getId(),
                employee.getId(),
                OpenShiftClaimStatus.PENDING
            )).thenReturn(false);
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(eligibleResult());
        when(openShiftClaimRepository.saveAndFlush(any()))
            .thenAnswer(invocation -> {
                OpenShiftClaim claim = invocation.getArgument(0);
                claim.setId(40L);
                return claim;
            });

        var response = service.createClaim(
            "employee",
            new OpenShiftClaimRequest(workShift.getId(), "  Muốn làm thêm  ")
        );

        assertEquals(40L, response.id());
        assertEquals(OpenShiftClaimStatus.PENDING, response.status());
        assertEquals("Muốn làm thêm", response.reason());
        assertEquals(employee.getId(), response.userId());
    }

    @Test
    void createClaimRejectsEmployeeWhoNoLongerMeetsConstraints() {
        stubLockedShiftAndEmployee();
        when(shiftRequirementRepository
            .findByWorkShiftIdAndPositionId(workShift.getId(), position.getId()))
            .thenReturn(Optional.of(requirement));
        when(shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            workShift.getId(),
            employee.getId()
        )).thenReturn(Optional.empty());
        when(shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShift.getId(),
                position.getId(),
                List.of(
                    com.smartshift.enums.AssignmentStatus.ASSIGNED,
                    com.smartshift.enums.AssignmentStatus.CONFIRMED
                )
            )).thenReturn(0L);
        when(openShiftClaimRepository
            .existsByWorkShiftIdAndUserIdAndStatus(
                workShift.getId(),
                employee.getId(),
                OpenShiftClaimStatus.PENDING
            )).thenReturn(false);
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(new AssignmentConstraintResult(
            AvailabilityType.AVAILABLE,
            List.of("Vượt giới hạn giờ làm trong tuần"),
            new BigDecimal("8.00"),
            new BigDecimal("48.00")
        ));

        assertThrows(
            BusinessRuleException.class,
            () -> service.createClaim(
                "employee",
                new OpenShiftClaimRequest(workShift.getId(), null)
            )
        );
        verify(openShiftClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelClaimCancelsOwnedPendingFutureClaim() {
        OpenShiftClaim claim = pendingClaim(40L);
        when(openShiftClaimRepository.findSchedulePeriodIdByClaimId(40L))
            .thenReturn(Optional.of(period.getId()));
        when(schedulePeriodRepository.findByIdForUpdate(period.getId()))
            .thenReturn(Optional.of(period));
        when(openShiftClaimRepository.findByIdForUpdate(40L))
            .thenReturn(Optional.of(claim));
        when(openShiftClaimRepository.saveAndFlush(claim))
            .thenReturn(claim);

        var response = service.cancelClaim(40L, "employee");

        assertEquals(OpenShiftClaimStatus.CANCELLED, response.status());
        assertNull(response.reviewedById());
        assertNull(response.assignmentId());
    }

    @Test
    void approvingClaimCreatesClaimAssignmentAndFillsShift() {
        OpenShiftClaim claim = pendingClaim(40L);
        when(openShiftClaimRepository.findSchedulePeriodIdByClaimId(40L))
            .thenReturn(Optional.of(period.getId()));
        when(schedulePeriodRepository.findByIdForUpdate(period.getId()))
            .thenReturn(Optional.of(period));
        when(openShiftClaimRepository.findByIdForUpdate(40L))
            .thenReturn(Optional.of(claim));
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(manager));
        when(userRepository.findByIdForUpdate(employee.getId()))
            .thenReturn(Optional.of(employee));
        when(shiftRequirementRepository
            .findByWorkShiftIdAndPositionId(workShift.getId(), position.getId()))
            .thenReturn(Optional.of(requirement));
        when(shiftAssignmentRepository.findByWorkShiftIdAndUserId(
            workShift.getId(),
            employee.getId()
        )).thenReturn(Optional.empty());
        when(shiftAssignmentRepository
            .countByWorkShiftIdAndPositionIdAndStatusIn(
                workShift.getId(),
                position.getId(),
                List.of(
                    com.smartshift.enums.AssignmentStatus.ASSIGNED,
                    com.smartshift.enums.AssignmentStatus.CONFIRMED
                )
            )).thenReturn(0L, 1L, 1L);
        when(assignmentConstraintService.evaluate(
            employee,
            workShift,
            position
        )).thenReturn(eligibleResult());
        when(shiftAssignmentRepository.saveAndFlush(any()))
            .thenAnswer(invocation -> {
                ShiftAssignment assignment = invocation.getArgument(0);
                assignment.setId(50L);
                return assignment;
            });
        when(openShiftClaimRepository.saveAndFlush(claim))
            .thenReturn(claim);
        when(shiftRequirementRepository.findAllByWorkShiftId(workShift.getId()))
            .thenReturn(List.of(requirement));
        when(workShiftRepository.saveAndFlush(workShift))
            .thenReturn(workShift);
        when(openShiftClaimRepository
            .findAllByShiftPositionAndStatusForUpdate(
                workShift.getId(),
                position.getId(),
                OpenShiftClaimStatus.PENDING
            )).thenReturn(List.of());

        var response = service.reviewClaim(
            40L,
            new OpenShiftClaimReviewRequest(
                OpenShiftClaimStatus.APPROVED,
                "  Đủ điều kiện  "
            ),
            "manager"
        );

        assertEquals(OpenShiftClaimStatus.APPROVED, response.status());
        assertEquals(50L, response.assignmentId());
        assertEquals(manager.getId(), response.reviewedById());
        assertEquals("Đủ điều kiện", response.reviewerNote());
        assertNotNull(response.reviewedAt());
        assertEquals(AssignmentSource.CLAIM,
            claim.getAssignment().getAssignmentSource());
        assertEquals(manager, claim.getAssignment().getAssignedBy());
        assertEquals(WorkShiftStatus.FILLED, workShift.getStatus());
    }

    private void stubLockedShiftAndEmployee() {
        when(workShiftRepository.findSchedulePeriodIdById(workShift.getId()))
            .thenReturn(Optional.of(period.getId()));
        when(schedulePeriodRepository.findByIdForUpdate(period.getId()))
            .thenReturn(Optional.of(period));
        when(workShiftRepository.findByIdForUpdate(workShift.getId()))
            .thenReturn(Optional.of(workShift));
        when(userRepository.findByUsernameForUpdate("employee"))
            .thenReturn(Optional.of(employee));
    }

    private AssignmentConstraintResult eligibleResult() {
        return new AssignmentConstraintResult(
            AvailabilityType.PREFERRED,
            List.of(),
            new BigDecimal("8.00"),
            new BigDecimal("24.00")
        );
    }

    private OpenShiftClaim pendingClaim(Long id) {
        OpenShiftClaim claim = new OpenShiftClaim();
        claim.setId(id);
        claim.setWorkShift(workShift);
        claim.setUser(employee);
        claim.setStatus(OpenShiftClaimStatus.PENDING);
        return claim;
    }

    private WorkShift workShift(Long id) {
        ShiftTemplate template = new ShiftTemplate();
        template.setId(100L);
        template.setName("Ca sáng");
        template.setColorCode("#3988df");

        WorkShift result = new WorkShift();
        result.setId(id);
        result.setSchedulePeriod(period);
        result.setShiftTemplate(template);
        result.setStartAt(Instant.now().plusSeconds(86_400));
        result.setEndAt(Instant.now().plusSeconds(115_200));
        result.setBreakMinutes((short) 60);
        result.setStatus(WorkShiftStatus.OPEN);
        return result;
    }

    private ShiftRequirement requirement(Long id) {
        ShiftRequirement result = new ShiftRequirement();
        result.setId(id);
        result.setWorkShift(workShift);
        result.setPosition(position);
        result.setMinEmployees((short) 1);
        result.setMaxEmployees((short) 2);
        result.setPriority((short) 1);
        return result;
    }

    private SchedulePeriod period(Long id) {
        SchedulePeriod result = new SchedulePeriod();
        result.setId(id);
        result.setName("Tuần kiểm thử");
        result.setLocation(location);
        result.setStartDate(LocalDate.now().plusDays(1));
        result.setEndDate(LocalDate.now().plusDays(7));
        result.setStatus(SchedulePeriodStatus.DRAFT);
        return result;
    }

    private User user(Long id, String username, String roleName) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);

        User result = new User();
        result.setId(id);
        result.setEmployeeCode("EMP_" + id);
        result.setUsername(username);
        result.setFullName(username);
        result.setRole(role);
        result.setLocation(location);
        result.setPosition(position);
        result.setEmploymentType(EmploymentType.FULL_TIME);
        result.setMaxHoursPerDay(new BigDecimal("8.00"));
        result.setMaxHoursPerWeek(new BigDecimal("40.00"));
        result.setMinRestHours(new BigDecimal("12.00"));
        result.setActive(true);
        return result;
    }

    private Location location(Long id) {
        Location result = new Location();
        result.setId(id);
        result.setCode("LOC_" + id);
        result.setName("Chi nhánh " + id);
        result.setTimezone("UTC");
        result.setActive(true);
        return result;
    }

    private Position position(Long id) {
        Position result = new Position();
        result.setId(id);
        result.setCode("POS_" + id);
        result.setName("Vị trí " + id);
        result.setActive(true);
        return result;
    }
}
