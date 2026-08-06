package com.smartshift.dto.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocationRequest(
    @NotBlank(message = "Mã chi nhánh không được để trống")
    @Size(max = 30, message = "Mã chi nhánh không được vượt quá 30 ký tự")
    String code,

    @NotBlank(message = "Tên chi nhánh không được để trống")
    @Size(max = 100, message = "Tên chi nhánh không được vượt quá 100 ký tự")
    String name,

    String address,

    @NotBlank(message = "Múi giờ không được để trống")
    @Size(max = 50, message = "Múi giờ không được vượt quá 50 ký tự")
    String timezone,

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    Boolean active
) {
}

