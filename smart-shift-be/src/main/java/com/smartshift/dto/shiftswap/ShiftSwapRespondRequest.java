package com.smartshift.dto.shiftswap;

import com.smartshift.enums.ShiftSwapStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShiftSwapRespondRequest(
    @NotNull(message = "Vui lòng chọn đồng ý hoặc từ chối")
    ShiftSwapStatus status,

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    String responseNote
) {
}
