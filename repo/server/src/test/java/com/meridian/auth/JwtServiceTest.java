package com.meridian.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SIGNING_KEY = "meridian-test-signing-key-32bytes!!";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SIGNING_KEY);
    }

    @Test
    void issueAndParseRoundTrip() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "ADMIN", orgId);

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId.toString());
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.extractOrgId(token)).isEqualTo(orgId.toString());
    }

    @Test
    void nullOrgIdEncodesAsNull() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "STUDENT", null);
        assertThat(jwtService.extractOrgId(token)).isNull();
    }

    @Test
    void tamperedTokenIsInvalid() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "ADMIN", null);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKeyIsInvalid() {
        JwtService other = new JwtService("other-signing-key-that-differs-xx");
        UUID userId = UUID.randomUUID();
        String token = other.issueAccessToken(userId, "STUDENT", null);
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void rejectsKeyUnder32Bytes() {
        assertThatThrownBy(() -> new JwtService("short-key"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    void ttlIs900Seconds() {
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(900L);
    }

    @Test
    void parseThrowsOnBlankToken() {
        assertThatThrownBy(() -> jwtService.parseToken("not-a-jwt"))
            .isInstanceOf(Exception.class);
    }
}
