package com.smartshift.service;

import com.smartshift.entity.Location;
import com.smartshift.entity.Role;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftTemplateRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;

    @Mock
    private SchedulePeriodRepository schedulePeriodRepository;

    @Mock
    private WorkShiftRepository workShiftRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    private SchedulingAccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new SchedulingAccessService(
            userRepository,
            shiftTemplateRepository,
            schedulePeriodRepository,
            workShiftRepository,
            shiftAssignmentRepository
        );
    }

    @Test
    void adminKeepsRequestedLocationFilter() {
        when(userRepository.findByUsername("admin"))
            .thenReturn(Optional.of(user("ROLE_ADMIN", 1L)));

        Long locationId = accessService.resolveLocationFilter("admin", 2L);

        assertEquals(2L, locationId);
    }

    @Test
    void managerWithoutFilterIsScopedToOwnLocation() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(user("ROLE_MANAGER", 3L)));

        Long locationId = accessService.resolveLocationFilter(
            "manager",
            null
        );

        assertEquals(3L, locationId);
    }

    @Test
    void managerCannotRequestAnotherLocation() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(user("ROLE_MANAGER", 3L)));

        assertThrows(
            AccessDeniedException.class,
            () -> accessService.resolveLocationFilter("manager", 4L)
        );
    }

    @Test
    void managerCanAccessWorkShiftInOwnLocation() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(user("ROLE_MANAGER", 3L)));
        when(workShiftRepository.findById(10L))
            .thenReturn(Optional.of(workShift(10L, 3L)));

        assertDoesNotThrow(
            () -> accessService.requireWorkShift("manager", 10L)
        );
    }

    @Test
    void managerCannotAccessSchedulePeriodInAnotherLocation() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(user("ROLE_MANAGER", 3L)));
        when(schedulePeriodRepository.findById(20L))
            .thenReturn(Optional.of(schedulePeriod(20L, 4L)));

        assertThrows(
            AccessDeniedException.class,
            () -> accessService.requireSchedulePeriod("manager", 20L)
        );
    }

    @Test
    void managerCannotRemoveAssignmentInAnotherLocation() {
        when(userRepository.findByUsername("manager"))
            .thenReturn(Optional.of(user("ROLE_MANAGER", 3L)));
        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setId(30L);
        assignment.setWorkShift(workShift(10L, 4L));
        when(shiftAssignmentRepository.findById(30L))
            .thenReturn(Optional.of(assignment));

        assertThrows(
            AccessDeniedException.class,
            () -> accessService.requireAssignment("manager", 30L)
        );
    }

    private User user(String roleName, Long locationId) {
        Role role = new Role();
        role.setName(roleName);

        User user = new User();
        user.setRole(role);
        user.setLocation(location(locationId));
        return user;
    }

    private WorkShift workShift(Long id, Long locationId) {
        WorkShift workShift = new WorkShift();
        workShift.setId(id);
        workShift.setSchedulePeriod(schedulePeriod(1L, locationId));
        return workShift;
    }

    private SchedulePeriod schedulePeriod(Long id, Long locationId) {
        SchedulePeriod schedulePeriod = new SchedulePeriod();
        schedulePeriod.setId(id);
        schedulePeriod.setLocation(location(locationId));
        return schedulePeriod;
    }

    private Location location(Long id) {
        Location location = new Location();
        location.setId(id);
        return location;
    }
}
