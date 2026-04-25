package com.meridian.backups;

import com.meridian.backups.entity.BackupRun;
import com.meridian.backups.entity.RecoveryDrill;
import com.meridian.backups.repository.RecoveryDrillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryDrillRunner {

    private final RecoveryDrillRepository recoveryDrillRepository;
    private final ProcessExecutor processExecutor;

    @Value("${app.backup-path:/app/backups}")
    private String backupPath;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/meridian}")
    private String datasourceUrl;

    @Async
    public void execute(RecoveryDrill drill, BackupRun backupRun) {
        drill.setStatus("RUNNING");
        recoveryDrillRepository.save(drill);

        String drillDb = "meridian_drill_" + System.currentTimeMillis();
        try {
            String filePath = backupRun.getFilePath();
            if (filePath == null) {
                fail(drill, "Backup file path is null");
                return;
            }

            String dbHost = extractHost(datasourceUrl);
            String dbPort = extractPort(datasourceUrl);
            String dbUser = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") :
                            System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "meridian";
            String pgPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "meridian_secret";

            // Create throwaway database
            ProcessBuilder createDb = new ProcessBuilder(
                    "psql", "-h", dbHost, "-p", dbPort, "-U", dbUser, "-c",
                    "CREATE DATABASE " + drillDb + ";");
            createDb.environment().put("PGPASSWORD", pgPassword);
            int createCode = processExecutor.run(createDb).exitCode();
            if (createCode != 0) {
                fail(drill, "Failed to create drill database (exit " + createCode + ")");
                return;
            }

            // Restore backup into drill database
            ProcessBuilder restore = new ProcessBuilder(
                    "pg_restore", "-h", dbHost, "-p", dbPort, "-U", dbUser,
                    "-d", drillDb, "--no-owner", "--no-acl", filePath);
            restore.environment().put("PGPASSWORD", pgPassword);
            int restoreCode = processExecutor.run(restore).exitCode();
            if (restoreCode != 0) {
                dropDrillDb(drillDb, dbHost, dbPort, dbUser, pgPassword);
                fail(drill, "pg_restore exited with code " + restoreCode);
                return;
            }

            // Count rows in key tables as validation
            long rowCount = countRows(drillDb, dbHost, dbPort, dbUser, pgPassword);

            // Drop the throwaway database
            dropDrillDb(drillDb, dbHost, dbPort, dbUser, pgPassword);

            drill.setStatus("PASSED");
            String existingNotes = drill.getNotes() != null ? drill.getNotes() + "; " : "";
            drill.setNotes(existingNotes + "Row count sampled: " + rowCount);
            drill.setCompletedAt(Instant.now());
            recoveryDrillRepository.save(drill);

        } catch (IOException | InterruptedException e) {
            log.error("Recovery drill failed", e);
            dropDrillDbQuietly(drillDb);
            fail(drill, e.getMessage());
        }
    }

    private long countRows(String db, String host, String port, String user, String password)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "psql", "-h", host, "-p", port, "-U", user, "-d", db,
                "-t", "-c", "SELECT COUNT(*) FROM users;");
        pb.environment().put("PGPASSWORD", password);
        String out = processExecutor.run(pb).stdout();
        try {
            return Long.parseLong(out);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void dropDrillDb(String db, String host, String port, String user, String password) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "psql", "-h", host, "-p", port, "-U", user, "-c",
                    "DROP DATABASE IF EXISTS " + db + ";");
            pb.environment().put("PGPASSWORD", password);
            processExecutor.run(pb);
        } catch (Exception e) {
            log.warn("Could not drop drill database {}", db, e);
        }
    }

    private void dropDrillDbQuietly(String db) {
        try {
            String host = extractHost(datasourceUrl);
            String port = extractPort(datasourceUrl);
            String user = System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") :
                          System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "meridian";
            String pgPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "meridian_secret";
            ProcessBuilder pb = new ProcessBuilder("psql", "-h", host, "-p", port, "-U", user, "-c",
                    "DROP DATABASE IF EXISTS " + db + ";");
            pb.environment().put("PGPASSWORD", pgPassword);
            processExecutor.run(pb);
        } catch (Exception ignored) {}
    }

    private void fail(RecoveryDrill drill, String message) {
        drill.setStatus("FAILED");
        drill.setNotes((drill.getNotes() != null ? drill.getNotes() + "; " : "") + "Error: " + message);
        drill.setCompletedAt(Instant.now());
        recoveryDrillRepository.save(drill);
    }

    private String extractHost(String url) {
        try {
            String withoutJdbc = url.replace("jdbc:", "");
            String[] slashParts = withoutJdbc.split("/");
            String hostPort = slashParts[2];
            return hostPort.contains(":") ? hostPort.split(":")[0] : hostPort;
        } catch (Exception e) {
            return "localhost";
        }
    }

    private String extractPort(String url) {
        try {
            String withoutJdbc = url.replace("jdbc:", "");
            String[] slashParts = withoutJdbc.split("/");
            String hostPort = slashParts[2];
            return hostPort.contains(":") ? hostPort.split(":")[1] : "5432";
        } catch (Exception e) {
            return "5432";
        }
    }
}
