package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionSyncControllerTest extends TestContainersBase {

    private static final String SYNC_URL = "/api/sessions/sync";
    private static final UUID VALID_COURSE_ID =
            UUID.fromString("44444444-0000-0000-0000-000000000001");

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: POST /api/sessions/sync with new record → 200, accepted=1
    @Test
    @Order(1)
    void sync_newRecord_returns200WithAccepted1() throws Exception {
        String idempotencyKey = "sync-test-key-" + UUID.randomUUID();

        List<Map<String, Object>> requests = List.of(Map.of(
                "idempotencyKey", idempotencyKey,
                "courseId", VALID_COURSE_ID.toString(),
                "courseVersion", "1.0",
                "status", "IN_PROGRESS",
                "restTimerSecs", 60,
                "startedAt", Instant.now().toString(),
                "clientUpdatedAt", Instant.now().toString(),
                "activities", List.of()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.rejected").value(0));
    }

    // Test 2: POST /api/sessions/sync duplicate idempotency key → 200, duplicates=1
    @Test
    @Order(2)
    void sync_duplicateIdempotencyKey_returns200WithDuplicates1() throws Exception {
        String idempotencyKey = "sync-duplicate-key-" + UUID.randomUUID();

        List<Map<String, Object>> firstRequest = List.of(Map.of(
                "idempotencyKey", idempotencyKey,
                "courseId", VALID_COURSE_ID.toString(),
                "courseVersion", "1.0",
                "status", "IN_PROGRESS",
                "restTimerSecs", 60,
                "startedAt", Instant.now().toString(),
                "clientUpdatedAt", Instant.now().minusSeconds(3600).toString(),
                "activities", List.of()
        ));

        // First sync — creates the record
        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        // Second sync with same key but older clientUpdatedAt — should be duplicate
        List<Map<String, Object>> duplicateRequest = List.of(Map.of(
                "idempotencyKey", idempotencyKey,
                "courseId", VALID_COURSE_ID.toString(),
                "courseVersion", "1.0",
                "status", "IN_PROGRESS",
                "restTimerSecs", 60,
                "startedAt", Instant.now().minusSeconds(7200).toString(),
                "clientUpdatedAt", Instant.now().minusSeconds(7200).toString(),
                "activities", List.of()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    // Test 3: POST /api/sessions/sync list of >500 → 400
    @Test
    @Order(3)
    void sync_batchExceeds500_returns400() throws Exception {
        List<Map<String, Object>> largeRequest = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            largeRequest.add(Map.of(
                    "idempotencyKey", "bulk-key-" + i + "-" + UUID.randomUUID(),
                    "courseId", VALID_COURSE_ID.toString(),
                    "courseVersion", "1.0",
                    "status", "IN_PROGRESS",
                    "restTimerSecs", 60,
                    "startedAt", Instant.now().toString(),
                    "clientUpdatedAt", Instant.now().toString(),
                    "activities", List.of()
            ));
        }

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(largeRequest)))
                .andExpect(status().isBadRequest());
    }

    // Test 4: POST /api/sessions/sync unauthenticated → 401
    @Test
    @Order(4)
    void sync_unauthenticated_returns401() throws Exception {
        List<Map<String, Object>> requests = List.of(Map.of(
                "idempotencyKey", "unauth-key-" + UUID.randomUUID(),
                "courseId", VALID_COURSE_ID.toString(),
                "courseVersion", "1.0",
                "status", "IN_PROGRESS",
                "restTimerSecs", 60,
                "startedAt", Instant.now().toString(),
                "clientUpdatedAt", Instant.now().toString(),
                "activities", List.of()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isUnauthorized());
    }

    // Test 5: Sync response contains accepted, rejected, duplicates, rejectedKeys fields
    @Test
    @Order(5)
    void sync_response_hasSyncResultShape() throws Exception {
        String idempotencyKey = "shape-test-key-" + UUID.randomUUID();

        List<Map<String, Object>> requests = List.of(Map.of(
                "idempotencyKey", idempotencyKey,
                "courseId", VALID_COURSE_ID.toString(),
                "courseVersion", "1.0",
                "status", "IN_PROGRESS",
                "restTimerSecs", 60,
                "startedAt", Instant.now().toString(),
                "clientUpdatedAt", Instant.now().toString(),
                "activities", List.of()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").isNumber())
                .andExpect(jsonPath("$.rejected").isNumber())
                .andExpect(jsonPath("$.duplicates").isNumber())
                .andExpect(jsonPath("$.rejectedKeys").isArray());
    }
}
