package com.smartshift.service.impl;

import com.smartshift.dto.audit.ScheduleAuditResponse;
import com.smartshift.entity.ScheduleAuditLog;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.ScheduleAuditAction;
import com.smartshift.enums.ScheduleAuditTargetType;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ScheduleAuditMapper;
import com.smartshift.repository.ScheduleAuditLogRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.ScheduleAuditService;
import com.smartshift.service.SchedulingAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleAuditServiceImpl implements ScheduleAuditService {

    private static final int MAX_RANGE_DAYS = 93;
    private static final ZoneId AUDIT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ScheduleAuditLogRepository scheduleAuditLogRepository;
    private final UserRepository userRepository;
    private final SchedulingAccessService schedulingAccessService;
    private final ScheduleAuditMapper scheduleAuditMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ScheduleAuditResponse> getAuditLogs(
        String username,
        Long locationId,
        Long schedulePeriodId,
        ScheduleAuditAction action,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateRange(startDate, endDate);
        Long effectiveLocationId = schedulingAccessService
            .resolveLocationFilter(username, locationId);
        Instant rangeStart = startDate.atStartOfDay(AUDIT_ZONE).toInstant();
        Instant rangeEnd = endDate.plusDays(1)
            .atStartOfDay(AUDIT_ZONE)
            .toInstant();
        return scheduleAuditLogRepository.search(
                effectiveLocationId,
                schedulePeriodId,
                action,
                rangeStart,
                rangeEnd
            ).stream()
            .map(scheduleAuditMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public void record(
        String username,
        SchedulePeriod schedulePeriod,
        WorkShift workShift,
        ScheduleAuditAction action,
        ScheduleAuditTargetType targetType,
        Long targetId,
        String reason,
        Object beforeData,
        Object afterData
    ) {
        User actor = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng đang thay đổi lịch"
            ));
        ScheduleAuditLog audit = new ScheduleAuditLog();
        audit.setLocation(schedulePeriod.getLocation());
        audit.setSchedulePeriod(schedulePeriod);
        audit.setWorkShiftId(workShift == null ? null : workShift.getId());
        audit.setAction(action);
        audit.setTargetType(targetType);
        audit.setTargetId(targetId);
        audit.setActor(actor);
        audit.setActorName(actor.getFullName());
        audit.setReason(normalizeReason(reason, action));
        audit.setBeforeData(toJson(beforeData));
        audit.setAfterData(toJson(afterData));
        scheduleAuditLogRepository.save(audit);
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessRuleException("Vui lòng chọn đầy đủ khoảng ngày");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessRuleException(
                "Ngày kết thúc không được trước ngày bắt đầu"
            );
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_RANGE_DAYS) {
            throw new BusinessRuleException(
                "Chỉ được xem lịch sử trong khoảng tối đa 93 ngày"
            );
        }
    }

    private String normalizeReason(
        String reason,
        ScheduleAuditAction action
    ) {
        if (reason == null || reason.isBlank()) {
            return switch (action) {
                case CREATED -> "Tạo mới";
                case UPDATED -> "Cập nhật thông tin";
                case STATUS_CHANGED -> "Thay đổi trạng thái";
                case GENERATED -> "Sinh ca hàng loạt";
                case REQUIREMENTS_CHANGED -> "Cập nhật nhu cầu nhân sự";
                case ASSIGNED -> "Phân công nhân viên";
                case UNASSIGNED -> "Gỡ phân công nhân viên";
                case AUTO_SCHEDULED -> "Sinh lịch tự động";
                case PUBLISHED -> "Công bố lịch làm việc";
                case LOCKED -> "Khóa kỳ xếp lịch";
            };
        }
        String normalized = reason.trim();
        return normalized.length() <= 500
            ? normalized
            : normalized.substring(0, 500);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                "Không thể lưu dữ liệu lịch sử thay đổi lịch",
                exception
            );
        }
    }
}
