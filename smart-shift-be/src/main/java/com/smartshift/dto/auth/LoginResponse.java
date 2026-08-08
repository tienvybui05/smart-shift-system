package com.smartshift.dto.auth;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    CurrentUserResponse user
) {
}
