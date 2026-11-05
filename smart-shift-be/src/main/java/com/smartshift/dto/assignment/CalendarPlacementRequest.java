package com.smartshift.dto.assignment;

import jakarta.validation.constraints.*;

public record CalendarPlacementRequest(
    @NotNull @Positive Long userId,
    @NotNull @Positive Long targetWorkShiftId,
    @Positive Long sourceAssignmentId,
    @Positive Long sourceWorkShiftId,
    @Min(0) Long expectedVersion,
    @NotBlank @Size(max=1000) String reason
) {}
