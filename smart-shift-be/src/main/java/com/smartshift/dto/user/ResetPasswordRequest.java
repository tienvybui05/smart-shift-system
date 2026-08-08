package com.smartshift.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, max = 72, message = "Mật khẩu mới phải từ 8 đến 72 ký tự")
    String newPassword,

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    String confirmPassword
) {
}
