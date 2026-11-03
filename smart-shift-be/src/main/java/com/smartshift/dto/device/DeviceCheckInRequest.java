package com.smartshift.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCheckInRequest(
    @NotBlank(message = "Device fingerprint không được để trống")
    @Size(max = 255, message = "Device fingerprint không được vượt quá 255 ký tự")
    String deviceFingerprint,

    @Size(max = 100, message = "Tên thiết bị không được vượt quá 100 ký tự")
    String deviceLabel,

    String deviceExplanation
) {
}
