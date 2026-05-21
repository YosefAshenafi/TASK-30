package com.meridian.controller;

import com.meridian.dto.AuditEventDto;
import com.meridian.entity.AuditEvent;
import com.meridian.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    public AuditController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Returns a paginated list of audit events, optionally filtered by userId or eventType.
     */
    @GetMapping("/events")
    public ResponseEntity<Page<AuditEventDto>> getEvents(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String eventType,
            Pageable pageable) {

        Page<AuditEvent> page;
        if (userId != null) {
            page = auditEventRepository.findByUserId(userId, pageable);
        } else if (eventType != null && !eventType.isBlank()) {
            page = auditEventRepository.findByEventType(eventType, pageable);
        } else {
            page = auditEventRepository.findAll(pageable);
        }

        return ResponseEntity.ok(page.map(AuditEventDto::from));
    }
}
