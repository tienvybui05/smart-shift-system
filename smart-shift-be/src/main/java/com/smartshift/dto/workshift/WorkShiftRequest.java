package com.smartshift.dto.workshift;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record WorkShiftRequest(
    @NotNull(message = "Kỳ xếp lịch không được để trống")
    @Positive(message = "Id kỳ xếp lịch phải lớn hơn 0")
    Long schedulePeriodId,

    @NotNull(message = "Mẫu ca không được để trống")
    @Positive(message = "Id mẫu ca phải lớn hơn 0")
    Long shiftTemplateId,

    @NotNull(message = "Ngày làm việc không được để trống")
    LocalDate workDate,

    @NotNull(message = "Giờ bắt đầu không được để trống")
    LocalTime startTime,

    @NotNull(message = "Giờ kết thúc không được để trống")
    LocalTime endTime,

    @NotNull(message = "Thời gian nghỉ không được để trống")
    @Min(value = 0, message = "Thời gian nghỉ không được âm")
    @Max(value = 1440, message = "Thời gian nghỉ không được vượt quá 1440 phút")
    Short breakMinutes,

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    String note,

    @Size(max = 500, message = "Lý do thay đổi không được vượt quá 500 ký tự")
    String changeReason
) {
}
