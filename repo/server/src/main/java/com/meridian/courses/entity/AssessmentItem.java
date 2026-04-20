package com.meridian.courses.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assessment_items")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false)
    private String stem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String choices;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal difficulty = new BigDecimal("0.500");

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal discrimination = new BigDecimal("0.000");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;
}
