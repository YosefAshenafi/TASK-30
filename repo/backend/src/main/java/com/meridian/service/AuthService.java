package com.meridian.service;

import com.meridian.config.JwtProperties;
import com.meridian.dto.AuthResponse;
import com.meridian.dto.LoginRequest;
import com.meridian.dto.LoginResult;
import com.meridian.dto.RegisterRequest;
import com.meridian.entity.RefreshToken;
import com.meridian.entity.Role;
import com.meridian.entity.User;
import com.meridian.entity.UserStatus;
import com.meridian.exception.AppException;
import com.meridian.repository.RefreshTokenRepository;
import com.meridian.repository.RoleRepository;
import com.meridian.repository.UserRepository;
import com.meridian.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw AppException.conflict("Username already exists: " + request.username());
        }

        Role studentRole = roleRepository.findByName("ROLE_STUDENT")
                .orElseThrow(() -> AppException.notFound("Default role not found"));

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING);
        user.setRoles(Set.of(studentRole));

        if (request.organizationId() != null && !request.organizationId().isBlank()) {
            try {
                user.setOrganizationId(UUID.fromString(request.organizationId()));
            } catch (IllegalArgumentException ex) {
                throw AppException.badRequest("Invalid organization ID format");
            }
        }

        return userRepository.save(user);
    }

    public LoginResult login(LoginRequest request, String ipAddress, String fingerprint) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> AppException.unauthorized("Invalid credentials"));

        switch (user.getStatus()) {
            case PENDING -> throw AppException.badRequest("Account pending approval");
            case REJECTED -> throw AppException.badRequest("Account has been rejected");
            case LOCKED -> {
                if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
                    throw AppException.badRequest("Account is temporarily locked. Try again later.");
                }
                // Lock period expired — allow re-attempt
            }
            default -> {
                // ACTIVE — OK to proceed
            }
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            boolean nowLocked = attempts >= MAX_FAILED_ATTEMPTS;
            if (nowLocked) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60));
                log.warn("User account locked after {} failed attempts: {}", attempts, user.getUsername());
            }

            userRepository.save(user);

            String failDetails = nowLocked
                    ? String.format("{\"reason\":\"bad_password\",\"attempts\":%d,\"accountLocked\":true}", attempts)
                    : String.format("{\"reason\":\"bad_password\",\"attempts\":%d}", attempts);
            auditService.logEvent(user.getId(), "LOGIN_FAILURE", "User",
                    user.getId().toString(), failDetails, ipAddress, fingerprint);

            throw AppException.unauthorized("Invalid credentials");
        }

        // Successful authentication — reset failure counters
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user, user.getId(), user.getPrimaryRole());
        String rawRefreshToken = jwtService.generateRefreshToken(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshExpiryMs()));
        refreshTokenRepository.save(refreshToken);

        String successDetails = String.format("{\"role\":\"%s\"}", user.getPrimaryRole());
        auditService.logEvent(user.getId(), "LOGIN_SUCCESS", "User",
                user.getId().toString(), successDetails, ipAddress, fingerprint);

        log.info("User logged in: username={}", user.getUsername());

        AuthResponse authResponse = buildAuthResponse(accessToken, user);
        return new LoginResult(authResponse, rawRefreshToken);
    }

    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        String hash = hashToken(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public LoginResult refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw AppException.unauthorized("Refresh token is required");
        }

        if (!jwtService.validateToken(refreshTokenValue, jwtService.getRefreshKey())) {
            throw AppException.unauthorized("Invalid or expired refresh token");
        }

        String hash = hashToken(refreshTokenValue);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> AppException.unauthorized("Refresh token not found"));

        if (storedToken.getRevokedAt() != null) {
            throw AppException.unauthorized("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.unauthorized("Refresh token has expired");
        }

        // Revoke old token
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        UUID userId = storedToken.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        String newAccessToken = jwtService.generateAccessToken(user, user.getId(), user.getPrimaryRole());
        String newRawRefreshToken = jwtService.generateRefreshToken(user.getId());

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setTokenHash(hashToken(newRawRefreshToken));
        newRefreshToken.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshExpiryMs()));
        refreshTokenRepository.save(newRefreshToken);

        AuthResponse authResponse = buildAuthResponse(newAccessToken, user);
        return new LoginResult(authResponse, newRawRefreshToken);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> AppException.notFound("User not found: " + username));
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private AuthResponse buildAuthResponse(String accessToken, User user) {
        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getPrimaryRole(),
                user.getOrganizationId()
        );
        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtProperties.accessExpiryMs() / 1000,
                userInfo
        );
    }
}
