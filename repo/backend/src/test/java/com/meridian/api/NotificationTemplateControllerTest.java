package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationTemplateControllerTest extends TestContainersBase {

    private static final String BASE_URL = "/api/admin/notification-templates";

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: GET /api/admin/notification-templates as admin → 200, returns list
    @Test
    @Order(1)
    void listTemplates_asAdmin_returns200WithTemplates() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].name", notNullValue()))
                .andExpect(jsonPath("$[0].subject", notNullValue()))
                .andExpect(jsonPath("$[0].body", notNullValue()));
    }

    // Test 2: GET /api/admin/notification-templates unauthenticated → 401
    @Test
    @Order(2)
    void listTemplates_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 3: GET /api/admin/notification-templates as student → 403
    @Test
    @Order(3)
    void listTemplates_asStudent_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 4: PUT /api/admin/notification-templates/{name} as admin → 200, updates subject and body
    @Test
    @Order(4)
    void updateTemplate_asAdmin_returns200WithUpdatedValues() throws Exception {
        Map<String, String> body = Map.of(
                "subject", "Updated: Your account has been approved",
                "body", "Hello {{username}}, your account is ready. Updated body."
        );

        mockMvc.perform(put(BASE_URL + "/account_approved")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("account_approved")))
                .andExpect(jsonPath("$.subject", is("Updated: Your account has been approved")))
                .andExpect(jsonPath("$.body").value("Hello {{username}}, your account is ready. Updated body."))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    // Test 5: PUT /api/admin/notification-templates/{name} with blank subject → 400
    @Test
    @Order(5)
    void updateTemplate_blankSubject_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "subject", "",
                "body", "Some body text"
        );

        mockMvc.perform(put(BASE_URL + "/account_approved")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // Test 6: PUT /api/admin/notification-templates/{name} with unknown template name → 404
    @Test
    @Order(6)
    void updateTemplate_unknownName_returns404() throws Exception {
        Map<String, String> body = Map.of(
                "subject", "Some subject",
                "body", "Some body"
        );

        mockMvc.perform(put(BASE_URL + "/nonexistent_template_xyz")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // Test 7: PUT /api/admin/notification-templates/{name} as student → 403
    @Test
    @Order(7)
    void updateTemplate_asStudent_returns403() throws Exception {
        Map<String, String> body = Map.of(
                "subject", "Hacked subject",
                "body", "Hacked body"
        );

        mockMvc.perform(put(BASE_URL + "/account_approved")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // Test 8: PUT /api/admin/notification-templates/{name} unauthenticated → 401
    @Test
    @Order(8)
    void updateTemplate_unauthenticated_returns401() throws Exception {
        Map<String, String> body = Map.of(
                "subject", "Subject",
                "body", "Body"
        );

        mockMvc.perform(put(BASE_URL + "/account_approved")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
