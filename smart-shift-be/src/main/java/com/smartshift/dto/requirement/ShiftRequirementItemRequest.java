package com.smartshift.dto.requirement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShiftRequirementItemRequest(
    @NotNull(message = "Vị trí không được để trống")
    @Positive(message = "Id vị trí phải lớn hơn 0")
    Long positionId,

    @NotNull(message = "Số nhân viên tối thiểu không được để trống")
    @Min(value = 0, message = "Số nhân viên tối thiểu không được âm")
    @Max(value = 1000, message = "Số nhân viên tối thiểu không được vượt quá 1000")
    Short minEmployees,

    @NotNull(message = "Số nhân viên tối đa không được để trống")
    @Min(value = 1, message = "Số nhân viên tối đa phải lớn hơn 0")
    @Max(value = 1000, message = "Số nhân viên tối đa không được vượt quá 1000")
    Short maxEmployees,

    @NotNull(message = "Mức ưu tiên không được để trống")
    @Min(value = 1, message = "Mức ưu tiên phải từ 1 đến 10")
    @Max(value = 10, message = "Mức ưu tiên phải từ 1 đến 10")
    Short priority
) {
}
