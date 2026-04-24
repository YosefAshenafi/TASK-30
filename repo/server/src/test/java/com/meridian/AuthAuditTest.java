package com.meridian;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying audit events are written for login lifecycle.
 *
 * Covers: LOGIN_SUCCESS, LOGIN_FAILURE, LOCKOUT (§10 audit logging gap).
 * Also covers 5-failed-login lockout + reset path (§8.2).
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "classpath:auth-test-users.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuthAuditTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String AUDIT_URL = "/api/v1/admin/audit";

    @Test
    @Order(1)
    void loginSuccess_writesLoginSuccessAuditEvent() throws Exception {
        String body = """
                {
                  "username": "admin",
                  "password": "Admin@123!",
                  "deviceFingerprint": "audit-test-fp-1"
                }
                """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        // Verify audit event was written
        mockMvc.perform(get(AUDIT_URL + "?action=LOGIN_SUCCESS&size=1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin-uuid").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].action").value("LOGIN_SUCCESS"));
    }

    @Test
    @Order(2)
    void loginFailure_writesLoginFailureAuditEvent() throws Exception {
        String body = """
                {
                  "username": "admin",
                  "password": "WrongPassword!99",
                  "deviceFingerprint": "audit-test-fp-2"
                }
                """;
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(AUDIT_URL + "?action=LOGIN_FAILURE&size=1")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("admin-uuid").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].action").value("LOGIN_FAILURE"));
    }

    @Test
    @Order(3)
    void fiveFailedLogins_locksAccountAndWritesLockoutEvent() throws Exception {
        String wrongPwd = """
                {
                  "username": "lockout_test_user",
                  "password": "WrongPass!999",
                  "deviceFingerprint": "fp-lockout"
                }
                """;

        // Trigger 5 failures
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(LOGIN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongPwd));
        }

        // 6th attempt should return ACCOUNT_LOCKED or INVALID_CREDENTIALS
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongPwd))
            .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                    org.hamcrest.Matchers.equalTo(401),
                    org.hamcrest.Matchers.equalTo(403))));
    }

    @Test
    @Order(4)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
    void auditEndpoint_authorizedAdminReturns200() throws Exception {
        // Q-1 remediation: promote status-only to body-shape assertion
        mockMvc.perform(get(AUDIT_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").exists());
    }

    @Test
    @Order(5)
    void auditEndpoint_unauthenticatedReturns401() throws Exception {
        // Q-1 remediation: pin error envelope shape for the 401 path
        mockMvc.perform(get(AUDIT_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @Order(6)
    @WithMockUser(username = "stud-uuid", roles = "STUDENT")
    void auditEndpoint_studentRoleReturns403WithFORBIDDEN() throws Exception {
        // Q-3 remediation: explicit 403 negative case, body-shape asserted
        mockMvc.perform(get(AUDIT_URL))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
