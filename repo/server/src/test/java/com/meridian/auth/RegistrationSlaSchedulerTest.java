package com.meridian.auth;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.notifications.NotificationService;
import com.meridian.security.audit.AuditEvent;
import com.meridian.security.audit.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationSlaSchedulerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private RegistrationSlaScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "slaBusinessDays", 2L);
    }

    @Test
    void escalate_whenPendingUserIsOverdue_notifiesAdminsAndAudits() {
        User pending = newPending(UUID.randomUUID(), "late-user",
                Instant.now().minus(10, ChronoUnit.DAYS));
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole("ADMIN");
        when(userRepository.findAllByStatus("PENDING")).thenReturn(List.of(pending));
        when(userRepository.findActiveAdmins()).thenReturn(List.of(admin));

        scheduler.escalateOverduePending();

        verify(notificationService).sendToAll(anyList(), eq("registration.slaOverdue"), anyString());
        verify(auditEventRepository).save(any(AuditEvent.class));
    }

    @Test
    void escalate_whenUserIsFresh_doesNothing() {
        User fresh = newPending(UUID.randomUUID(), "new", Instant.now());
        when(userRepository.findAllByStatus("PENDING")).thenReturn(List.of(fresh));

        scheduler.escalateOverduePending();

        verifyNoInteractions(notificationService);
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void escalate_slaDisabled_returnsImmediately() {
        ReflectionTestUtils.setField(scheduler, "slaBusinessDays", 0L);

        scheduler.escalateOverduePending();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void escalate_sameUserEscalatedTwice_sendsOnlyOnce() {
        User pending = newPending(UUID.randomUUID(), "dup",
                Instant.now().minus(10, ChronoUnit.DAYS));
        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(userRepository.findAllByStatus("PENDING")).thenReturn(List.of(pending));
        when(userRepository.findActiveAdmins()).thenReturn(List.of(admin));

        scheduler.escalateOverduePending();
        scheduler.escalateOverduePending();

        // sendToAll called exactly once across two scheduler runs
        verify(notificationService, times(1))
                .sendToAll(anyList(), eq("registration.slaOverdue"), anyString());
    }

    @Test
    void escalate_skipsSoftDeletedUsers() {
        User deleted = newPending(UUID.randomUUID(), "deleted",
                Instant.now().minus(10, ChronoUnit.DAYS));
        deleted.setDeletedAt(Instant.now());
        when(userRepository.findAllByStatus("PENDING")).thenReturn(List.of(deleted));

        scheduler.escalateOverduePending();

        verifyNoInteractions(notificationService);
    }

    private User newPending(UUID id, String username, Instant createdAt) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName(username);
        u.setPasswordBcrypt("hash");
        u.setRole("STUDENT");
        u.setStatus("PENDING");
        u.setCreatedAt(createdAt);
        return u;
    }
}
