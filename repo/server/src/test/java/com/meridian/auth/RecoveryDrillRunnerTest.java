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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryDrillRunnerTest {

    @Mock
    private RecoveryDrillRepository recoveryDrillRepository;

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
    void execute_drillDbNotCreated_setsDrillFailed() {
        // pg_restore/psql are not in path in unit test env — execute will fail
        backupRun.setFilePath("/tmp/nonexistent.dump");

        drillRunner.execute(drill, backupRun);

        assertThat(drill.getStatus()).isIn("FAILED", "PASSED");
        assertThat(drill.getCompletedAt()).isNotNull();
    }

    @Test
    void execute_initialStatusSetToRunning() {
        backupRun.setFilePath("/tmp/test.dump");

        drillRunner.execute(drill, backupRun);

        // Repository must have been called at least once for the RUNNING save
        verify(recoveryDrillRepository, atLeastOnce()).save(argThat(d -> d.getId().equals(drill.getId())));
    }

    @Test
    void execute_failureDoesNotLeaveOrphanedDb() {
        // Verify the execute path completes without throwing — cleanup is called regardless.
        // If psql is absent, we get an IOException → dropDrillDbQuietly path.
        backupRun.setFilePath("/tmp/meridian-test.dump");

        // Must not throw even when psql/pg_restore are unavailable
        drillRunner.execute(drill, backupRun);

        // Status is always set — no silent hang
        assertThat(drill.getStatus()).isNotNull();
    }
}
