package com.smartshift.dto.attendance;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AttendanceGpsRequest(
    @NotNull(message = "Phân công ca không được để trống")
    @Positive(message = "Id phân công ca phải lớn hơn 0")
    Long shiftAssignmentId,

    @NotNull(message = "Vĩ độ không được để trống")
    @DecimalMin(value = "-90.0", message = "Vĩ độ phải từ -90 đến 90")
    @DecimalMax(value = "90.0", message = "Vĩ độ phải từ -90 đến 90")
    BigDecimal latitude,

    @NotNull(message = "Kinh độ không được để trống")
    @DecimalMin(value = "-180.0", message = "Kinh độ phải từ -180 đến 180")
    @DecimalMax(value = "180.0", message = "Kinh độ phải từ -180 đến 180")
    BigDecimal longitude,

    @NotNull(message = "Độ chính xác GPS không được để trống")
    @DecimalMin(value = "0.0", message = "Độ chính xác GPS không được âm")
    @DecimalMax(value = "5000.0", message = "Độ chính xác GPS không hợp lệ")
    BigDecimal accuracyMeters
) {
}
