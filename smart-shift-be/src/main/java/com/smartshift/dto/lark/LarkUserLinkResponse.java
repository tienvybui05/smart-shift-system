package com.smartshift.dto.lark;

import java.time.Instant;

public record LarkUserLinkResponse(
    Long userId,
    String employeeCode,
    String fullName,
    String email,
    String phoneNumber,
    String roleName,
    String locationName,
    boolean active,
    boolean linked,
    String larkOpenId,
    Instant syncedAt,
    String syncError
) {
}
