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

import java.io.IOException;
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

    @Mock
    private ProcessExecutor processExecutor;

    @InjectMocks
    private BackupRunner backupRunner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupRunner, "defaultBackupPath", "/tmp/meridian-test-backups");
        ReflectionTestUtils.setField(backupRunner, "datasourceUrl",
                "jdbc:postgresql://localhost:5432/meridian");
        ReflectionTestUtils.setField(backupRunner, "datasourceUsername", "meridian");
        ReflectionTestUtils.setField(backupRunner, "datasourcePassword", "secret");
        lenient().when(backupPolicyRepository.findAll()).thenReturn(Collections.emptyList());
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

    // ---- execute() status transitions — pg_dump is fully mocked ----

    @Test
    void execute_pgDumpFailure_setsStatusFailed() throws Exception {
        when(processExecutor.run(any())).thenReturn(new ProcessExecutor.ProcessResult(1, "pg_dump: connection refused"));
        when(backupRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BackupRun run = new BackupRun();
        run.setId(UUID.randomUUID());
        run.setType("FULL");

        backupRunner.execute(run);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorMessage()).contains("pg_dump exited with code 1");
        assertThat(run.getCompletedAt()).isNotNull();
        verify(backupRunRepository, atLeastOnce()).save(run);
    }

    @Test
    void execute_pgDumpSuccess_setsStatusCompleted() throws Exception {
        when(processExecutor.run(any())).thenReturn(new ProcessExecutor.ProcessResult(0, ""));
        when(backupRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BackupRun run = new BackupRun();
        run.setId(UUID.randomUUID());
        run.setType("FULL");

        backupRunner.execute(run);

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getCompletedAt()).isNotNull();
        verify(backupRunRepository, atLeastOnce()).save(run);
    }

    @Test
    void execute_ioException_setsStatusFailed() throws Exception {
        when(processExecutor.run(any())).thenThrow(new IOException("pg_dump: No such file or directory"));
        when(backupRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BackupRun run = new BackupRun();
        run.setId(UUID.randomUUID());
        run.setType("INCREMENTAL");

        backupRunner.execute(run);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorMessage()).contains("pg_dump: No such file or directory");
        assertThat(run.getCompletedAt()).isNotNull();
    }
}
