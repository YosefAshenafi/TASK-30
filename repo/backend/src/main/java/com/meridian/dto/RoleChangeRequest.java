package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleChangeRequest(
        @NotBlank
        String roleName
) {}
