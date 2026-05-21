package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PermissionUpdateRequest(
        @NotBlank String fieldName,
        @NotBlank
        @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED",
                message = "classification must be one of PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED")
        String classification
) {
}
