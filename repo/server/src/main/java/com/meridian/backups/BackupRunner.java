package com.meridian.backups;

import com.meridian.backups.entity.BackupPolicy;
import com.meridian.backups.entity.BackupRun;
import com.meridian.backups.repository.BackupPolicyRepository;
import com.meridian.backups.repository.BackupRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupRunner {

    private final BackupRunRepository backupRunRepository;
    private final BackupPolicyRepository backupPolicyRepository;
    private final ProcessExecutor processExecutor;

    @Value("${app.backup-path:/app/backups}")
    private String defaultBackupPath;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/meridian}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:meridian}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Async
    public void execute(BackupRun run) {
        String backupPath = resolveBackupPath();
        try {
            Path dir = Path.of(backupPath);
            Files.createDirectories(dir);

            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    .replace(":", "-").replace(".", "-");
            String filename = timestamp + "-" + run.getType().toLowerCase() + ".dump";
            String filePath = backupPath + "/" + filename;

            JdbcTarget target = parseJdbcUrl(datasourceUrl);
            ProcessBuilder pb = new ProcessBuilder(
                    "pg_dump",
                    "-h", target.host,
                    "-p", String.valueOf(target.port),
                    "-U", datasourceUsername,
                    "-d", target.database,
                    "-Fc",
                    "-f", filePath);
            pb.redirectErrorStream(true);
            pb.environment().put("PGPASSWORD",
                    datasourcePassword != null && !datasourcePassword.isEmpty()
                            ? datasourcePassword
                            : System.getenv().getOrDefault("DB_PASSWORD", ""));
            int exitCode = processExecutor.run(pb).exitCode();

            if (exitCode == 0) {
                Path dumpFile = Path.of(filePath);
                long size = Files.exists(dumpFile) ? Files.size(dumpFile) : 0;
                run.setFilePath(filePath);
                run.setSizeBytes(size);
                run.setStatus("COMPLETED");
                run.setCompletedAt(Instant.now());
            } else {
                run.setStatus("FAILED");
                run.setErrorMessage("pg_dump exited with code " + exitCode);
                run.setCompletedAt(Instant.now());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Backup failed", e);
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
        }
        backupRunRepository.save(run);
    }

    private String resolveBackupPath() {
        return backupPolicyRepository.findAll().stream()
                .findFirst()
                .map(BackupPolicy::getBackupPath)
                .filter(p -> p != null && !p.isBlank())
                .orElse(defaultBackupPath);
    }

    JdbcTarget parseJdbcUrl(String url) {
        String stripped = url.startsWith("jdbc:") ? url.substring("jdbc:".length()) : url;
        try {
            URI uri = URI.create(stripped);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return new JdbcTarget("localhost", 5432, "meridian");
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() != null ? uri.getPath() : "";
            String database = path.startsWith("/") ? path.substring(1) : path;
            int q = database.indexOf('?');
            if (q >= 0) database = database.substring(0, q);
            if (database.isBlank()) database = "meridian";
            return new JdbcTarget(host, port, database);
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse datasource URL {}, falling back to defaults", url);
            return new JdbcTarget("localhost", 5432, "meridian");
        }
    }

    record JdbcTarget(String host, int port, String database) {}
}
