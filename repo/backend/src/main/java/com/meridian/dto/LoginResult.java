package com.meridian.dto;

/**
 * Internal result of an AuthService.login or refresh operation.
 * Carries both the public AuthResponse and the raw refresh token value
 * so the controller can place the token in an HttpOnly cookie.
 */
public record LoginResult(
        AuthResponse authResponse,
        String rawRefreshToken
) {}
