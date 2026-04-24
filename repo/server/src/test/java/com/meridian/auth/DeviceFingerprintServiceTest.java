package com.meridian.auth;

import com.meridian.auth.entity.User;
import com.meridian.auth.entity.UserDeviceFingerprint;
import com.meridian.auth.entity.UserDeviceFingerprintId;
import com.meridian.auth.repository.UserDeviceFingerprintRepository;
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
class DeviceFingerprintServiceTest {

    @Mock
    private UserDeviceFingerprintRepository fingerprintRepository;
    @Mock
    private AnomalyEventRepository anomalyEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceFingerprintService service;

    @Test
    void processFingerprint_knownDevice_returnsFalseAndUpdatesLastSeen() {
        UUID userId = UUID.randomUUID();
        UserDeviceFingerprint existing = UserDeviceFingerprint.create(userId, "fp-hash");
        when(fingerprintRepository.findByIdUserIdAndIdFingerprintHash(userId, "fp-hash"))
                .thenReturn(Optional.of(existing));

        boolean result = service.processFingerprint(userId, "fp-hash", "127.0.0.1");

        assertThat(result).isFalse();
        verify(fingerprintRepository).save(existing);
        verifyNoInteractions(anomalyEventRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void processFingerprint_newDevice_returnsTrueAndNotifiesAdmins() {
        UUID userId = UUID.randomUUID();
        when(fingerprintRepository.findByIdUserIdAndIdFingerprintHash(userId, "new-fp"))
                .thenReturn(Optional.empty());

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");
        when(userRepository.findActiveAdmins()).thenReturn(List.of(admin));

        boolean result = service.processFingerprint(userId, "new-fp", "10.0.0.2");

        assertThat(result).isTrue();
        verify(fingerprintRepository).save(any(UserDeviceFingerprint.class));
        ArgumentCaptor<AnomalyEvent> captor = ArgumentCaptor.forClass(AnomalyEvent.class);
        verify(anomalyEventRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("NEW_DEVICE");
        verify(notificationService).send(eq(userId), eq("anomaly.newDevice"), anyString());
        verify(notificationService).sendToAll(anyList(), eq("anomaly.newDevice"), anyString());
    }

    @Test
    void processFingerprint_newDevice_excludesSelfFromAdminFanout() {
        UUID adminId = UUID.randomUUID();
        User self = new User();
        self.setId(adminId);
        self.setRole("ADMIN");
        when(fingerprintRepository.findByIdUserIdAndIdFingerprintHash(adminId, "fp"))
                .thenReturn(Optional.empty());
        when(userRepository.findActiveAdmins()).thenReturn(List.of(self));

        service.processFingerprint(adminId, "fp", "1.2.3.4");

        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).sendToAll(captor.capture(), eq("anomaly.newDevice"), anyString());
        assertThat(captor.getValue()).doesNotContain(adminId);
    }
}
