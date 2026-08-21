package com.smartshift.dto.schedule;

import com.smartshift.enums.SchedulePeriodStatus;

import java.util.List;

public record SchedulePublicationCheckResponse(
    Long schedulePeriodId,
    SchedulePeriodStatus currentStatus,
    int totalShifts,
    int activeShifts,
    int cancelledShifts,
    int totalMinimumEmployees,
    int totalAssignedEmployees,
    int shiftsWithoutRequirements,
    int understaffedShifts,
    int invalidAssignments,
    boolean canPublish,
    List<String> blockers,
    List<SchedulePublicationIssueResponse> shiftIssues
) {
}
