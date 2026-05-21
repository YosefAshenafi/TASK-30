package com.meridian.service;

import com.meridian.entity.Backup;
import com.meridian.exception.AppException;
import com.meridian.repository.BackupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final BackupRepository backupRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Value("${meridian.backup.path:/var/meridian/backups}")
    private String defaultBackupPath;

    @Value("${meridian.backup.retention-days:30}")
    private int defaultRetentionDays;

    @Value("${DATABASE_URL:postgresql://postgres:postgres@localhost:5432/meridian}")
    private String databaseUrl;

    public BackupService(JdbcTemplate jdbcTemplate,
                         BackupRepository backupRepository,
                         AuditService auditService,
                         NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.backupRepository = backupRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Triggers a database backup of the given type (FULL or INCREMENTAL).
     * Executes pg_dump as an external process and records the result.
     */
    public Backup triggerBackup(String type) {
        String backupPath = resolveBackupPath();
        String timestamp = TIMESTAMP_FMT.format(Instant.now());
        String filename = "meridian_" + type.toLowerCase() + "_" + timestamp + ".pgdump";
        String fullPath = backupPath + File.separator + filename;

        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = databaseUrl;
        }

        Map<String, String> dbParams = parseDatabaseUrl(dbUrl);
        String host = dbParams.getOrDefault("host", "localhost");
        String port = dbParams.getOrDefault("port", "5432");
        String user = dbParams.getOrDefault("user", "postgres");
        String password = dbParams.getOrDefault("password", "postgres");
        String db = dbParams.getOrDefault("db", "meridian");

        List<String> command = buildDumpCommand(type, host, port, user, db, fullPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("PGPASSWORD", password);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw AppException.badRequest("Backup failed with exit code: " + exitCode);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw AppException.badRequest("Backup failed: " + e.getMessage());
        }

        long sizeBytes = 0L;
        File backupFile = new File(fullPath);
        if (backupFile.exists()) {
            sizeBytes = backupFile.length();
        }

        int retentionDays = resolveRetentionDays();
        Instant retentionUntil = Instant.now().plusSeconds((long) retentionDays * 86400);

        Backup backup = new Backup();
        backup.setType(type);
        backup.setPath(fullPath);
        backup.setSizeBytes(sizeBytes);
        backup.setRetentionUntil(retentionUntil);
        Backup saved = backupRepository.save(backup);

        auditService.logEvent(null, "BACKUP_TRIGGERED", "Backup",
                saved.getId().toString(),
                "{\"type\":\"" + type + "\",\"path\":\"" + fullPath + "\"}", null, null);

        log.info("Backup completed: type={} path={} size={}", type, fullPath, sizeBytes);
        return saved;
    }

    /**
     * Returns paginated backup history ordered by most recent first.
     */
    @Transactional(readOnly = true)
    public Page<Backup> getHistory(Pageable pageable) {
        return backupRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Deletes expired backup files and records. Called by the Quartz pruning job.
     */
    public void pruneExpired() {
        List<Backup> expired = backupRepository.findByRetentionUntilBefore(Instant.now());
        for (Backup backup : expired) {
            try {
                File file = new File(backup.getPath());
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        log.warn("Could not delete backup file: {}", backup.getPath());
                    }
                }
                backupRepository.delete(backup);
                log.info("Pruned expired backup: id={} path={}", backup.getId(), backup.getPath());
            } catch (Exception e) {
                log.warn("Error pruning backup id={}: {}", backup.getId(), e.getMessage());
            }
        }
    }

    /**
     * Parses a PostgreSQL connection URL of the form
     * {@code postgresql://user:password@host:port/database} into a parameter map.
     */
    public Map<String, String> parseDatabaseUrl(String url) {
        Map<String, String> result = new HashMap<>();
        if (url == null || url.isBlank()) {
            return result;
        }
        try {
            String stripped = url;
            if (stripped.startsWith("postgresql://")) {
                stripped = stripped.substring("postgresql://".length());
            } else if (stripped.startsWith("postgres://")) {
                stripped = stripped.substring("postgres://".length());
            }

            // Extract user:password@host:port/db
            int atIndex = stripped.lastIndexOf('@');
            if (atIndex >= 0) {
                String credentials = stripped.substring(0, atIndex);
                stripped = stripped.substring(atIndex + 1);
                int colonIdx = credentials.indexOf(':');
                if (colonIdx >= 0) {
                    result.put("user", credentials.substring(0, colonIdx));
                    result.put("password", credentials.substring(colonIdx + 1));
                } else {
                    result.put("user", credentials);
                }
            }

            // Now stripped is host:port/db
            int slashIdx = stripped.indexOf('/');
            String hostPort = slashIdx >= 0 ? stripped.substring(0, slashIdx) : stripped;
            String dbPart = slashIdx >= 0 ? stripped.substring(slashIdx + 1) : "";

            // Remove any query string from db
            int queryIdx = dbPart.indexOf('?');
            if (queryIdx >= 0) {
                dbPart = dbPart.substring(0, queryIdx);
            }
            result.put("db", dbPart);

            int portColon = hostPort.lastIndexOf(':');
            if (portColon >= 0) {
                result.put("host", hostPort.substring(0, portColon));
                result.put("port", hostPort.substring(portColon + 1));
            } else {
                result.put("host", hostPort);
                result.put("port", "5432");
            }
        } catch (Exception e) {
            log.warn("Failed to parse DATABASE_URL '{}': {}", url, e.getMessage());
        }
        return result;
    }

    private String resolveBackupPath() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT value FROM security_policies WHERE key = 'backup_path'",
                    String.class);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        } catch (Exception e) {
            log.debug("backup_path not found in security_policies, using default");
        }
        return defaultBackupPath;
    }

    private int resolveRetentionDays() {
        try {
            String value = jdbcTemplate.queryForObject(
                    "SELECT value FROM security_policies WHERE key = 'backup_retention_days'",
                    String.class);
            if (value != null && !value.isBlank()) {
                return Integer.parseInt(value.trim());
            }
        } catch (Exception e) {
            log.debug("backup_retention_days not found in security_policies, using default");
        }
        return defaultRetentionDays;
    }

    private List<String> buildDumpCommand(String type, String host, String port,
                                          String user, String db, String outputPath) {
        if ("FULL".equalsIgnoreCase(type)) {
            return List.of(
                    "pg_dump",
                    "--format=custom",
                    "--no-password",
                    "-h", host,
                    "-p", port,
                    "-U", user,
                    "-d", db,
                    "-f", outputPath
            );
        } else {
            // INCREMENTAL: schema-only dump (simplified for non-enterprise PostgreSQL)
            return List.of(
                    "pg_dump",
                    "--schema-only",
                    "--no-password",
                    "-h", host,
                    "-p", port,
                    "-U", user,
                    "-d", db,
                    "-f", outputPath
            );
        }
    }
}
