package com.meridian.recyclebin;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.courses.entity.Course;
import com.meridian.courses.repository.CourseRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecycleBinRetentionSchedulerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private RecycleBinRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 14);
    }

    @Test
    void purge_retentionDisabled_noops() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 0);

        scheduler.purge();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(courseRepository);
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void purge_expiredCoursesAndUsers_hardDeletesAndAudits() {
        Course c = new Course();
        c.setId(UUID.randomUUID());
        c.setCode("C1");
        c.setTitle("Course1");
        c.setVersion("v1");
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("gone");
        u.setDisplayName("Gone");
        u.setPasswordBcrypt("hash");
        u.setRole("STUDENT");
        u.setStatus("DELETED");

        when(courseRepository.findSoftDeletedBefore(any())).thenReturn(List.of(c));
        when(userRepository.findSoftDeletedBefore(any())).thenReturn(List.of(u));

        scheduler.purge();

        verify(courseRepository).deleteById(c.getId());
        verify(userRepository).deleteById(u.getId());
        verify(auditEventRepository, times(2)).save(any(AuditEvent.class));
    }

    @Test
    void purge_nothingExpired_noDeletes() {
        when(courseRepository.findSoftDeletedBefore(any())).thenReturn(List.of());
        when(userRepository.findSoftDeletedBefore(any())).thenReturn(List.of());

        scheduler.purge();

        verify(courseRepository, never()).deleteById(any());
        verify(userRepository, never()).deleteById(any());
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void purge_cutoffIsRetentionDaysBeforeNow() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
        when(courseRepository.findSoftDeletedBefore(any())).thenReturn(List.of());
        when(userRepository.findSoftDeletedBefore(any())).thenReturn(List.of());
        Instant before = Instant.now();

        scheduler.purge();

        org.mockito.ArgumentCaptor<Instant> captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(courseRepository).findSoftDeletedBefore(captor.capture());
        Instant cutoff = captor.getValue();
        // cutoff should be approximately now - 7 days
        org.assertj.core.api.Assertions.assertThat(cutoff)
                .isBefore(before.minus(6, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
    }
}
