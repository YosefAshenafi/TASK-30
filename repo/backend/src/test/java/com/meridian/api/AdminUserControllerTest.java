package com.meridian.api;

import com.meridian.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest extends TestContainersBase {

    private static final String PENDING_URL = "/api/admin/users/pending";
    private static final String USERS_URL = "/api/admin/users";
    private static final String REJECT_URL_TEMPLATE = "/api/admin/users/%s/reject";
    private static final String ROLE_URL_TEMPLATE = "/api/admin/users/%s/role";

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");

        // Register + approve a student so we can get a student token
        String studentUsername = "student_admin_test_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(studentUsername, "Student@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Approve this student via admin
        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String pendingBody = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode pendingList = objectMapper.readTree(pendingBody);

        for (com.fasterxml.jackson.databind.JsonNode node : pendingList) {
            if (studentUsername.equals(node.get("username").asText())) {
                String userId = node.get("id").asText();
                mockMvc.perform(put("/api/admin/users/" + userId + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk());
                break;
            }
        }

        studentToken = loginAs(studentUsername, "Student@12345678!");
    }

    // Test 1: GET /pending as ADMIN returns 200
    @Test
    void getPending_asAdmin_returns200() throws Exception {
        mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 2: GET /pending as STUDENT returns 403
    @Test
    void getPending_asStudent_returns403() throws Exception {
        mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 3: GET /pending unauthenticated returns 401
    @Test
    void getPending_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(PENDING_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 4: PUT /{id}/approve as ADMIN returns 200
    @Test
    void approve_asAdmin_returns200() throws Exception {
        String newUsername = "approve_test_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "Approve@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);
        String targetId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                targetId = node.get("id").asText();
                break;
            }
        }

        org.assertj.core.api.Assertions.assertThat(targetId).isNotNull();

        mockMvc.perform(put("/api/admin/users/" + targetId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // Test 5: PUT /{id}/approve as non-admin returns 403
    @Test
    void approve_asNonAdmin_returns403() throws Exception {
        String newUsername = "nonadmin_approve_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "NonAdmin@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);
        String targetId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                targetId = node.get("id").asText();
                break;
            }
        }

        org.assertj.core.api.Assertions.assertThat(targetId).isNotNull();

        mockMvc.perform(put("/api/admin/users/" + targetId + "/approve")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 6: GET /admin/users paginated as ADMIN returns 200 with content
    @Test
    void getUsers_asAdmin_returns200WithContent() throws Exception {
        mockMvc.perform(get(USERS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }

    // Test 7: Pending users have pendingDeadlineAt and overdue SLA fields
    @Test
    void pendingUsers_haveSlaDeadlineFields() throws Exception {
        String newUsername = "sla_test_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "SlaTest@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);

        boolean found = false;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                assertThat(node.has("pendingDeadlineAt")).isTrue();
                assertThat(node.get("pendingDeadlineAt").isNull()).isFalse();
                assertThat(node.has("overdue")).isTrue();
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    // Test 9: PUT /admin/users/{id}/reject as ADMIN → 200, status=REJECTED
    @Test
    void rejectUser_asAdmin_returns200WithRejectedStatus() throws Exception {
        String newUsername = "reject_test_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "Reject@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);
        String targetId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                targetId = node.get("id").asText();
                break;
            }
        }
        assertThat(targetId).isNotNull();

        mockMvc.perform(put(String.format(REJECT_URL_TEMPLATE, targetId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // Test 10: PUT /admin/users/{id}/reject as non-admin → 403
    @Test
    void rejectUser_asNonAdmin_returns403() throws Exception {
        String newUsername = "reject_nonadmin_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "Reject@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);
        String targetId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                targetId = node.get("id").asText();
                break;
            }
        }
        assertThat(targetId).isNotNull();

        mockMvc.perform(put(String.format(REJECT_URL_TEMPLATE, targetId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 11: PATCH /admin/users/{id}/role as ADMIN → 200, role updated
    @Test
    void changeRole_asAdmin_returns200WithUpdatedRole() throws Exception {
        String newUsername = "role_change_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "RoleChange@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Approve the user first so they have a valid account
        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);
        String targetId = null;
        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                targetId = node.get("id").asText();
                break;
            }
        }
        assertThat(targetId).isNotNull();

        mockMvc.perform(put("/api/admin/users/" + targetId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Now change their role to FACULTY_MENTOR
        mockMvc.perform(patch(String.format(ROLE_URL_TEMPLATE, targetId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "ROLE_FACULTY_MENTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FACULTY_MENTOR"));
    }

    // Test 12: PATCH /admin/users/{id}/role as non-admin → 403
    @Test
    void changeRole_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(patch(String.format(ROLE_URL_TEMPLATE, "33333333-0000-0000-0000-000000000004"))
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "ROLE_FACULTY_MENTOR"))))
                .andExpect(status().isForbidden());
    }

    // Test 13: PATCH /admin/users/{id}/role with unknown role → 404
    @Test
    void changeRole_unknownRole_returns404() throws Exception {
        mockMvc.perform(patch(String.format(ROLE_URL_TEMPLATE, "33333333-0000-0000-0000-000000000004"))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "ROLE_DOES_NOT_EXIST"))))
                .andExpect(status().isNotFound());
    }

    // Test 8: Pending user maskedEmployeeId and maskedContact fields are present
    @Test
    void pendingUsers_haveMaskedSensitiveFields() throws Exception {
        String newUsername = "mask_test_" + System.currentTimeMillis();
        RegisterRequest reg = new RegisterRequest(newUsername, "MaskTest@12345678!", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        MvcResult pendingResult = mockMvc.perform(get(PENDING_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = pendingResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode list = objectMapper.readTree(body);

        for (com.fasterxml.jackson.databind.JsonNode node : list) {
            if (newUsername.equals(node.get("username").asText())) {
                assertThat(node.has("maskedEmployeeId")).isTrue();
                assertThat(node.has("maskedContact")).isTrue();
                break;
            }
        }
    }
}
