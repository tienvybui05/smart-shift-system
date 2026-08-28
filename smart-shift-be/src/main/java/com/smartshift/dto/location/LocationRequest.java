package com.smartshift.dto.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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

    @DecimalMin(value = "-90.0", message = "Vĩ độ phải từ -90 đến 90")
    @DecimalMax(value = "90.0", message = "Vĩ độ phải từ -90 đến 90")
    BigDecimal latitude,

    @DecimalMin(value = "-180.0", message = "Kinh độ phải từ -180 đến 180")
    @DecimalMax(value = "180.0", message = "Kinh độ phải từ -180 đến 180")
    BigDecimal longitude,

    @Min(value = 10, message = "Bán kính chấm công tối thiểu là 10 mét")
    @Max(value = 5000, message = "Bán kính chấm công tối đa là 5000 mét")
    Integer attendanceRadiusMeters,

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    Boolean active
) {
}
