package com.meridian.repository;

import com.meridian.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByUserId(UUID userId, Pageable pageable);

    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);

    long countByUserIdAndEventTypeAndCreatedAtAfter(UUID userId, String eventType, Instant after);
}
