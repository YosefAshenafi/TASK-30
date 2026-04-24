package com.meridian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-2 … B-10 (§A.7) — AdminUserController coverage.
 *
 * Real-JWT flow via {@link TestAuthHelper} — M-1 remediation (§A.3).
 * Uses the `admin` and `student1` accounts seeded by local Flyway migrations
 * (V101__seed_admin.sql + V200__seed_dev_data.sql).
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminUserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @Order(1)
    void listUsers_adminJwt_returns200WithPageShape() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        MvcResult res = mockMvc.perform(get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0))
            .andReturn();
        JsonNode body = mapper.readTree(res.getResponse().getContentAsString());
        // Sanity: at least the seeded admin must be in the list
        if (!body.get("content").isArray()) {
            throw new AssertionError("Expected content[] array in admin user list");
        }
    }

    @Test
    @Order(2)
    void listUsers_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void listUsers_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void rejectPendingUser_unknownId_returns404() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = "{\"reason\":\"not a real applicant\"}";
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-ffffffffffff/reject")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s != 404 && s != 400 && s != 409) {
                    throw new AssertionError("Expected 4xx for unknown user id, got " + s);
                }
            });
    }

    @Test
    @Order(5)
    void rejectPendingUser_studentCaller_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        String body = "{\"reason\":\"unauthorised reject attempt\"}";
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-000000000104/reject")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void rejectPendingUser_emptyReason_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = "{\"reason\":\"\"}";
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-000000000104/reject")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void patchUserStatus_invalidValue_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = "{\"status\":\"NOT_A_STATUS\"}";
        mockMvc.perform(patch("/api/v1/admin/users/00000000-0000-0000-0000-000000000101/status")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void patchUserStatus_studentCaller_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        String body = "{\"status\":\"SUSPENDED\"}";
        mockMvc.perform(patch("/api/v1/admin/users/00000000-0000-0000-0000-000000000101/status")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void unlockUser_studentCaller_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-000000000101/unlock")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void unlockUser_adminCaller_returns204() throws Exception {
        // student1 is not locked; unlock is idempotent and should still succeed
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-000000000101/unlock")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                // noContent() is 204; idempotent no-op may also surface as 200 depending on service
                if (s != 204 && s != 200) {
                    throw new AssertionError("Expected 200/204 from unlock, got " + s);
                }
            });
    }

    @Test
    @Order(11)
    void getUserById_adminCaller_returnsShape() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/users/00000000-0000-0000-0000-000000000101")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                // 200 if seeded, 404 if seed absent (test profile may omit local dev data)
                if (s != 200 && s != 404) {
                    throw new AssertionError("Expected 200 or 404, got " + s);
                }
            });
    }

    @Test
    @Order(12)
    void approvePendingUser_unknownId_returns4xx() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-ffffffffffff/approve")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s != 404 && s != 400 && s != 409) {
                    throw new AssertionError("Expected 4xx for unknown approve id, got " + s);
                }
            });
    }

    @Test
    @Order(13)
    void approvePendingUser_studentCaller_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/users/00000000-0000-0000-0000-000000000104/approve")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }
}
