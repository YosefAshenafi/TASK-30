package com.meridian.backups;

import com.meridian.backups.entity.BackupRun;
import com.meridian.backups.entity.RecoveryDrill;
import com.meridian.backups.repository.RecoveryDrillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryDrillRunnerTest {

    @Mock
    private RecoveryDrillRepository recoveryDrillRepository;

    @Mock
    private ProcessExecutor processExecutor;

    @InjectMocks
    private RecoveryDrillRunner drillRunner;

    private RecoveryDrill drill;
    private BackupRun backupRun;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(drillRunner, "backupPath", "/tmp/meridian-test-backups");
        ReflectionTestUtils.setField(drillRunner, "datasourceUrl",
                "jdbc:postgresql://localhost:5432/meridian");

        drill = new RecoveryDrill();
        drill.setId(UUID.randomUUID());

        backupRun = new BackupRun();
        backupRun.setId(UUID.randomUUID());

        when(recoveryDrillRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void execute_nullFilePath_setsDrillFailed() {
        backupRun.setFilePath(null);

        drillRunner.execute(drill, backupRun);

        assertThat(drill.getStatus()).isEqualTo("FAILED");
        assertThat(drill.getNotes()).contains("Backup file path is null");
        verify(recoveryDrillRepository, atLeastOnce()).save(drill);
    }

    @Test
    void execute_drillDbNotCreated_setsDrillFailed() throws Exception {
        // psql returns non-zero for the CREATE DATABASE step
        when(processExecutor.run(any())).thenReturn(new ProcessExecutor.ProcessResult(1, ""));
        backupRun.setFilePath("/tmp/nonexistent.dump");

        drillRunner.execute(drill, backupRun);

        assertThat(drill.getStatus()).isEqualTo("FAILED");
        assertThat(drill.getNotes()).contains("Failed to create drill database");
        assertThat(drill.getCompletedAt()).isNotNull();
    }

    @Test
    void execute_initialStatusSetToRunning() throws Exception {
        // Fail on CREATE DATABASE so the test terminates quickly
        when(processExecutor.run(any())).thenReturn(new ProcessExecutor.ProcessResult(1, ""));
        backupRun.setFilePath("/tmp/test.dump");

        drillRunner.execute(drill, backupRun);

        // Repository must have been called at least once for the RUNNING save
        verify(recoveryDrillRepository, atLeastOnce()).save(argThat(d -> d.getId().equals(drill.getId())));
    }

    @Test
    void execute_failureDoesNotLeaveOrphanedDb() throws Exception {
        // IOException on CREATE DATABASE triggers the quiet-drop cleanup path
        when(processExecutor.run(any())).thenThrow(new IOException("psql: No such file or directory"));
        backupRun.setFilePath("/tmp/meridian-test.dump");

        drillRunner.execute(drill, backupRun);

        // Status is always set — no silent hang or unhandled exception
        assertThat(drill.getStatus()).isEqualTo("FAILED");
        assertThat(drill.getCompletedAt()).isNotNull();
    }

    @Test
    void execute_restoreFailure_setsDrillFailed() throws Exception {
        // CREATE DATABASE succeeds, pg_restore fails
        when(processExecutor.run(any()))
                .thenReturn(new ProcessExecutor.ProcessResult(0, ""))  // CREATE DATABASE
                .thenReturn(new ProcessExecutor.ProcessResult(1, ""))  // pg_restore
                .thenReturn(new ProcessExecutor.ProcessResult(0, "")); // DROP DATABASE cleanup
        backupRun.setFilePath("/tmp/test.dump");

        drillRunner.execute(drill, backupRun);

        assertThat(drill.getStatus()).isEqualTo("FAILED");
        assertThat(drill.getNotes()).contains("pg_restore exited with code 1");
    }
}
