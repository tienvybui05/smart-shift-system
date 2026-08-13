package com.smartshift.dto.workshift;

import java.util.List;

public record GenerateWorkShiftsResponse(
    int createdCount,
    int skippedCount,
    List<WorkShiftResponse> workShifts
) {
}
