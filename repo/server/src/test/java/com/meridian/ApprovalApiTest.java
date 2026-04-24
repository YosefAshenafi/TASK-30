package com.meridian;

import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-21 / B-22 (§A.7) — ApprovalController coverage.
 * Real-JWT flow via TestAuthHelper (M-1 remediation).
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApprovalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void listApprovals_adminJwt_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/approvals").header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(2)
    void listApprovals_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/approvals").header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void listApprovals_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/approvals"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void listApprovals_withStatusFilter_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/approvals?status=PENDING")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(5)
    void approveApproval_unknownId_returns4xx() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = "{\"reason\":\"approved via test\"}";
        mockMvc.perform(post("/api/v1/admin/approvals/00000000-0000-0000-0000-ffffffffffff/approve")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s < 400 || s >= 500) {
                    throw new AssertionError("Expected 4xx for unknown approval id, got " + s);
                }
            });
    }

    @Test
    @Order(6)
    void approveApproval_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/approvals/00000000-0000-0000-0000-000000000aa1/approve")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"not my role\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void rejectApproval_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/approvals/00000000-0000-0000-0000-000000000aa1/reject")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"not my role\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void rejectApproval_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/approvals/00000000-0000-0000-0000-000000000aa1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }
}
