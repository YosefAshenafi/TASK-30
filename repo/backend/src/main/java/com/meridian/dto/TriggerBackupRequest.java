package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;

public record TriggerBackupRequest(
        @NotBlank(message = "type must not be blank") String type
) {
}
