package com.meridian.sessions.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    private String key;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static IdempotencyKey of(String key, UUID userId, String hash, String responseJson) {
        IdempotencyKey ik = new IdempotencyKey();
        ik.key = key;
        ik.userId = userId;
        ik.requestHash = hash;
        ik.responseJson = responseJson;
        return ik;
    }
}
