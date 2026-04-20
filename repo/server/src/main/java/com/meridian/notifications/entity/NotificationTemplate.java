package com.meridian.notifications.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplate {

    @Id
    private String key;

    @Column(name = "title_tmpl", nullable = false)
    private String titleTmpl;

    @Column(name = "body_tmpl", nullable = false)
    private String bodyTmpl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String variables = "[]";

    @Column(name = "updated_by")
    private java.util.UUID updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
