package com.meridian.security.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "allowed_ip_ranges")
@Getter
@Setter
@NoArgsConstructor
public class AllowedIpRange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ColumnTransformer(write = "CAST(? AS inet)")
    @Column(nullable = false, columnDefinition = "inet")
    private String cidr;

    @Column(name = "role_scope", length = 20)
    private String roleScope;

    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
