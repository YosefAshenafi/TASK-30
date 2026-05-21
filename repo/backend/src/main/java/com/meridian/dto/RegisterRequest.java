package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        String username,

        @NotBlank
        @Size(min = 12, message = "Password must be at least 12 characters")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[!@#$%^&*]).*$",
                message = "Password must contain at least one number and one symbol"
        )
        String password,

        String organizationId
) {}
