package com.meridian.reports.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_runs")
@Getter
@Setter
@NoArgsConstructor
public class ReportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String parameters = "{}";

    @Column(nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(nullable = false, length = 20)
    private String classification = "INTERNAL";

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
