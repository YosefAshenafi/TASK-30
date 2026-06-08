package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an administrator changing a user's account status
 * (e.g. ACTIVE, LOCKED, PENDING, REJECTED).
 */
public record UserStatusRequest(
        @NotBlank(message = "status must not be blank")
        String status
) {}
