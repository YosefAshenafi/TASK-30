package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public record DraftAssessmentSyncRequest(
        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey,

        UUID sessionId,
        UUID itemId,
        String answer,
        boolean flagged,
        int timeSpentSecs,
        Instant lastModified
) {
}
