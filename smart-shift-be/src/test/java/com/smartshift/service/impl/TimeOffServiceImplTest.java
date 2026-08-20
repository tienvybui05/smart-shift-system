package com.smartshift.service.impl;

import com.smartshift.dto.timeoff.TimeOffRequest;
import com.smartshift.dto.timeoff.TimeOffReviewRequest;
import com.smartshift.entity.Location;
import com.smartshift.entity.Role;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.enums.LeaveType;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.TimeOffMapper;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import com.smartshift.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeOffServiceImplTest {

    @Mock
    private TimeOffRequestRepository timeOffRequestRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    private TimeOffServiceImpl timeOffService;
    private Location primaryLocation;
    private Location secondaryLocation;
    private User employee;
    private User manager;

    @BeforeEach
    void setUp() {
        timeOffService = new TimeOffServiceImpl(
            timeOffRequestRepository,
            shiftAssignmentRepository,
            userRepository,
            new TimeOffMapper()
        );

        primaryLocation = location(1L, "Chi nhánh 1");
        secondaryLocation = location(2L, "Chi nhánh 2");
        employee = user(10L, "employee", "ROLE_EMPLOYEE", primaryLocation);
        manager = user(20L, "manager", "ROLE_MANAGER", primaryLocation);
    }

    @Test
    void createMyRequestCreatesPendingRequestForAuthenticatedUser() {
        Instant startAt = Instant.now().plusSeconds(86_400);
        Instant endAt = startAt.plusSeconds(28_800);
        TimeOffRequest request = new TimeOffRequest(
            startAt,
            endAt,
            LeaveType.ANNUAL,
            "  Việc gia đình  "
        );

        when(userRepository.findByUsernameForUpdate("employee"))
            .thenReturn(Optional.of(employee));
        when(timeOffRequestRepository.existsOverlappingRequest(
            employee.getId(),
            List.of(TimeOffStatus.PENDING, TimeOffStatus.APPROVED),
            startAt,
            endAt
        )).thenReturn(false);
        when(timeOffRequestRepository.save(any()))
            .thenAnswer(invocation -> {
                com.smartshift.entity.TimeOffRequest saved = invocation
                    .getArgument(0);
                saved.setId(100L);
                return saved;
            });

        var response = timeOffService.createMyRequest("employee", request);

        assertEquals(100L, response.id());
        assertEquals(employee.getId(), response.userId());
        assertEquals(TimeOffStatus.PENDING, response.status());
        assertEquals("Việc gia đình", response.reason());
    }

    @Test
    void createMyRequestRejectsOverlapWithPendingOrApprovedRequest() {
        Instant startAt = Instant.now().plusSeconds(86_400);
        Instant endAt = startAt.plusSeconds(3_600);
        TimeOffRequest request = new TimeOffRequest(
            startAt,
            endAt,
            LeaveType.SICK,
            null
        );

        when(userRepository.findByUsernameForUpdate("employee"))
            .thenReturn(Optional.of(employee));
        when(timeOffRequestRepository.existsOverlappingRequest(
            employee.getId(),
            List.of(TimeOffStatus.PENDING, TimeOffStatus.APPROVED),
            startAt,
            endAt
        )).thenReturn(true);

        assertThrows(
            DuplicateResourceException.class,
            () -> timeOffService.createMyRequest("employee", request)
        );
        verify(timeOffRequestRepository, never()).save(any());
    }

    @Test
    void cancelMyRequestCancelsOwnedPendingFutureRequest() {
        var request = pendingRequest(100L, employee);
        when(timeOffRequestRepository.findByIdForUpdate(100L))
            .thenReturn(Optional.of(request));
        when(timeOffRequestRepository.save(request)).thenReturn(request);

        var response = timeOffService.cancelMyRequest(100L, "employee");

        assertEquals(TimeOffStatus.CANCELLED, response.status());
        verify(timeOffRequestRepository).save(request);
    }

    @Test
    void cancelMyRequestHidesRequestsOwnedByAnotherUser() {
        var request = pendingRequest(100L, employee);
        when(timeOffRequestRepository.findByIdForUpdate(100L))
            .thenReturn(Optional.of(request));

        assertThrows(
            ResourceNotFoundException.class,
            () -> timeOffService.cancelMyRequest(100L, "someone-else")
        );
        verify(timeOffRequestRepository, never()).save(any());
    }

    @Test
    void getRequestsScopesManagerToOwnLocation() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(manager));
        when(timeOffRequestRepository.search(TimeOffStatus.PENDING, 1L))
            .thenReturn(List.of());

        timeOffService.getRequests(TimeOffStatus.PENDING, null, "manager");

        verify(timeOffRequestRepository).search(TimeOffStatus.PENDING, 1L);
    }

    @Test
    void getRequestsRejectsManagerLocationOverride() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(manager));

        assertThrows(
            BusinessRuleException.class,
            () -> timeOffService.getRequests(
                TimeOffStatus.PENDING,
                secondaryLocation.getId(),
                "manager"
            )
        );
        verify(timeOffRequestRepository, never()).search(any(), any());
    }

    @Test
    void reviewRequestRejectsApprovalWhenActiveAssignmentOverlaps() {
        var request = pendingRequest(100L, employee);
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(manager));
        when(timeOffRequestRepository.findByIdForUpdate(100L))
            .thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(employee.getId()))
            .thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.findActiveAssignmentsInRange(
            employee.getId(),
            List.of(
                com.smartshift.enums.AssignmentStatus.ASSIGNED,
                com.smartshift.enums.AssignmentStatus.CONFIRMED
            ),
            request.getStartAt(),
            request.getEndAt()
        )).thenReturn(List.of(new ShiftAssignment()));

        assertThrows(
            BusinessRuleException.class,
            () -> timeOffService.reviewRequest(
                100L,
                new TimeOffReviewRequest(TimeOffStatus.APPROVED),
                "manager"
            )
        );
        verify(timeOffRequestRepository, never()).save(any());
    }

    @Test
    void reviewRequestApprovesPendingRequestAndRecordsReviewer() {
        var request = pendingRequest(100L, employee);
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(manager));
        when(timeOffRequestRepository.findByIdForUpdate(100L))
            .thenReturn(Optional.of(request));
        when(userRepository.findByIdForUpdate(employee.getId()))
            .thenReturn(Optional.of(employee));
        when(shiftAssignmentRepository.findActiveAssignmentsInRange(
            employee.getId(),
            List.of(
                com.smartshift.enums.AssignmentStatus.ASSIGNED,
                com.smartshift.enums.AssignmentStatus.CONFIRMED
            ),
            request.getStartAt(),
            request.getEndAt()
        )).thenReturn(List.of());
        when(timeOffRequestRepository.save(request)).thenReturn(request);

        var response = timeOffService.reviewRequest(
            100L,
            new TimeOffReviewRequest(TimeOffStatus.APPROVED),
            "manager"
        );

        assertEquals(TimeOffStatus.APPROVED, response.status());
        assertEquals(manager.getId(), response.approvedById());
        assertNotNull(response.approvedAt());
    }

    private com.smartshift.entity.TimeOffRequest pendingRequest(
        Long id,
        User owner
    ) {
        var request = new com.smartshift.entity.TimeOffRequest();
        request.setId(id);
        request.setUser(owner);
        request.setStartAt(Instant.now().plusSeconds(86_400));
        request.setEndAt(Instant.now().plusSeconds(90_000));
        request.setLeaveType(LeaveType.ANNUAL);
        request.setStatus(TimeOffStatus.PENDING);
        return request;
    }

    private Location location(Long id, String name) {
        Location location = new Location();
        location.setId(id);
        location.setCode("LOC_" + id);
        location.setName(name);
        location.setTimezone("UTC");
        return location;
    }

    private User user(
        Long id,
        String username,
        String roleName,
        Location location
    ) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);

        User user = new User();
        user.setId(id);
        user.setEmployeeCode("EMP_" + id);
        user.setUsername(username);
        user.setFullName(username);
        user.setRole(role);
        user.setLocation(location);
        user.setActive(true);
        return user;
    }
}
