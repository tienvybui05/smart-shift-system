package com.smartshift.dto.assignment;

import java.util.List;

public record CalendarPlacementResponse(Long assignmentId,Long userId,Long sourceWorkShiftId,
    Long targetWorkShiftId,long version,List<String> warnings) {}
