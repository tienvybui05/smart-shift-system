package com.smartshift.dto.position;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PositionRequest(
    @NotBlank(message = "Mã vị trí không được để trống")
    @Size(max = 30, message = "Mã vị trí không được vượt quá 30 ký tự")
    String code,

    @NotBlank(message = "Tên vị trí không được để trống")
    @Size(max = 100, message = "Tên vị trí không được vượt quá 100 ký tự")
    String name,

    String description,

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    Boolean active
) {
}

