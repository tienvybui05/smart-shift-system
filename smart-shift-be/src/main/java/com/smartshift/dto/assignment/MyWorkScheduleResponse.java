package com.smartshift.dto.assignment;

import com.smartshift.enums.AssignmentSource;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.SchedulePeriodStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record MyWorkScheduleResponse(
    Long assignmentId,
    Long workShiftId,
    Long schedulePeriodId,
    String schedulePeriodName,
    SchedulePeriodStatus schedulePeriodStatus,
    Long locationId,
    String locationName,
    String shiftTemplateName,
    String colorCode,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean overnight,
    Short breakMinutes,
    long workMinutes,
    Long positionId,
    String positionName,
    AssignmentSource assignmentSource,
    AssignmentStatus assignmentStatus,
    String note
) {
}
