package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecoveryDrillControllerTest extends TestContainersBase {

    private static final String BASE_URL = "/api/admin/recovery-drills";

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: POST /api/admin/recovery-drills as admin → 201 with drill details
    @Test
    @Order(1)
    void recordDrill_asAdmin_returns201WithDrillDetails() throws Exception {
        Map<String, Object> body = Map.of(
                "drillDate", LocalDate.now().toString(),
                "stepsCompleted", 7,
                "totalSteps", 8,
                "outcome", "PASS",
                "notes", "Q1 quarterly DR drill completed successfully"
        );

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.drillDate", is(LocalDate.now().toString())))
                .andExpect(jsonPath("$.stepsCompleted", is(7)))
                .andExpect(jsonPath("$.totalSteps", is(8)))
                .andExpect(jsonPath("$.outcome", is("PASS")))
                .andExpect(jsonPath("$.notes", is("Q1 quarterly DR drill completed successfully")));
    }

    // Test 2: POST /api/admin/recovery-drills with FAIL outcome → 201
    @Test
    @Order(2)
    void recordDrill_failOutcome_returns201() throws Exception {
        Map<String, Object> body = Map.of(
                "drillDate", LocalDate.now().minusDays(1).toString(),
                "stepsCompleted", 3,
                "totalSteps", 8,
                "outcome", "FAIL",
                "notes", "Drill failed at step 4 — replication lag exceeded threshold"
        );

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome", is("FAIL")))
                .andExpect(jsonPath("$.stepsCompleted", is(3)));
    }

    // Test 3: POST /api/admin/recovery-drills with missing drillDate → 400
    @Test
    @Order(3)
    void recordDrill_missingDrillDate_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "stepsCompleted", 5,
                "totalSteps", 8,
                "outcome", "PASS"
        );

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // Test 4: POST /api/admin/recovery-drills with invalid outcome → 400
    @Test
    @Order(4)
    void recordDrill_invalidOutcome_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "drillDate", LocalDate.now().toString(),
                "stepsCompleted", 5,
                "totalSteps", 8,
                "outcome", "UNKNOWN_OUTCOME"
        );

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // Test 5: POST /api/admin/recovery-drills as student → 403
    @Test
    @Order(5)
    void recordDrill_asStudent_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "drillDate", LocalDate.now().toString(),
                "stepsCompleted", 8,
                "totalSteps", 8,
                "outcome", "PASS"
        );

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // Test 6: POST /api/admin/recovery-drills unauthenticated → 401
    @Test
    @Order(6)
    void recordDrill_unauthenticated_returns401() throws Exception {
        Map<String, Object> body = Map.of(
                "drillDate", LocalDate.now().toString(),
                "stepsCompleted", 8,
                "totalSteps", 8,
                "outcome", "PASS"
        );

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // Test 7: GET /api/admin/recovery-drills as admin → 200 with page structure
    @Test
    @Order(7)
    void listDrills_asAdmin_returns200WithPageStructure() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // Test 8: GET /api/admin/recovery-drills as student → 403
    @Test
    @Order(8)
    void listDrills_asStudent_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 9: GET /api/admin/recovery-drills unauthenticated → 401
    @Test
    @Order(9)
    void listDrills_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }
}
