package com.smartshift.dto.device;

import com.smartshift.enums.DeviceStatus;

import java.time.Instant;

public record EmployeeDeviceResponse(
    Long id,
    Long employeeId,
    String employeeCode,
    String employeeName,
    String deviceFingerprint,
    String deviceLabel,
    DeviceStatus status,
    String deviceExplanation,
    Instant registeredAt,
    Long approvedById,
    String approvedByName,
    Instant approvedAt,
    Instant lastSeenAt,
    Instant createdAt,
    Instant updatedAt
) {
}
