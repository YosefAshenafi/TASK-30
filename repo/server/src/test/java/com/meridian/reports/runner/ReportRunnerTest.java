package com.meridian.reports.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.governance.MaskingPolicy;
import com.meridian.notifications.NotificationService;
import com.meridian.reports.entity.ReportRun;
import com.meridian.reports.repository.ReportRunRepository;
import com.meridian.security.audit.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportRunnerTest {

    @Mock
    private ReportRunRepository reportRunRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MaskingPolicy maskingPolicy;
    @Mock
    private UserRepository userRepository;

    private ReportRunner runner;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        runner = new ReportRunner(reportRunRepository, auditEventRepository, jdbc,
                notificationService, new ObjectMapper(), maskingPolicy, userRepository);
        ReflectionTestUtils.setField(runner, "exportPath", tempDir.toString());
    }

    @Test
    void execute_unknownType_writesCsvAndMarksSucceeded() throws Exception {
        ReportRun run = newRun("UNSUPPORTED_TYPE", "{}");
        when(reportRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        runner.execute(run);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getFilePath()).isNotNull();
        assertThat(Files.exists(Path.of(run.getFilePath()))).isTrue();
        verify(notificationService).send(eq(run.getRequestedBy()), eq("export.ready"), anyString());
    }

    @Test
    void execute_jsonFormat_writesJsonFile() throws Exception {
        ReportRun run = newRun("UNKNOWN", "{\"format\":\"JSON\"}");
        when(reportRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        runner.execute(run);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getFilePath()).endsWith(".json");
    }

    @Test
    void execute_queryFailure_marksFailedAndNotifies() {
        ReportRun run = newRun("ENROLLMENTS", "{}");
        when(reportRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(run.getRequestedBy())).thenReturn(Optional.empty());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("db boom"));

        runner.execute(run);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getErrorMessage()).contains("db boom");
        verify(notificationService).send(eq(run.getRequestedBy()), eq("export.failed"), anyString());
    }

    @Test
    void execute_enrollmentsForStudent_masksIdentityFields() throws Exception {
        ReportRun run = newRun("ENROLLMENTS", "{}");
        User student = new User();
        student.setId(run.getRequestedBy());
        student.setRole("STUDENT");
        when(userRepository.findById(run.getRequestedBy())).thenReturn(Optional.of(student));

        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("username", "alice");
        row.put("display_name", "Alice A");
        row.put("email", "alice@example.com");
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(row));
        when(maskingPolicy.maskField(anyString(), anyString())).thenReturn("***");
        when(reportRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        runner.execute(run);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        verify(maskingPolicy, atLeastOnce()).maskField(anyString(), anyString());
    }

    @Test
    void execute_adminDoesNotMask() throws Exception {
        ReportRun run = newRun("ENROLLMENTS", "{}");
        User admin = new User();
        admin.setId(run.getRequestedBy());
        admin.setRole("ADMIN");
        when(userRepository.findById(run.getRequestedBy())).thenReturn(Optional.of(admin));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("username", "alice")));
        when(reportRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        runner.execute(run);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        verify(maskingPolicy, never()).maskField(anyString(), anyString());
    }

    private ReportRun newRun(String type, String params) {
        ReportRun run = new ReportRun();
        run.setId(UUID.randomUUID());
        run.setType(type);
        run.setParameters(params);
        run.setRequestedBy(UUID.randomUUID());
        return run;
    }
}
