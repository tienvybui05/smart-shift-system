package com.smartshift.mapper;

import com.smartshift.dto.shiftswap.ShiftSwapAssignmentResponse;
import com.smartshift.dto.shiftswap.ShiftSwapRequestResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftSwapRequest;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.ShiftSwapStatus;
import com.smartshift.enums.ShiftSwapType;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class ShiftSwapMapper {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_MANAGER = "ROLE_MANAGER";
    private static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";

    public ShiftSwapRequestResponse toResponse(
        ShiftSwapRequest request,
        User actor
    ) {
        User targetUser = request.getTargetUser();
        User approvedBy = request.getApprovedBy();
        boolean isRequester = actor.getId().equals(
            request.getRequesterUser().getId()
        );
        boolean publicGiveaway = request.getTargetAssignment() == null
            && targetUser == null;
        boolean employee = ROLE_EMPLOYEE.equals(actor.getRole().getName());
        boolean reviewer = ROLE_ADMIN.equals(actor.getRole().getName())
            || ROLE_MANAGER.equals(actor.getRole().getName());
        boolean intendedTarget = targetUser != null
            && actor.getId().equals(targetUser.getId());

        return new ShiftSwapRequestResponse(
            request.getId(),
            request.getTargetAssignment() == null
                ? ShiftSwapType.GIVEAWAY
                : ShiftSwapType.SWAP,
            request.getStatus(),
            request.getRequesterUser().getId(),
            request.getRequesterUser().getEmployeeCode(),
            request.getRequesterUser().getFullName(),
            toAssignmentResponse(request.getRequesterAssignment()),
            targetUser == null ? null : targetUser.getId(),
            targetUser == null ? null : targetUser.getEmployeeCode(),
            targetUser == null ? null : targetUser.getFullName(),
            request.getTargetAssignment() == null
                ? null
                : toAssignmentResponse(request.getTargetAssignment()),
            request.getReason(),
            request.getResponseNote(),
            request.getRespondedAt(),
            approvedBy == null ? null : approvedBy.getId(),
            approvedBy == null ? null : approvedBy.getFullName(),
            request.getApprovedAt(),
            request.getReviewerNote(),
            request.getCreatedAt(),
            request.getUpdatedAt(),
            isRequester && (
                request.getStatus() == ShiftSwapStatus.PENDING
                    || request.getStatus() == ShiftSwapStatus.ACCEPTED
            ),
            employee
                && !isRequester
                && request.getStatus() == ShiftSwapStatus.PENDING
                && (publicGiveaway || intendedTarget),
            reviewer && request.getStatus() == ShiftSwapStatus.ACCEPTED
        );
    }

    public ShiftSwapAssignmentResponse toAssignmentResponse(
        ShiftAssignment assignment
    ) {
        WorkShift workShift = assignment.getWorkShift();
        SchedulePeriod period = workShift.getSchedulePeriod();
        ZoneId zoneId = ZoneId.of(period.getLocation().getTimezone());
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);

        return new ShiftSwapAssignmentResponse(
            assignment.getId(),
            workShift.getId(),
            period.getId(),
            period.getName(),
            period.getLocation().getId(),
            period.getLocation().getName(),
            workShift.getShiftTemplate() == null
                ? "Ca tùy chỉnh"
                : workShift.getShiftTemplate().getName(),
            workShift.getShiftTemplate() == null
                ? null
                : workShift.getShiftTemplate().getColorCode(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            assignment.getPosition().getId(),
            assignment.getPosition().getName()
        );
    }
}
