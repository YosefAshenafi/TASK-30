package com.meridian.dto;

import com.meridian.entity.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        UUID userId,
        String eventType,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        String deviceFingerprint,
        Instant createdAt
) {
    public static AuditEventDto from(AuditEvent event) {
        return new AuditEventDto(
                event.getId(),
                event.getUserId(),
                event.getEventType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getDetails(),
                event.getIpAddress(),
                event.getDeviceFingerprint(),
                event.getCreatedAt()
        );
    }
}
