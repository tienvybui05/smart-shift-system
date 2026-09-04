package com.smartshift.dto.dashboard;

import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;

import java.time.Instant;

public record DashboardShiftResponse(
    Long workShiftId,
    Long assignmentId,
    String shiftName,
    String colorCode,
    String locationName,
    String positionName,
    Instant startAt,
    Instant endAt,
    Short breakMinutes,
    WorkShiftStatus workShiftStatus,
    SchedulePeriodStatus schedulePeriodStatus,
    long assignedEmployees,
    long requiredEmployees,
    boolean understaffed
) {
}
