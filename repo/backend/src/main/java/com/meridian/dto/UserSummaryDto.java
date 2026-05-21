package com.meridian.dto;

import com.meridian.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String username,
        UserStatus status,
        String role,
        UUID orgId,
        Instant pendingDeadlineAt,
        boolean overdue,
        String maskedEmployeeId,
        String maskedContact
) {}
