package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for an administrator creating a user account directly.
 * Unlike self-registration, the account is created already ACTIVE with the chosen role.
 * The role may be supplied with or without the Spring {@code ROLE_} prefix.
 */
public record CreateUserRequest(
        @NotBlank
        String username,

        @NotBlank
        @Size(min = 12, message = "Password must be at least 12 characters")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[!@#$%^&*]).*$",
                message = "Password must contain at least one number and one symbol"
        )
        String password,

        @NotBlank(message = "role must not be blank")
        String role,

        String organizationId
) {}
