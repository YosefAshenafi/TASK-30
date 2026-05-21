package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationTemplateUpdateRequest(
        @NotBlank(message = "subject is required")
        @Size(max = 255, message = "subject must be at most 255 characters")
        String subject,

        @NotBlank(message = "body is required")
        String body
) {
}
