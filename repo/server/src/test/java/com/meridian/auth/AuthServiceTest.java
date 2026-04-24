package com.meridian.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.dto.LoginRequest;
import com.meridian.auth.dto.RefreshRequest;
import com.meridian.auth.dto.RegisterRequest;
import com.meridian.auth.dto.RegisterResponse;
import com.meridian.auth.entity.RefreshToken;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.RefreshTokenRepository;
import com.meridian.auth.repository.UserRepository;
import com.meridian.notifications.NotificationService;
import com.meridian.organizations.repository.OrganizationRepository;
import com.meridian.security.audit.AuditEventPublisher;
import com.meridian.security.audit.AuditEventRepository;
import com.meridian.security.repository.AllowedIpRangeRepository;
import com.meridian.security.repository.AnomalyEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure-unit tests for AuthService public methods (register/login/refresh/logout).
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private OrganizationRepository organizationRepository;
    private AllowedIpRangeRepository allowedIpRangeRepository;
    private AnomalyEventRepository anomalyEventRepository;
    private AuditEventRepository auditEventRepository;
    private AuditEventPublisher auditEventPublisher;
    private NotificationService notificationService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private DeviceFingerprintService deviceFingerprintService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        refreshTokenRepository = Mockito.mock(RefreshTokenRepository.class);
        organizationRepository = Mockito.mock(OrganizationRepository.class);
        allowedIpRangeRepository = Mockito.mock(AllowedIpRangeRepository.class);
        anomalyEventRepository = Mockito.mock(AnomalyEventRepository.class);
        auditEventRepository = Mockito.mock(AuditEventRepository.class);
        auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
        notificationService = Mockito.mock(NotificationService.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        deviceFingerprintService = Mockito.mock(DeviceFingerprintService.class);

        authService = new AuthService(userRepository, refreshTokenRepository,
                organizationRepository, allowedIpRangeRepository, anomalyEventRepository,
                auditEventRepository, auditEventPublisher, notificationService, passwordEncoder,
                jwtService, deviceFingerprintService, new ObjectMapper());

        when(allowedIpRangeRepository.countRulesForRole(any())).thenReturn(0L);
        when(allowedIpRangeRepository.findCidrsByRole(any())).thenReturn(List.of());
        when(jwtService.issueAccessToken(any(), any(), any())).thenReturn("jwt-token");
        when(deviceFingerprintService.processFingerprint(any(), any(), any())).thenReturn(false);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void register_validStudent_returnsPendingResponse() {
        RegisterRequest req = new RegisterRequest(
                "alice123", "ValidPass123!", "Alice", "alice@example.com", "STUDENT", null);
        when(userRepository.existsByUsername("alice123")).thenReturn(false);
        when(passwordEncoder.encode("ValidPass123!")).thenReturn("bcrypt$$hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterResponse resp = authService.register(req);

        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(resp.userId()).isNotNull();
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        RegisterRequest req = new RegisterRequest(
                "taken", "ValidPass123!", "Alice", "a@b.com", "STUDENT", null);
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_corporateMentorMissingOrgCode_throwsBadRequest() {
        RegisterRequest req = new RegisterRequest(
                "corp", "ValidPass123!", "Corp", "c@b.com", "CORPORATE_MENTOR", "");
        when(userRepository.existsByUsername("corp")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_unknownUser_throwsUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("ghost", "x", "fp"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_pendingAccount_throwsForbidden() {
        User u = buildUser("pendingU", "PENDING");
        when(userRepository.findByUsername("pendingU")).thenReturn(Optional.of(u));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("pendingU", "p", "fp"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void login_happyPath_returnsTokens() {
        User u = buildUser("active", "ACTIVE");
        when(userRepository.findByUsername("active")).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenReturn(u);
        when(passwordEncoder.matches("p", "hash")).thenReturn(true);

        var resp = authService.login(new LoginRequest("active", "p", "fp"), "10.0.0.1");

        assertThat(resp.accessToken()).isEqualTo("jwt-token");
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.user().username()).isEqualTo("active");
    }

    @Test
    void refresh_invalidToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("missing")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_revokedToken_triggersFamilyRevocation() {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setFamilyId(UUID.randomUUID());
        rt.setUserId(UUID.randomUUID());
        rt.setRevokedAt(Instant.now());
        rt.setIdleExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        rt.setAbsoluteExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("reused")))
                .isInstanceOf(ResponseStatusException.class);
        verify(refreshTokenRepository).revokeFamily(eq(rt.getFamilyId()), any());
    }

    @Test
    void logout_withUnknownToken_doesNotThrow() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() ->
                authService.logout(new RefreshRequest("any")));
    }

    private User buildUser(String username, String status) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setDisplayName("Name");
        u.setPasswordBcrypt("hash");
        u.setRole("STUDENT");
        u.setStatus(status);
        return u;
    }
}
