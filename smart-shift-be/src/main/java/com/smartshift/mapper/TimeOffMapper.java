package com.smartshift.mapper;

import com.smartshift.dto.timeoff.TimeOffRequest;
import com.smartshift.dto.timeoff.TimeOffResponse;
import com.smartshift.entity.User;
import com.smartshift.enums.TimeOffStatus;
import org.springframework.stereotype.Component;

@Component
public class TimeOffMapper {

    public com.smartshift.entity.TimeOffRequest toEntity(
        TimeOffRequest request,
        User user
    ) {
        com.smartshift.entity.TimeOffRequest timeOffRequest =
            new com.smartshift.entity.TimeOffRequest();
        timeOffRequest.setUser(user);
        timeOffRequest.setStartAt(request.startAt());
        timeOffRequest.setEndAt(request.endAt());
        timeOffRequest.setLeaveType(request.leaveType());
        timeOffRequest.setReason(normalizeNullableText(request.reason()));
        timeOffRequest.setStatus(TimeOffStatus.PENDING);
        return timeOffRequest;
    }

    public TimeOffResponse toResponse(
        com.smartshift.entity.TimeOffRequest request
    ) {
        User user = request.getUser();
        User approvedBy = request.getApprovedBy();
        return new TimeOffResponse(
            request.getId(),
            user.getId(),
            user.getEmployeeCode(),
            user.getFullName(),
            user.getLocation().getId(),
            user.getLocation().getName(),
            request.getStartAt(),
            request.getEndAt(),
            request.getLeaveType(),
            request.getReason(),
            request.getStatus(),
            approvedBy == null ? null : approvedBy.getId(),
            approvedBy == null ? null : approvedBy.getFullName(),
            request.getApprovedAt(),
            request.getCreatedAt()
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
