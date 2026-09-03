package com.smartshift.dto.shiftswap;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftSwapAssignmentResponse(
    Long assignmentId,
    Long workShiftId,
    Long schedulePeriodId,
    String schedulePeriodName,
    Long locationId,
    String locationName,
    String shiftName,
    String colorCode,
    LocalDate workDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean overnight,
    Long positionId,
    String positionName
) {
}
