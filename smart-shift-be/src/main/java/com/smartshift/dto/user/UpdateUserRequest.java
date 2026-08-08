package com.smartshift.dto.user;

import com.smartshift.enums.EmploymentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateUserRequest(
    @NotBlank(message = "Mã nhân viên không được để trống")
    @Size(max = 30, message = "Mã nhân viên không được vượt quá 30 ký tự")
    String employeeCode,

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(max = 50, message = "Tên đăng nhập không được vượt quá 50 ký tự")
    String username,

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    String fullName,

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    String email,

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    String phoneNumber,

    @NotNull(message = "Vai trò không được để trống")
    @Positive(message = "Id vai trò phải lớn hơn 0")
    Long roleId,

    @NotNull(message = "Chi nhánh không được để trống")
    @Positive(message = "Id chi nhánh phải lớn hơn 0")
    Long locationId,

    @NotNull(message = "Vị trí không được để trống")
    @Positive(message = "Id vị trí phải lớn hơn 0")
    Long positionId,

    @NotNull(message = "Loại hợp đồng không được để trống")
    EmploymentType employmentType,

    @NotNull(message = "Ngày vào làm không được để trống")
    @PastOrPresent(message = "Ngày vào làm không được ở tương lai")
    LocalDate hireDate,

    @NotNull(message = "Giờ tối thiểu mỗi tuần không được để trống")
    @DecimalMin(value = "0.0", message = "Giờ tối thiểu mỗi tuần không được âm")
    @DecimalMax(value = "168.0", message = "Giờ tối thiểu mỗi tuần không được vượt quá 168")
    @Digits(integer = 3, fraction = 2, message = "Giờ tối thiểu mỗi tuần chỉ được có tối đa 2 số lẻ")
    BigDecimal minHoursPerWeek,

    @NotNull(message = "Giờ tối đa mỗi tuần không được để trống")
    @DecimalMin(value = "0.01", message = "Giờ tối đa mỗi tuần phải lớn hơn 0")
    @DecimalMax(value = "168.0", message = "Giờ tối đa mỗi tuần không được vượt quá 168")
    @Digits(integer = 3, fraction = 2, message = "Giờ tối đa mỗi tuần chỉ được có tối đa 2 số lẻ")
    BigDecimal maxHoursPerWeek,

    @NotNull(message = "Giờ tối đa mỗi ngày không được để trống")
    @DecimalMin(value = "0.01", message = "Giờ tối đa mỗi ngày phải lớn hơn 0")
    @DecimalMax(value = "24.0", message = "Giờ tối đa mỗi ngày không được vượt quá 24")
    @Digits(integer = 2, fraction = 2, message = "Giờ tối đa mỗi ngày chỉ được có tối đa 2 số lẻ")
    BigDecimal maxHoursPerDay,

    @NotNull(message = "Giờ nghỉ tối thiểu không được để trống")
    @DecimalMin(value = "0.0", message = "Giờ nghỉ tối thiểu không được âm")
    @DecimalMax(value = "24.0", message = "Giờ nghỉ tối thiểu không được vượt quá 24")
    @Digits(integer = 2, fraction = 2, message = "Giờ nghỉ tối thiểu chỉ được có tối đa 2 số lẻ")
    BigDecimal minRestHours,

    @NotNull(message = "Số ngày làm liên tiếp không được để trống")
    @Min(value = 1, message = "Số ngày làm liên tiếp phải lớn hơn 0")
    @Max(value = 7, message = "Số ngày làm liên tiếp không được vượt quá 7")
    Short maxConsecutiveDays
) {
}
