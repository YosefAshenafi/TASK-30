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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionControllerTest extends TestContainersBase {

    private static final String SESSIONS_URL = "/api/sessions";
    // Seeded course from V2 migration
    private static final UUID VALID_COURSE_ID =
            UUID.fromString("44444444-0000-0000-0000-000000000001");
    private static final UUID INVALID_COURSE_ID = UUID.randomUUID();

    private String studentToken;
    private String anotherStudentToken;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: POST /api/sessions with valid courseId as STUDENT → 201
    @Test
    @Order(1)
    void createSession_withValidCourse_returns201() throws Exception {
        Map<String, Object> request = Map.of("courseId", VALID_COURSE_ID.toString());

        mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(VALID_COURSE_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.restTimerSecs").value(60));
    }

    // Test 2: POST /api/sessions with invalid courseId → 404
    @Test
    @Order(2)
    void createSession_withInvalidCourse_returns404() throws Exception {
        Map<String, Object> request = Map.of("courseId", INVALID_COURSE_ID.toString());

        mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // Test 3: GET /api/sessions as STUDENT → 200, only own sessions
    @Test
    @Order(3)
    void getSessions_asStudent_returns200AndOwnSessions() throws Exception {
        // First create a session to ensure there is at least one
        Map<String, Object> request = Map.of("courseId", VALID_COURSE_ID.toString());
        mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("IN_PROGRESS");
    }

    // Test 4: GET /api/sessions/{id} as correct owner → 200
    @Test
    @Order(4)
    void getSession_asOwner_returns200() throws Exception {
        Map<String, Object> request = Map.of("courseId", VALID_COURSE_ID.toString());

        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get(SESSIONS_URL + "/" + sessionId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));
    }

    // Test 5: GET /api/sessions/{id} as different user → 403
    @Test
    @Order(5)
    void getSession_asDifferentUser_returns403() throws Exception {
        // Create session as student1
        Map<String, Object> request = Map.of("courseId", VALID_COURSE_ID.toString());
        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // Try to access as faculty1 (different user)
        String facultyToken = loginAs("faculty1", "Faculty@12345678");

        mockMvc.perform(get(SESSIONS_URL + "/" + sessionId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }

    // Test 6: PUT /api/sessions/{id} with restTimerSecs=5 → 400
    @Test
    @Order(6)
    void updateSession_withInvalidRestTimer_returns400() throws Exception {
        Map<String, Object> createRequest = Map.of("courseId", VALID_COURSE_ID.toString());
        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        Map<String, Object> updateRequest = Map.of("restTimerSecs", 5);

        mockMvc.perform(put(SESSIONS_URL + "/" + sessionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    // Test 7: PUT /api/sessions/{id} with restTimerSecs=120 as owner → 200
    @Test
    @Order(7)
    void updateSession_withValidRestTimer_returns200() throws Exception {
        Map<String, Object> createRequest = Map.of("courseId", VALID_COURSE_ID.toString());
        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        Map<String, Object> updateRequest = Map.of("restTimerSecs", 120);

        mockMvc.perform(put(SESSIONS_URL + "/" + sessionId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restTimerSecs").value(120));
    }

    // Test 8: POST /api/sessions/{id}/complete → 200, status=COMPLETED
    @Test
    @Order(8)
    void completeSession_asOwner_returns200WithCompletedStatus() throws Exception {
        Map<String, Object> createRequest = Map.of("courseId", VALID_COURSE_ID.toString());
        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post(SESSIONS_URL + "/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // Test 9: GET /api/sessions unauthorized → 401
    @Test
    @Order(9)
    void getSessions_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(SESSIONS_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 10: GET /api/sessions/{id}/activities as owner → 200 with array
    @Test
    @Order(10)
    void getActivities_asOwner_returns200() throws Exception {
        Map<String, Object> createRequest = Map.of("courseId", VALID_COURSE_ID.toString());
        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get(SESSIONS_URL + "/" + sessionId + "/activities")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    // Test 11: GET /api/sessions/{id}/activities as different user → 403
    @Test
    @Order(11)
    void getActivities_asDifferentUser_returns403() throws Exception {
        Map<String, Object> createRequest = Map.of("courseId", VALID_COURSE_ID.toString());
        MvcResult createResult = mockMvc.perform(post(SESSIONS_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        String facultyToken = loginAs("faculty1", "Faculty@12345678");
        mockMvc.perform(get(SESSIONS_URL + "/" + sessionId + "/activities")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }

    // Test 12: GET /api/sessions/{id}/activities unauthenticated → 401
    @Test
    @Order(12)
    void getActivities_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(SESSIONS_URL + "/" + UUID.randomUUID() + "/activities"))
                .andExpect(status().isUnauthorized());
    }
}
