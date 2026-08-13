package com.smartshift.dto.workshift;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

public record GenerateWorkShiftsRequest(
    @NotNull(message = "Kỳ xếp lịch không được để trống")
    @Positive(message = "Id kỳ xếp lịch phải lớn hơn 0")
    Long schedulePeriodId,

    @NotEmpty(message = "Phải chọn ít nhất một mẫu ca")
    List<@Positive(message = "Id mẫu ca phải lớn hơn 0") Long> shiftTemplateIds,

    @NotNull(message = "Ngày bắt đầu không được để trống")
    LocalDate startDate,

    @NotNull(message = "Ngày kết thúc không được để trống")
    LocalDate endDate
) {
}
