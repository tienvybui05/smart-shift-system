package com.smartshift.dto.availability;

import com.smartshift.enums.AvailabilityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityRequest(
    @NotNull(message = "Ngày đăng ký không được để trống")
    LocalDate availableDate,

    @NotNull(message = "Giờ bắt đầu không được để trống")
    LocalTime startTime,

    @NotNull(message = "Giờ kết thúc không được để trống")
    LocalTime endTime,

    @NotNull(message = "Loại lịch rảnh không được để trống")
    AvailabilityType availabilityType,

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    String note
) {
}
