package com.smartshift.dto.assignment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ShiftAssignmentRequest(
    @NotNull(message = "Nhân viên không được để trống")
    @Positive(message = "Id nhân viên phải lớn hơn 0")
    Long userId,

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    String note
) {
}
