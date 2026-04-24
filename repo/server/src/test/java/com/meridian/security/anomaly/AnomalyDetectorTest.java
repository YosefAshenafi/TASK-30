package com.meridian.security.anomaly;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.notifications.NotificationService;
import com.meridian.security.entity.AnomalyEvent;
import com.meridian.security.repository.AnomalyEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectorTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private AnomalyEventRepository anomalyEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AnomalyDetector detector;

    @Test
    void detectExportBurst_noBursts_doesNothing() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());

        detector.detectExportBurst();

        verifyNoInteractions(anomalyEventRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void detectExportBurst_burstFound_savesAnomalyAndNotifies() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "actor_id", userId.toString(),
                "ip_address", "10.0.0.1",
                "cnt", 25L);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
        when(anomalyEventRepository.existsRecentByUserIdAndType(userId, "EXPORT_BURST"))
                .thenReturn(false);
        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(userRepository.findActiveAdmins()).thenReturn(List.of(admin));

        detector.detectExportBurst();

        ArgumentCaptor<AnomalyEvent> captor = ArgumentCaptor.forClass(AnomalyEvent.class);
        verify(anomalyEventRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("EXPORT_BURST");
        verify(notificationService).send(eq(userId), eq("anomaly.exportBurst"), anyString());
        verify(notificationService).sendToAll(anyList(), eq("anomaly.exportBurst"), anyString());
    }

    @Test
    void detectExportBurst_alreadyFlagged_skipsSave() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> row = Map.of(
                "actor_id", userId.toString(),
                "ip_address", "10.0.0.1",
                "cnt", 25L);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row));
        when(anomalyEventRepository.existsRecentByUserIdAndType(userId, "EXPORT_BURST"))
                .thenReturn(true);

        detector.detectExportBurst();

        verify(anomalyEventRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void detectExportBurst_skipsNullActor() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("actor_id", null);
        row.put("ip_address", "10.0.0.1");
        row.put("cnt", 25L);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(row));

        detector.detectExportBurst();

        verifyNoInteractions(anomalyEventRepository);
        verifyNoInteractions(notificationService);
    }
}
