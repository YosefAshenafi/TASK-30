package com.meridian.notifications.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        UUID recipientId,
        String templateKey,
        String payload,
        RenderedContent rendered,
        String severity,
        Instant readAt,
        Instant createdAt
) {
    public record RenderedContent(String subject, String bodyHtml) {}
}
