package com.meridian.dto;

import com.meridian.entity.Backup;

import java.time.Instant;
import java.util.UUID;

public record BackupDto(
        UUID id,
        String type,
        String path,
        Long sizeBytes,
        Instant retentionUntil,
        Instant createdAt
) {
    public static BackupDto from(Backup backup) {
        return new BackupDto(
                backup.getId(),
                backup.getType(),
                backup.getPath(),
                backup.getSizeBytes(),
                backup.getRetentionUntil(),
                backup.getCreatedAt()
        );
    }
}
