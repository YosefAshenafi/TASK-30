package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApprovalControllerTest extends TestContainersBase {

    private static final String APPROVALS_URL = "/api/approvals";

    private String adminToken;
    private String studentToken;
    private String facultyToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
        facultyToken = loginAs("faculty1", "Faculty@12345678");
    }

    // Test 1: POST /api/approvals as authenticated user → 201, status=PENDING
    @Test
    @Order(1)
    void createApproval_asAuthenticated_returns201WithPendingStatus() throws Exception {
        Map<String, Object> request = Map.of(
                "type", "EXPORT",
                "entityType", "REPORT",
                "entityId", "ENROLLMENTS"
        );

        mockMvc.perform(post(APPROVALS_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("EXPORT"))
                .andExpect(jsonPath("$.entityType").value("REPORT"))
                .andExpect(jsonPath("$.entityId").value("ENROLLMENTS"));
    }

    // Test 2: POST /api/approvals unauthenticated → 401
    @Test
    @Order(2)
    void createApproval_unauthenticated_returns401() throws Exception {
        Map<String, Object> request = Map.of(
                "type", "EXPORT",
                "entityType", "REPORT",
                "entityId", "ENROLLMENTS"
        );

        mockMvc.perform(post(APPROVALS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // Test 3: POST /api/approvals with missing type → 400
    @Test
    @Order(3)
    void createApproval_missingType_returns400() throws Exception {
        Map<String, Object> request = Map.of(
                "type", "",
                "entityType", "REPORT",
                "entityId", "ENROLLMENTS"
        );

        mockMvc.perform(post(APPROVALS_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Test 4: GET /api/approvals as admin → 200 with page structure
    @Test
    @Order(4)
    void getApprovals_asAdmin_returns200WithPageStructure() throws Exception {
        mockMvc.perform(get(APPROVALS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // Test 5: GET /api/approvals as student → 200 (sees own approvals)
    @Test
    @Order(5)
    void getApprovals_asStudent_returns200() throws Exception {
        mockMvc.perform(get(APPROVALS_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // Test 6: GET /api/approvals unauthenticated → 401
    @Test
    @Order(6)
    void getApprovals_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(APPROVALS_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 7: PUT /api/approvals/{id}/approve as ADMIN → 200, status=APPROVED
    @Test
    @Order(7)
    void approveApproval_asAdmin_returns200WithApprovedStatus() throws Exception {
        // Create approval first
        Map<String, Object> createRequest = Map.of(
                "type", "EXPORT",
                "entityType", "REPORT",
                "entityId", "ENROLLMENTS"
        );

        MvcResult createResult = mockMvc.perform(post(APPROVALS_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String approvalId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put(APPROVALS_URL + "/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // Test 8: PUT /api/approvals/{id}/reject as ADMIN → 200, status=REJECTED
    @Test
    @Order(8)
    void rejectApproval_asAdmin_returns200WithRejectedStatus() throws Exception {
        // Create approval first
        Map<String, Object> createRequest = Map.of(
                "type", "EXPORT",
                "entityType", "REPORT",
                "entityId", "SEAT_UTILIZATION"
        );

        MvcResult createResult = mockMvc.perform(post(APPROVALS_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String approvalId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put(APPROVALS_URL + "/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // Test 9: PUT /api/approvals/{id}/reject as non-admin → 403
    @Test
    @Order(9)
    void rejectApproval_asNonAdmin_returns403() throws Exception {
        // Create approval first
        Map<String, Object> createRequest = Map.of(
                "type", "EXPORT",
                "entityType", "REPORT",
                "entityId", "REFUNDS"
        );

        MvcResult createResult = mockMvc.perform(post(APPROVALS_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String approvalId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put(APPROVALS_URL + "/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 10: PUT /api/approvals/{unknown-id}/reject as ADMIN → 404
    @Test
    @Order(10)
    void rejectApproval_unknownId_returns404() throws Exception {
        String unknownId = UUID.randomUUID().toString();

        mockMvc.perform(put(APPROVALS_URL + "/" + unknownId + "/reject")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
