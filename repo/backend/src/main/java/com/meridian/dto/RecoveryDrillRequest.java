package com.meridian.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record RecoveryDrillRequest(
        @NotNull(message = "drillDate is required")
        LocalDate drillDate,

        @Min(value = 0, message = "stepsCompleted must be >= 0")
        int stepsCompleted,

        @Min(value = 1, message = "totalSteps must be >= 1")
        @Max(value = 100, message = "totalSteps must be <= 100")
        int totalSteps,

        @NotNull(message = "outcome is required")
        @Pattern(regexp = "PASS|FAIL|PARTIAL", message = "outcome must be PASS, FAIL, or PARTIAL")
        String outcome,

        String notes
) {
}
