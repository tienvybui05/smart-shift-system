package com.smartshift.dto.schedule;

import java.time.LocalDate;
import java.time.LocalTime;

public record SchedulePublicationIssueResponse(
    Long workShiftId,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    String shiftName,
    Long positionId,
    String positionName,
    int requiredEmployees,
    int assignedEmployees,
    String issueCode,
    String message
) {
}
