package com.meridian.notifications.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "in_app_notifications")
@Getter
@Setter
@NoArgsConstructor
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload = "{}";

    @Column(name = "read_at")
    private Instant readAt;

    @Column(length = 10)
    private String severity = "INFO";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static InAppNotification of(UUID userId, String templateKey, String payloadJson) {
        InAppNotification n = new InAppNotification();
        n.userId = userId;
        n.templateKey = templateKey;
        n.payload = payloadJson;
        return n;
    }
}
