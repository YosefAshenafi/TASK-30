package com.meridian.backups;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.backups.entity.BackupPolicy;
import com.meridian.backups.entity.BackupRun;
import com.meridian.backups.entity.RecoveryDrill;
import com.meridian.backups.repository.BackupPolicyRepository;
import com.meridian.backups.repository.BackupRunRepository;
import com.meridian.backups.repository.RecoveryDrillRepository;
import com.meridian.notifications.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupSchedulerTest {

    @Mock
    private BackupPolicyRepository policyRepository;
    @Mock
    private BackupRunRepository backupRunRepository;
    @Mock
    private RecoveryDrillRepository recoveryDrillRepository;
    @Mock
    private BackupRunner backupRunner;
    @Mock
    private RecoveryDrillRunner recoveryDrillRunner;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BackupScheduler scheduler;

    @Test
    void runScheduledBackup_noPolicy_noops() {
        when(policyRepository.findAll()).thenReturn(List.of());

        scheduler.runScheduledBackup();

        verifyNoInteractions(backupRunner);
        verify(backupRunRepository, never()).save(any());
    }

    @Test
    void runScheduledBackup_disabledPolicy_noops() {
        BackupPolicy p = new BackupPolicy();
        p.setScheduleEnabled(false);
        when(policyRepository.findAll()).thenReturn(List.of(p));

        scheduler.runScheduledBackup();

        verifyNoInteractions(backupRunner);
    }

    @Test
    void runScheduledBackup_cronDoesNotMatch_noops() {
        BackupPolicy p = new BackupPolicy();
        p.setScheduleEnabled(true);
        // Cron that triggers only at Jan 1 00:00:00 — nearly never "now"
        p.setScheduleCron("0 0 0 1 1 *");
        p.setRetentionDays(30);
        when(policyRepository.findAll()).thenReturn(List.of(p));

        scheduler.runScheduledBackup();

        verifyNoInteractions(backupRunner);
    }

    @Test
    void runQuarterlyRecoveryDrill_drillIsCurrent_noops() {
        RecoveryDrill recent = new RecoveryDrill();
        recent.setStatus("PASSED");
        recent.setCompletedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(recoveryDrillRepository.findFirstByStatusOrderByCompletedAtDesc("PASSED"))
                .thenReturn(Optional.of(recent));

        scheduler.runQuarterlyRecoveryDrill();

        verifyNoInteractions(recoveryDrillRunner);
        verify(recoveryDrillRepository, never()).save(any());
    }

    @Test
    void runQuarterlyRecoveryDrill_noCompletedBackup_notifiesAdmins() {
        when(recoveryDrillRepository.findFirstByStatusOrderByCompletedAtDesc("PASSED"))
                .thenReturn(Optional.empty());
        when(backupRunRepository.findFirstByStatusOrderByStartedAtDesc("COMPLETED"))
                .thenReturn(Optional.empty());
        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(userRepository.findActiveAdmins()).thenReturn(List.of(admin));

        scheduler.runQuarterlyRecoveryDrill();

        verify(notificationService).sendToAll(anyList(), eq("backup.drillOverdue"), anyString());
        verifyNoInteractions(recoveryDrillRunner);
    }

    @Test
    void runQuarterlyRecoveryDrill_overdueWithBackup_schedulesDrill() {
        when(recoveryDrillRepository.findFirstByStatusOrderByCompletedAtDesc("PASSED"))
                .thenReturn(Optional.empty());
        BackupRun latest = new BackupRun();
        latest.setId(UUID.randomUUID());
        when(backupRunRepository.findFirstByStatusOrderByStartedAtDesc("COMPLETED"))
                .thenReturn(Optional.of(latest));

        scheduler.runQuarterlyRecoveryDrill();

        org.mockito.ArgumentCaptor<RecoveryDrill> captor = org.mockito.ArgumentCaptor.forClass(RecoveryDrill.class);
        verify(recoveryDrillRepository).save(captor.capture());
        assertThat(captor.getValue().getBackupRunId()).isEqualTo(latest.getId());
        verify(recoveryDrillRunner).execute(any(RecoveryDrill.class), eq(latest));
    }

    @Test
    void runQuarterlyRecoveryDrill_drillOverdue_schedulesDrill() {
        RecoveryDrill old = new RecoveryDrill();
        old.setStatus("PASSED");
        old.setCompletedAt(Instant.now().minus(200, ChronoUnit.DAYS));
        when(recoveryDrillRepository.findFirstByStatusOrderByCompletedAtDesc("PASSED"))
                .thenReturn(Optional.of(old));
        BackupRun latest = new BackupRun();
        latest.setId(UUID.randomUUID());
        when(backupRunRepository.findFirstByStatusOrderByStartedAtDesc("COMPLETED"))
                .thenReturn(Optional.of(latest));

        scheduler.runQuarterlyRecoveryDrill();

        verify(recoveryDrillRunner).execute(any(RecoveryDrill.class), eq(latest));
    }
}
