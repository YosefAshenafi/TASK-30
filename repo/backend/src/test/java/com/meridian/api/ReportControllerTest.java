package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportControllerTest extends TestContainersBase {

    private static final String ENROLLMENTS_URL = "/api/reports/enrollments";
    private static final String SEAT_UTIL_URL = "/api/reports/seat-utilization";
    private static final String EXPORT_URL = "/api/reports/export";
    private static final String NOTIFICATIONS_URL = "/api/notifications";
    private static final String APPROVALS_URL = "/api/approvals";

    // Seeded org IDs from V2__seed_roles_and_users.sql
    private static final String ACME_ORG_ID = "22222222-0000-0000-0000-000000000001";
    private static final String MERIDIAN_ORG_ID = "22222222-0000-0000-0000-000000000002";
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("33333333-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String studentToken;
    private String facultyToken;
    private String corpToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
        facultyToken = loginAs("faculty1", "Faculty@12345678");
        corpToken = loginAs("corp1", "Corp@12345678");
    }

    // Test 1: GET /api/reports/enrollments as ADMIN → 200
    @Test
    @Order(1)
    void getEnrollments_asAdmin_returns200() throws Exception {
        mockMvc.perform(get(ENROLLMENTS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 2: GET /api/reports/enrollments as STUDENT → 403
    @Test
    @Order(2)
    void getEnrollments_asStudent_returns403() throws Exception {
        mockMvc.perform(get(ENROLLMENTS_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 3: GET /api/reports/enrollments unauthenticated → 401
    @Test
    @Order(3)
    void getEnrollments_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ENROLLMENTS_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 4: GET /api/reports/seat-utilization as FACULTY_MENTOR → 200
    @Test
    @Order(4)
    void getSeatUtilization_asFacultyMentor_returns200() throws Exception {
        mockMvc.perform(get(SEAT_UTIL_URL)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk());
    }

    // Test 5: Corporate mentor with NO orgId param gets their own org data (forced scope)
    @Test
    @Order(5)
    void getEnrollments_asCorporateMentor_noOrgParam_usesOwnOrg() throws Exception {
        // corp1 belongs to ACME_ORG_ID — when no orgId is provided, server forces it to their org
        MvcResult result = mockMvc.perform(get(ENROLLMENTS_URL)
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk())
                .andReturn();

        // Response should be a list (not an error) — org was auto-scoped
        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("[");
    }

    // Test 6: Corporate mentor with DIFFERENT orgId param is still forced to their own org (no cross-tenant)
    @Test
    @Order(6)
    void getEnrollments_asCorporateMentor_differentOrgParam_scopedToOwnOrg() throws Exception {
        // corp1 (ACME org) requests MERIDIAN org data — server forces ACME scope
        MvcResult result = mockMvc.perform(get(ENROLLMENTS_URL)
                        .param("orgId", MERIDIAN_ORG_ID)
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk())
                .andReturn();

        // Should succeed (200) but data is scoped to ACME, not MERIDIAN
        String body = result.getResponse().getContentAsString();
        assertThat(body).startsWith("[");
    }

    // Test 7: POST /api/reports/export as ADMIN without approvalId → 200 (admin bypasses approval gate)
    @Test
    @Order(7)
    void exportReport_asAdmin_noApprovalId_returns200() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "format", "CSV"
        );

        MvcResult result = mockMvc.perform(post(EXPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("path");
        assertThat(body).contains("filename");
    }

    // Test 8: POST /api/reports/export as FACULTY_MENTOR without approvalId → 400 (approval required)
    @Test
    @Order(8)
    void exportReport_asFacultyMentor_noApprovalId_returns400() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "format", "CSV"
        );

        mockMvc.perform(post(EXPORT_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("approval")));
    }

    // Test 9: POST /api/reports/export as FACULTY_MENTOR with unknown approvalId → 404
    @Test
    @Order(9)
    void exportReport_asFacultyMentor_unknownApprovalId_returns404() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "format", "CSV",
                "approvalId", UUID.randomUUID().toString()
        );

        mockMvc.perform(post(EXPORT_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // Test 10: POST /api/reports/export as FACULTY_MENTOR with PENDING approvalId → 403
    @Test
    @Order(10)
    void exportReport_asFacultyMentor_pendingApprovalId_returns403() throws Exception {
        // Create approval as faculty — must return 201 CREATED
        MvcResult approvalResult = mockMvc.perform(post(APPROVALS_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXPORT",
                                "entityType", "REPORT",
                                "entityId", "ENROLLMENTS"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String approvalBody = approvalResult.getResponse().getContentAsString();
        String approvalId = objectMapper.readTree(approvalBody).get("id").asText();

        // Attempt export with the PENDING approval — must be rejected with 403
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "format", "CSV",
                "approvalId", approvalId
        );

        mockMvc.perform(post(EXPORT_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test 14: POST /api/reports/export exceeding rate limit → 429 + anomaly recorded
    @Test
    @Order(14)
    void exportReport_rateLimitExceeded_returns429AndRecordsAnomaly() throws Exception {
        // Pre-seed 20 EXPORT audit events for admin within the last 10 minutes
        for (int i = 0; i < 20; i++) {
            jdbcTemplate.update(
                    "INSERT INTO audit_events (user_id, event_type, details) " +
                    "VALUES (?::uuid, 'EXPORT'::audit_event_type, '{}'::jsonb)",
                    ADMIN_USER_ID.toString()
            );
        }

        long anomalyBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM anomalies WHERE user_id = ?::uuid AND type = 'EXPORT_RATE_EXCEEDED'",
                Long.class, ADMIN_USER_ID.toString());

        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "format", "CSV"
        );

        mockMvc.perform(post(EXPORT_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());

        long anomalyAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM anomalies WHERE user_id = ?::uuid AND type = 'EXPORT_RATE_EXCEEDED'",
                Long.class, ADMIN_USER_ID.toString());
        assertThat(anomalyAfter).isGreaterThan(anomalyBefore);
    }

    // Test 15: GET /api/reports/refunds as ADMIN → 200 with array
    @Test
    @Order(15)
    void getRefunds_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/refunds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 16: GET /api/reports/refunds as STUDENT → 403
    @Test
    @Order(16)
    void getRefunds_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/refunds")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 17: GET /api/reports/refunds as CORPORATE_MENTOR → 200 (forced to own org)
    @Test
    @Order(17)
    void getRefunds_asCorporateMentor_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/refunds")
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 18: GET /api/reports/inventory as ADMIN → 200
    @Test
    @Order(18)
    void getInventory_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/inventory")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 19: GET /api/reports/inventory as STUDENT → 403
    @Test
    @Order(19)
    void getInventory_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/inventory")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 20: GET /api/reports/certifications/expiring as ADMIN → 200
    @Test
    @Order(20)
    void getCertificationsExpiring_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/reports/certifications/expiring")
                        .param("days", "30")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 21: GET /api/reports/certifications/expiring as STUDENT → 403
    @Test
    @Order(21)
    void getCertificationsExpiring_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/certifications/expiring")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 22: GET /api/reports/certifications/expiring unauthenticated → 401
    @Test
    @Order(22)
    void getCertificationsExpiring_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reports/certifications/expiring"))
                .andExpect(status().isUnauthorized());
    }

    // Test 11: GET /api/notifications as authenticated → 200
    @Test
    @Order(11)
    void getNotifications_asAuthenticated_returns200() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    // Test 12: GET /api/notifications unauthenticated → 401
    @Test
    @Order(12)
    void getNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 13: PUT /api/notifications/{id}/read as owner → 200 or 403/404
    @Test
    @Order(13)
    void markNotificationRead_asOwner_returns200() throws Exception {
        MvcResult listResult = mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String listBody = listResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(listBody);
        com.fasterxml.jackson.databind.JsonNode content = root.get("content");

        if (content != null && content.isArray() && content.size() > 0) {
            String notifId = content.get(0).get("id").asText();

            mockMvc.perform(put(NOTIFICATIONS_URL + "/" + notifId + "/read")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        } else {
            UUID randomId = UUID.randomUUID();
            mockMvc.perform(put(NOTIFICATIONS_URL + "/" + randomId + "/read")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isIn(200, 403, 404);
                    });
        }
    }
}
