package com.meridian.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.dto.LoginRequest;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.RefreshTokenRepository;
import com.meridian.auth.repository.UserRepository;
import com.meridian.notifications.NotificationService;
import com.meridian.organizations.repository.OrganizationRepository;
import com.meridian.security.audit.AuditEvent;
import com.meridian.security.audit.AuditEventPublisher;
import com.meridian.security.audit.AuditEventRepository;
import com.meridian.security.repository.AllowedIpRangeRepository;
import com.meridian.security.repository.AnomalyEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying audit events are persisted for login lifecycle (§10).
 */
class AuthServiceAuditTest {

    private UserRepository userRepository;
    private AuditEventRepository auditEventRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        RefreshTokenRepository refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
        OrganizationRepository organizationRepository = Mockito.mock(OrganizationRepository.class);
        AllowedIpRangeRepository allowedIpRangeRepository = Mockito.mock(AllowedIpRangeRepository.class);
        AnomalyEventRepository anomalyEventRepository = Mockito.mock(AnomalyEventRepository.class);
        auditEventRepository = Mockito.mock(AuditEventRepository.class);
        AuditEventPublisher auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
        Mockito.when(auditEventPublisher.publish(Mockito.any())).thenAnswer(inv -> {
            AuditEvent e = inv.getArgument(0);
            return auditEventRepository.save(e);
        });
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        JwtService jwtService = Mockito.mock(JwtService.class);
        DeviceFingerprintService deviceFingerprintService = Mockito.mock(DeviceFingerprintService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        authService = new AuthService(userRepository, refreshTokenRepository, organizationRepository,
                allowedIpRangeRepository, anomalyEventRepository, auditEventRepository, auditEventPublisher,
                notificationService, passwordEncoder, jwtService, deviceFingerprintService, objectMapper);

        when(allowedIpRangeRepository.countRulesForRole(any())).thenReturn(0L);
        when(jwtService.issueAccessToken(any(), any(), any())).thenReturn("mock.jwt.token");
        when(allowedIpRangeRepository.findCidrsByRole(any())).thenReturn(List.of());
        when(deviceFingerprintService.processFingerprint(any(), any(), any())).thenReturn(false);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private User activeUser(UUID id, String password) {
        User u = new User();
        u.setId(id != null ? id : UUID.randomUUID());
        u.setUsername("testuser");
        u.setDisplayName("Test User");
        u.setPasswordBcrypt(password);
        u.setRole("STUDENT");
        u.setStatus("ACTIVE");
        return u;
    }

    @Test
    void loginSuccess_savesLoginSuccessAuditEvent() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "hashed");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        authService.login(new LoginRequest("testuser", "password", "fp"), "127.0.0.1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, atLeastOnce()).save(captor.capture());

        boolean hasLoginSuccess = captor.getAllValues().stream()
                .anyMatch(e -> "LOGIN_SUCCESS".equals(e.getAction()));
        assertThat(hasLoginSuccess).isTrue();
    }

    @Test
    void loginFailure_savesLoginFailureAuditEvent() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "hashed");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("testuser", "wrong", "fp"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, atLeastOnce()).save(captor.capture());

        boolean hasFailure = captor.getAllValues().stream()
                .anyMatch(e -> "LOGIN_FAILURE".equals(e.getAction()));
        assertThat(hasFailure).isTrue();
    }

    @Test
    void loginLockedAccount_savesLockoutAuditEvent() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "hashed");
        user.setStatus("LOCKED");
        user.setLockedUntil(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS));
        user.setFailedLoginCount(5);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("testuser", "password", "fp"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, atLeastOnce()).save(captor.capture());

        boolean hasLockout = captor.getAllValues().stream()
                .anyMatch(e -> "LOCKOUT".equals(e.getAction()));
        assertThat(hasLockout).isTrue();
    }

    @Test
    void auditEvent_includesIpAddress() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "hashed");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        authService.login(new LoginRequest("testuser", "password", "fp"), "10.0.0.5");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, atLeastOnce()).save(captor.capture());

        boolean hasIp = captor.getAllValues().stream()
                .filter(e -> "LOGIN_SUCCESS".equals(e.getAction()))
                .anyMatch(e -> "10.0.0.5".equals(e.getIpAddress()));
        assertThat(hasIp).isTrue();
    }
}
