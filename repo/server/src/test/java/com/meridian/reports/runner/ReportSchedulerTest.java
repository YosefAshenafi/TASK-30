package com.meridian.reports.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.reports.entity.ReportRun;
import com.meridian.reports.entity.ReportSchedule;
import com.meridian.reports.repository.ReportRunRepository;
import com.meridian.reports.repository.ReportScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportSchedulerTest {

    @Mock
    private ReportScheduleRepository scheduleRepository;
    @Mock
    private ReportRunRepository runRepository;
    @Mock
    private ReportRunner reportRunner;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReportScheduler scheduler;

    @Test
    void runDueSchedules_noDue_doesNothing() {
        when(scheduleRepository.findDue(any())).thenReturn(List.of());

        scheduler.runDueSchedules();

        verifyNoInteractions(reportRunner);
        verify(runRepository, never()).save(any());
    }

    @Test
    void runDueSchedules_dueSchedule_createsRunAndExecutes() {
        ReportSchedule schedule = newSchedule("0 0 * * * *");
        when(scheduleRepository.findDue(any())).thenReturn(List.of(schedule));
        when(runRepository.save(any())).thenAnswer(i -> {
            ReportRun r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        scheduler.runDueSchedules();

        ArgumentCaptor<ReportRun> runCaptor = ArgumentCaptor.forClass(ReportRun.class);
        verify(runRepository).save(runCaptor.capture());
        ReportRun saved = runCaptor.getValue();
        assertThat(saved.getType()).isEqualTo("ENROLLMENTS");
        assertThat(saved.getRequestedBy()).isEqualTo(schedule.getOwnerId());
        verify(reportRunner).execute(any(ReportRun.class));
        verify(scheduleRepository).save(schedule);
        assertThat(schedule.getLastRunAt()).isNotNull();
        assertThat(schedule.getNextRunAt()).isNotNull();
    }

    @Test
    void runDueSchedules_exceptionInOne_doesNotAbortOthers() {
        ReportSchedule s1 = newSchedule("0 0 * * * *");
        ReportSchedule s2 = newSchedule("0 0 * * * *");
        when(scheduleRepository.findDue(any())).thenReturn(List.of(s1, s2));
        when(runRepository.save(any()))
                .thenThrow(new RuntimeException("boom"))
                .thenAnswer(i -> {
                    ReportRun r = i.getArgument(0);
                    r.setId(UUID.randomUUID());
                    return r;
                });

        scheduler.runDueSchedules();

        // Second schedule still processed
        verify(runRepository, times(2)).save(any());
        verify(reportRunner, times(1)).execute(any());
    }

    @Test
    void computeNextRunAt_validCron_returnsFutureInstant() {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant next = scheduler.computeNextRunAt("0 0 * * * *", from);
        assertThat(next).isAfter(from);
    }

    @Test
    void computeNextRunAt_blankCron_fallsBackOneDay() {
        Instant from = Instant.now();
        Instant next = scheduler.computeNextRunAt("", from);
        assertThat(next).isEqualTo(from.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void computeNextRunAt_invalidCron_fallsBackOneDay() {
        Instant from = Instant.now();
        Instant next = scheduler.computeNextRunAt("not a cron expression at all", from);
        assertThat(next).isEqualTo(from.plus(1, ChronoUnit.DAYS));
    }

    private ReportSchedule newSchedule(String cron) {
        ReportSchedule s = new ReportSchedule();
        s.setId(UUID.randomUUID());
        s.setType("ENROLLMENTS");
        s.setParameters("{}");
        s.setCronExpr(cron);
        s.setOwnerId(UUID.randomUUID());
        s.setEnabled(true);
        return s;
    }
}
