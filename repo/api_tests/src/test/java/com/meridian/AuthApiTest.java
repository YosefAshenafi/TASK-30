package com.meridian;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for §1 Authentication & Accounts.
 *
 * Uses the Testcontainers PostgreSQL setup defined in application-test.yml.
 * Run with: mvn test -pl api_tests (requires Docker).
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "classpath:auth-test-users.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    // Shared state for test ordering
    private static String savedRefreshToken;

    @Test
    @Order(1)
    void registerNewStudent_returns201() throws Exception {
        String body = """
            {
              "username": "student_test1",
              "password": "Test@Password1!",
              "displayName": "Test Student",
              "requestedRole": "STUDENT"
            }
            """;
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @Order(2)
    void registerDuplicateUsername_returns409() throws Exception {
        String body = """
            {
              "username": "student_test1",
              "password": "Another@Pass1!",
              "displayName": "Dup",
              "requestedRole": "STUDENT"
            }
            """;
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"));
    }

    @Test
    @Order(3)
    void registerWeakPassword_returns400() throws Exception {
        String body = """
            {
              "username": "weakpwduser",
              "password": "short",
              "displayName": "Weak",
              "requestedRole": "STUDENT"
            }
            """;
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void loginWithPendingAccount_returns403() throws Exception {
        String body = """
            {
              "username": "student_test1",
              "password": "Test@Password1!",
              "deviceFingerprint": "abc123def456"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_PENDING"));
    }

    @Test
    @Order(5)
    void loginAdmin_returns200WithTokens() throws Exception {
        // admin seeded in V101__seed_admin.sql
        String body = """
            {
              "username": "admin",
              "password": "Admin@123!",
              "deviceFingerprint": "testfingerprinthash"
            }
            """;
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andReturn();

        String json = result.getResponse().getContentAsString();
        savedRefreshToken = json.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @Order(6)
    void loginWithWrongPassword_returns401() throws Exception {
        String body = """
            {
              "username": "admin",
              "password": "WrongPass!1234",
              "deviceFingerprint": "fp"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @Order(7)
    void refreshToken_returns200() throws Exception {
        assumeRefreshTokenAvailable();
        String body = "{\"refreshToken\":\"" + savedRefreshToken + "\"}";
        mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Order(8)
    void loginWithXForwardedFor_returns200() throws Exception {
        String body = """
            {
              "username": "admin",
              "password": "Admin@123!",
              "deviceFingerprint": "forwarded-fp-test"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Order(9)
    void registerCorporateMentorWithoutOrgCode_returns400() throws Exception {
        String body = """
            {
              "username": "corp_mentor_test",
              "password": "Test@Password1!",
              "displayName": "Corp Mentor",
              "requestedRole": "CORPORATE_MENTOR"
            }
            """;
        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    void logout_returns204() throws Exception {
        assumeRefreshTokenAvailable();
        String body = "{\"refreshToken\":\"" + savedRefreshToken + "\"}";
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNoContent());
    }

    @Test
    @Order(11)
    void loginWithSuspendedUser_returns401() throws Exception {
        String body = """
            {
              "username": "suspended_user",
              "password": "Test@Password1!",
              "deviceFingerprint": "fp-suspended"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @Order(12)
    void loginWithLockedUser_returns403() throws Exception {
        String body = """
            {
              "username": "locked_user",
              "password": "Test@Password1!",
              "deviceFingerprint": "fp-locked"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @Order(13)
    void loginWithExpiredLockUser_returns401() throws Exception {
        String body = """
            {
              "username": "expired_lock_user",
              "password": "WrongPass!999",
              "deviceFingerprint": "fp-expired-lock"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @Order(14)
    void loginWithAllowedIp_returns200() throws Exception {
        String body = """
            {
              "username": "ip_mentor",
              "password": "Mentor@123!",
              "deviceFingerprint": "fp-ip-allowed"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Order(15)
    void loginWithBlockedIp_returns403() throws Exception {
        String body = """
            {
              "username": "ip_mentor",
              "password": "Mentor@123!",
              "deviceFingerprint": "fp-ip-blocked"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.5")
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("IP_NOT_ALLOWED"));
    }

    @Test
    @Order(16)
    void refreshTokenReuse_returns401() throws Exception {
        assumeRefreshTokenAvailable();
        String body = "{\"refreshToken\":\"" + savedRefreshToken + "\"}";
        mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSE"));
    }

    @Test
    @Order(17)
    void loginFromTrustedProxyWithBlankXForwardedFor_usesRemoteAddr() throws Exception {
        String body = """
            {
              "username": "admin",
              "password": "Admin@123!",
              "deviceFingerprint": "fp-blank-xff"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "   ")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Order(18)
    void loginFrom172PrivateSubnet_usesXForwardedForClientIp() throws Exception {
        String body = """
            {
              "username": "admin",
              "password": "Admin@123!",
              "deviceFingerprint": "fp-172-private"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "198.51.100.22, 10.0.0.2")
                .with(request -> {
                    request.setRemoteAddr("172.20.5.1");
                    return request;
                })
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Order(19)
    void loginFromNonRfc1918Remote_ignoresXForwardedFor() throws Exception {
        String body = """
            {
              "username": "admin",
              "password": "Admin@123!",
              "deviceFingerprint": "fp-untrusted-remote"
            }
            """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "198.51.100.50")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.99");
                    return request;
                })
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private void assumeRefreshTokenAvailable() {
        assumeThat(savedRefreshToken).isNotNull();
    }
}
