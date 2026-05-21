package com.meridian.controller;

import com.meridian.dto.AuthResponse;
import com.meridian.dto.LoginRequest;
import com.meridian.dto.LoginResult;
import com.meridian.dto.RegisterRequest;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.service.AnomalyDetectionService;
import com.meridian.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final int REFRESH_COOKIE_MAX_AGE = 604800; // 7 days in seconds

    private final AuthService authService;
    private final AnomalyDetectionService anomalyDetectionService;

    public AuthController(AuthService authService, AnomalyDetectionService anomalyDetectionService) {
        this.authService = authService;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "Registration successful. Awaiting administrator approval."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String ipAddress = extractIpAddress(httpRequest);
        String fingerprint = computeFingerprint(httpRequest);

        LoginResult result = authService.login(request, ipAddress, fingerprint);
        setRefreshCookie(httpResponse, result.rawRefreshToken());

        try {
            anomalyDetectionService.checkNewDevice(result.authResponse().user().id(), fingerprint, ipAddress);
            anomalyDetectionService.checkIpRange(result.authResponse().user().id(), ipAddress);
        } catch (Exception e) {
            log.warn("Anomaly check failed during login: {}", e.getMessage());
        }

        return ResponseEntity.ok(result.authResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshTokenValue = extractRefreshCookie(request);
        authService.logout(refreshTokenValue);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshTokenValue = extractRefreshCookie(request);
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw AppException.unauthorized("Refresh token cookie is missing");
        }

        LoginResult result = authService.refresh(refreshTokenValue);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.authResponse());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AuthResponse.UserInfo> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUser(userDetails.getUsername());
        AuthResponse.UserInfo info = new AuthResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getPrimaryRole(),
                user.getOrganizationId()
        );
        return ResponseEntity.ok(info);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String computeFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String acceptLang = request.getHeader("Accept-Language");
        String tzOffset = request.getHeader("Tz-Offset");
        return String.valueOf(
                ((userAgent != null ? userAgent : "") +
                 (acceptLang != null ? acceptLang : "") +
                 (tzOffset != null ? tzOffset : "")).hashCode()
        );
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(REFRESH_COOKIE_MAX_AGE);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
