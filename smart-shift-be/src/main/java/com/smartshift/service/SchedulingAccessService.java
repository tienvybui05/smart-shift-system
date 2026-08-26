package com.smartshift.service;

import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftTemplate;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftTemplateRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulingAccessService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_MANAGER = "ROLE_MANAGER";
    private static final String OUTSIDE_LOCATION_MESSAGE =
        "Quản lý chỉ được quản lý lịch làm việc trong chi nhánh của mình";

    private final UserRepository userRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    public Long resolveLocationFilter(
        String username,
        Long requestedLocationId
    ) {
        User actor = getActor(username);
        if (isAdmin(actor)) {
            return requestedLocationId;
        }

        Long managerLocationId = getManagerLocationId(actor);
        if (
            requestedLocationId != null
                && !Objects.equals(managerLocationId, requestedLocationId)
        ) {
            throw new AccessDeniedException(OUTSIDE_LOCATION_MESSAGE);
        }
        return managerLocationId;
    }

    public void requireLocation(String username, Long locationId) {
        requireLocation(getActor(username), locationId);
    }

    public void requireShiftTemplate(String username, Long shiftTemplateId) {
        User actor = getActor(username);
        ShiftTemplate shiftTemplate = shiftTemplateRepository
            .findById(shiftTemplateId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy mẫu ca với id " + shiftTemplateId
            ));
        requireLocation(actor, shiftTemplate.getLocation().getId());
    }

    public void requireShiftTemplates(
        String username,
        List<Long> shiftTemplateIds
    ) {
        User actor = getActor(username);
        for (Long shiftTemplateId : shiftTemplateIds) {
            ShiftTemplate shiftTemplate = shiftTemplateRepository
                .findById(shiftTemplateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Không tìm thấy mẫu ca với id " + shiftTemplateId
                ));
            requireLocation(actor, shiftTemplate.getLocation().getId());
        }
    }

    public void requireSchedulePeriod(String username, Long schedulePeriodId) {
        User actor = getActor(username);
        SchedulePeriod schedulePeriod = schedulePeriodRepository
            .findById(schedulePeriodId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch với id " + schedulePeriodId
            ));
        requireLocation(actor, schedulePeriod.getLocation().getId());
    }

    public void requireWorkShift(String username, Long workShiftId) {
        User actor = getActor(username);
        WorkShift workShift = workShiftRepository
            .findById(workShiftId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy ca làm với id " + workShiftId
            ));
        requireLocation(
            actor,
            workShift.getSchedulePeriod().getLocation().getId()
        );
    }

    public void requireAssignment(String username, Long assignmentId) {
        User actor = getActor(username);
        ShiftAssignment assignment = shiftAssignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy phân công với id " + assignmentId
            ));
        requireLocation(
            actor,
            assignment.getWorkShift().getSchedulePeriod().getLocation().getId()
        );
    }

    private User getActor(String username) {
        User actor = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));

        String roleName = actor.getRole().getName();
        if (!ROLE_ADMIN.equals(roleName) && !ROLE_MANAGER.equals(roleName)) {
            throw new AccessDeniedException(
                "Bạn không có quyền quản lý lịch làm việc"
            );
        }
        return actor;
    }

    private void requireLocation(User actor, Long locationId) {
        if (isAdmin(actor)) {
            return;
        }
        if (!Objects.equals(getManagerLocationId(actor), locationId)) {
            throw new AccessDeniedException(OUTSIDE_LOCATION_MESSAGE);
        }
    }

    private Long getManagerLocationId(User actor) {
        if (!ROLE_MANAGER.equals(actor.getRole().getName())) {
            throw new AccessDeniedException(
                "Bạn không có quyền quản lý lịch làm việc"
            );
        }
        return actor.getLocation().getId();
    }

    private boolean isAdmin(User actor) {
        return ROLE_ADMIN.equals(actor.getRole().getName());
    }
}
