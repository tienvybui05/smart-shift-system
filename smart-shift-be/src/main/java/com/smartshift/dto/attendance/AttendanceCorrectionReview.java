package com.smartshift.dto.attendance;
import jakarta.validation.constraints.*;
public record AttendanceCorrectionReview(
    @NotNull Boolean approved,
    @NotBlank @Size(max=1000) String note
) {}

