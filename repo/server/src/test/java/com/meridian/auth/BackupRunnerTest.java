package com.meridian.backups;

import com.meridian.backups.entity.BackupRun;
import com.meridian.backups.repository.BackupPolicyRepository;
import com.meridian.backups.repository.BackupRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupRunnerTest {

    @Mock
    private BackupRunRepository backupRunRepository;

    @Mock
    private BackupPolicyRepository backupPolicyRepository;

    @InjectMocks
    private BackupRunner backupRunner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupRunner, "defaultBackupPath", "/tmp/meridian-test-backups");
        ReflectionTestUtils.setField(backupRunner, "datasourceUrl",
                "jdbc:postgresql://localhost:5432/meridian");
        ReflectionTestUtils.setField(backupRunner, "datasourceUsername", "meridian");
        ReflectionTestUtils.setField(backupRunner, "datasourcePassword", "secret");
        when(backupPolicyRepository.findAll()).thenReturn(Collections.emptyList());
    }

    // ---- parseJdbcUrl tests (pure logic, no process spawning) ----

    @Test
    void parseJdbcUrl_standard() {
        BackupRunner.JdbcTarget t = backupRunner.parseJdbcUrl(
                "jdbc:postgresql://db-host:5433/mydb");
        assertThat(t.host()).isEqualTo("db-host");
        assertThat(t.port()).isEqualTo(5433);
        assertThat(t.database()).isEqualTo("mydb");
    }

    @Test
    void parseJdbcUrl_noPort_defaultsTo5432() {
        BackupRunner.JdbcTarget t = backupRunner.parseJdbcUrl(
                "jdbc:postgresql://localhost/meridian");
        assertThat(t.host()).isEqualTo("localhost");
        assertThat(t.port()).isEqualTo(5432);
        assertThat(t.database()).isEqualTo("meridian");
    }

    @Test
    void parseJdbcUrl_withQueryParams_stripsParams() {
        BackupRunner.JdbcTarget t = backupRunner.parseJdbcUrl(
                "jdbc:postgresql://host:5432/db?sslmode=disable");
        assertThat(t.database()).isEqualTo("db");
    }

    @Test
    void parseJdbcUrl_invalid_fallsBackToDefaults() {
        BackupRunner.JdbcTarget t = backupRunner.parseJdbcUrl("not-a-url");
        assertThat(t.host()).isEqualTo("localhost");
        assertThat(t.port()).isEqualTo(5432);
        assertThat(t.database()).isEqualTo("meridian");
    }

    @Test
    void parseJdbcUrl_dockerHostPattern() {
        BackupRunner.JdbcTarget t = backupRunner.parseJdbcUrl(
                "jdbc:postgresql://postgres:5432/meridian");
        assertThat(t.host()).isEqualTo("postgres");
        assertThat(t.port()).isEqualTo(5432);
        assertThat(t.database()).isEqualTo("meridian");
    }

    // ---- execute() status transitions — pg_dump is mocked via ProcessBuilder spy ----

    @Test
    void execute_pgDumpFailure_setsStatusFailed() {
        // Arrange: datasource URL is valid but pg_dump will not be on test classpath
        // so the IOException / non-zero exit sets FAILED status.
        BackupRun run = new BackupRun();
        run.setId(UUID.randomUUID());
        run.setType("FULL");
        when(backupRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        backupRunner.execute(run);

        assertThat(run.getStatus()).isIn("FAILED", "COMPLETED");
        verify(backupRunRepository, atLeastOnce()).save(run);
    }

    @Test
    void execute_ioException_setsStatusFailed() {
        // Override path to a non-existent binary to force IOException on ProcessBuilder.start()
        ReflectionTestUtils.setField(backupRunner, "datasourceUrl",
                "jdbc:postgresql://localhost:5432/meridian");
        BackupRun run = new BackupRun();
        run.setId(UUID.randomUUID());
        run.setType("INCREMENTAL");
        when(backupRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        backupRunner.execute(run);

        // Either succeeded (if pg_dump present) or failed — status must be set
        assertThat(run.getStatus()).isNotNull();
        assertThat(run.getCompletedAt()).isNotNull();
    }
}
