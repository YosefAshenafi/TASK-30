package com.meridian.service;

import com.meridian.entity.AuditEvent;
import com.meridian.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Async
    public void logEvent(UUID userId,
                         String eventType,
                         String entityType,
                         String entityId,
                         String details,
                         String ipAddress,
                         String fingerprint) {
        try {
            AuditEvent event = new AuditEvent();
            event.setUserId(userId);
            event.setEventType(eventType);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setDetails(details);
            event.setIpAddress(ipAddress);
            event.setDeviceFingerprint(fingerprint);
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Audit log failed for userId={} eventType={}: {}", userId, eventType, e.getMessage());
        }
    }
}
