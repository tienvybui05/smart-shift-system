package com.smartshift.dto.shift;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record ShiftTemplateRequest(
    @NotNull(message = "Chi nhánh không được để trống")
    @Positive(message = "Id chi nhánh phải lớn hơn 0")
    Long locationId,

    @NotBlank(message = "Tên mẫu ca không được để trống")
    @Size(max = 50, message = "Tên mẫu ca không được vượt quá 50 ký tự")
    String name,

    @NotNull(message = "Giờ bắt đầu không được để trống")
    LocalTime startTime,

    @NotNull(message = "Giờ kết thúc không được để trống")
    LocalTime endTime,

    @NotNull(message = "Thời gian nghỉ không được để trống")
    @Min(value = 0, message = "Thời gian nghỉ không được âm")
    @Max(value = 1440, message = "Thời gian nghỉ không được vượt quá 1440 phút")
    Short breakMinutes,

    @Size(max = 7, message = "Mã màu không được vượt quá 7 ký tự")
    @Pattern(
        regexp = "^$|^#[0-9A-Fa-f]{6}$",
        message = "Mã màu phải có định dạng #RRGGBB"
    )
    String colorCode,

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    Boolean active
) {
}
