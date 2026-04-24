package com.meridian.notifications;

import com.meridian.notifications.entity.InAppNotification;
import com.meridian.notifications.repository.InAppNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private InAppNotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService service;

    @Test
    void send_persistsNotification() {
        UUID userId = UUID.randomUUID();
        String key = "approval.decided";
        String payload = "{\"foo\":\"bar\"}";

        service.send(userId, key, payload);

        ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
        verify(notificationRepository).save(captor.capture());
        InAppNotification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTemplateKey()).isEqualTo(key);
        assertThat(saved.getPayload()).isEqualTo(payload);
    }

    @Test
    void sendToAll_savesOnePerRecipient() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        service.sendToAll(List.of(a, b, c), "anomaly.newDevice", "{}");

        verify(notificationRepository, times(3)).save(org.mockito.ArgumentMatchers.any(InAppNotification.class));
    }

    @Test
    void sendToAll_emptyList_savesNothing() {
        service.sendToAll(List.of(), "any.key", "{}");
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void send_nullPayloadAccepted_savedAsNull() {
        UUID userId = UUID.randomUUID();
        service.send(userId, "test.key", null);

        ArgumentCaptor<InAppNotification> captor = ArgumentCaptor.forClass(InAppNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).isNull();
    }
}
