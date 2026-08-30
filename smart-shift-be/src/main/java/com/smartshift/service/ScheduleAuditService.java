package com.smartshift.service;

import com.smartshift.dto.audit.ScheduleAuditResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.ScheduleAuditAction;
import com.smartshift.enums.ScheduleAuditTargetType;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleAuditService {

    List<ScheduleAuditResponse> getAuditLogs(
        String username,
        Long locationId,
        Long schedulePeriodId,
        ScheduleAuditAction action,
        LocalDate startDate,
        LocalDate endDate
    );

    void record(
        String username,
        SchedulePeriod schedulePeriod,
        WorkShift workShift,
        ScheduleAuditAction action,
        ScheduleAuditTargetType targetType,
        Long targetId,
        String reason,
        Object beforeData,
        Object afterData
    );
}
