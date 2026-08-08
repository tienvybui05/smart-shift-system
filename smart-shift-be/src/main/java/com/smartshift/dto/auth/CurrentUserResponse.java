package com.smartshift.dto.auth;

public record CurrentUserResponse(
    Long id,
    String employeeCode,
    String username,
    String fullName,
    String role,
    Long locationId,
    String locationName,
    Long positionId,
    String positionName
) {
}
