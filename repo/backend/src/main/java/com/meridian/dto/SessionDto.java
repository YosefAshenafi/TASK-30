package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SessionDto {

    public record CreateSessionRequest(
            @NotNull(message = "courseId is required")
            UUID courseId,
            Integer restTimerSecs
    ) {}

    public record SessionResponse(
            UUID id,
            UUID userId,
            UUID courseId,
            String status,
            int restTimerSecs,
            Instant startedAt,
            Instant completedAt,
            String syncStatus,
            List<SessionActivityDto> activities
    ) {}

    public record SessionActivityDto(
            UUID activityId,
            String name,
            boolean completed
    ) {}

    public record UpdateSessionRequest(
            Integer restTimerSecs,
            List<ActivityUpdate> activities
    ) {}

    public record ActivityUpdate(
            UUID activityId,
            boolean completed
    ) {}

    public record SyncSessionRequest(
            @NotBlank(message = "idempotencyKey is required")
            String idempotencyKey,
            UUID courseId,
            String courseVersion,
            String status,
            int restTimerSecs,
            Instant startedAt,
            Instant completedAt,
            List<ActivityState> activities,
            Instant clientUpdatedAt
    ) {}

    public record ActivityState(
            String activityRef,
            boolean completed
    ) {}

    public record SyncResult(
            int accepted,
            int rejected,
            int duplicates,
            List<String> rejectedKeys
    ) {}
}
