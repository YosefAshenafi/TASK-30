package com.meridian.approvals.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

    @Column(name = "decided_at")
    private Instant decidedAt;

    public static ApprovalRequest create(String type, String payload, UUID requestedBy) {
        ApprovalRequest ar = new ApprovalRequest();
        ar.type = type;
        ar.payload = payload;
        ar.requestedBy = requestedBy;
        ar.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        return ar;
    }
}
