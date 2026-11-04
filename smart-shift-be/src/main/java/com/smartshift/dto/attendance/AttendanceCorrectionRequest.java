package com.smartshift.dto.attendance;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record AttendanceCorrectionRequest(
    @NotNull @Positive Long shiftAssignmentId,
    @NotNull Instant checkInAt,
    @NotNull Instant checkOutAt,
    @NotNull @Min(0) @Max(1440) Short breakMinutes,
    @NotBlank @Size(max=1000) String reason
) {}

