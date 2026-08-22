package com.smartshift.dto.autoschedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AutoScheduleShortageResponse(
    Long workShiftId,
    String shiftName,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean endsNextDay,
    Long positionId,
    String positionName,
    int minimumEmployees,
    int assignedEmployees,
    int missingEmployees,
    List<String> reasons
) {

    public AutoScheduleShortageResponse {
        reasons = List.copyOf(reasons);
    }
}
