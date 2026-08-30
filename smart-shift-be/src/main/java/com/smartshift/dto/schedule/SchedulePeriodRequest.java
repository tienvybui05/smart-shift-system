package com.smartshift.dto.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SchedulePeriodRequest(
    @NotNull(message = "Chi nhánh không được để trống")
    @Positive(message = "Id chi nhánh phải lớn hơn 0")
    Long locationId,

    @NotBlank(message = "Tên kỳ xếp lịch không được để trống")
    @Size(max = 100, message = "Tên kỳ xếp lịch không được vượt quá 100 ký tự")
    String name,

    @NotNull(message = "Ngày bắt đầu không được để trống")
    LocalDate startDate,

    @NotNull(message = "Ngày kết thúc không được để trống")
    LocalDate endDate,

    @Size(max = 500, message = "Lý do thay đổi không được vượt quá 500 ký tự")
    String changeReason
) {
}
