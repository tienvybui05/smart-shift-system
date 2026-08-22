package com.smartshift.dto.autoschedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AutoScheduleAssignmentResponse(
    Long assignmentId,
    Long workShiftId,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean endsNextDay,
    Long userId,
    String employeeCode,
    String employeeName,
    Long positionId,
    String positionName,
    BigDecimal score,
    List<String> selectionReasons
) {

    public AutoScheduleAssignmentResponse {
        selectionReasons = List.copyOf(selectionReasons);
    }
}
