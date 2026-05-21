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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportScheduleControllerTest extends TestContainersBase {

    private static final String SCHEDULES_URL = "/api/reports/schedules";
    // Valid Quartz cron: at 06:00:00 every day
    private static final String VALID_CRON = "0 0 6 * * ?";

    private String adminToken;
    private String corpToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        corpToken = loginAs("corp1", "Corp@12345678");
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: POST /api/reports/schedules as ADMIN → 201 with schedule fields
    @Test
    @Order(1)
    void createSchedule_asAdmin_returns201() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "cronExpression", VALID_CRON,
                "outputFormat", "CSV",
                "outputPath", "/tmp/reports/enrollments"
        );

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.reportType").value("ENROLLMENTS"))
                .andExpect(jsonPath("$.cronExpression").value(VALID_CRON))
                .andExpect(jsonPath("$.outputFormat").value("CSV"));
    }

    // Test 2: POST /api/reports/schedules as CORPORATE_MENTOR → 201
    @Test
    @Order(2)
    void createSchedule_asCorporateMentor_returns201() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "SEAT_UTILIZATION",
                "cronExpression", VALID_CRON,
                "outputFormat", "PDF",
                "outputPath", "/tmp/reports/seat-util"
        );

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + corpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value("SEAT_UTILIZATION"));
    }

    // Test 3: POST /api/reports/schedules as STUDENT → 403
    @Test
    @Order(3)
    void createSchedule_asStudent_returns403() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "cronExpression", VALID_CRON,
                "outputFormat", "CSV",
                "outputPath", "/tmp/reports"
        );

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test 4: POST /api/reports/schedules with missing reportType → 400
    @Test
    @Order(4)
    void createSchedule_missingReportType_returns400() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "",
                "cronExpression", VALID_CRON,
                "outputFormat", "CSV",
                "outputPath", "/tmp/reports"
        );

        mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Test 5: POST /api/reports/schedules unauthenticated → 401
    @Test
    @Order(5)
    void createSchedule_unauthenticated_returns401() throws Exception {
        Map<String, Object> request = Map.of(
                "reportType", "ENROLLMENTS",
                "cronExpression", VALID_CRON,
                "outputFormat", "CSV",
                "outputPath", "/tmp/reports"
        );

        mockMvc.perform(post(SCHEDULES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // Test 6: GET /api/reports/schedules as ADMIN → 200 with page structure
    @Test
    @Order(6)
    void listSchedules_asAdmin_returns200WithPageStructure() throws Exception {
        mockMvc.perform(get(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // Test 7: GET /api/reports/schedules as CORPORATE_MENTOR → 200
    @Test
    @Order(7)
    void listSchedules_asCorporateMentor_returns200() throws Exception {
        mockMvc.perform(get(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // Test 8: GET /api/reports/schedules unauthenticated → 401
    @Test
    @Order(8)
    void listSchedules_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(SCHEDULES_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 9: DELETE /api/reports/schedules/{id} as ADMIN → 204
    @Test
    @Order(9)
    void deleteSchedule_asAdmin_ownSchedule_returns204() throws Exception {
        // Create a schedule first
        Map<String, Object> createRequest = Map.of(
                "reportType", "INVENTORY",
                "cronExpression", VALID_CRON,
                "outputFormat", "CSV",
                "outputPath", "/tmp/reports/inv"
        );

        MvcResult createResult = mockMvc.perform(post(SCHEDULES_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String scheduleId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete(SCHEDULES_URL + "/" + scheduleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // Test 10: DELETE /api/reports/schedules/{unknown-id} as ADMIN → 404
    @Test
    @Order(10)
    void deleteSchedule_unknownId_returns404() throws Exception {
        mockMvc.perform(delete(SCHEDULES_URL + "/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // Test 11: DELETE /api/reports/schedules/{id} as STUDENT → 403
    @Test
    @Order(11)
    void deleteSchedule_asStudent_returns403() throws Exception {
        mockMvc.perform(delete(SCHEDULES_URL + "/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
