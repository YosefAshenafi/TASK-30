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
class DraftAssessmentSyncControllerTest extends TestContainersBase {

    private static final String SYNC_URL = "/api/sessions/draft-assessments/sync";
    private static final UUID VALID_ITEM_ID =
            UUID.fromString("44444444-0000-0000-0000-000000000001");

    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: POST /api/sessions/draft-assessments/sync new record → 200, accepted=1
    @Test
    @Order(1)
    void sync_newDraft_returns200WithAccepted1() throws Exception {
        String key = "draft-key-" + UUID.randomUUID();

        List<Map<String, Object>> payload = List.of(Map.of(
                "idempotencyKey", key,
                "answer", "B",
                "flagged", false,
                "timeSpentSecs", 30,
                "lastModified", Instant.now().toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.duplicates").value(0));
    }

    // Test 2: POST sync same key with older lastModified → 200, duplicates=1 (LWW server wins)
    @Test
    @Order(2)
    void sync_existingKeyOlderTimestamp_returns200WithDuplicate() throws Exception {
        String key = "draft-lww-key-" + UUID.randomUUID();
        Instant now = Instant.now();

        List<Map<String, Object>> first = List.of(Map.of(
                "idempotencyKey", key,
                "answer", "A",
                "flagged", false,
                "timeSpentSecs", 10,
                "lastModified", now.toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        // Second sync with older timestamp — server version wins (duplicate)
        List<Map<String, Object>> stale = List.of(Map.of(
                "idempotencyKey", key,
                "answer", "B",
                "flagged", true,
                "timeSpentSecs", 20,
                "lastModified", now.minusSeconds(3600).toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    // Test 3: POST sync same key with newer lastModified → 200, accepted=1 (client wins)
    @Test
    @Order(3)
    void sync_existingKeyNewerTimestamp_returns200WithAccepted() throws Exception {
        String key = "draft-client-wins-key-" + UUID.randomUUID();
        Instant baseTime = Instant.now().minusSeconds(1000);

        List<Map<String, Object>> first = List.of(Map.of(
                "idempotencyKey", key,
                "answer", "A",
                "flagged", false,
                "timeSpentSecs", 10,
                "lastModified", baseTime.toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));

        // Second sync with newer timestamp — client wins
        List<Map<String, Object>> updated = List.of(Map.of(
                "idempotencyKey", key,
                "answer", "C",
                "flagged", true,
                "timeSpentSecs", 45,
                "lastModified", Instant.now().toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1));
    }

    // Test 4: POST sync unauthenticated → 401
    @Test
    @Order(4)
    void sync_unauthenticated_returns401() throws Exception {
        List<Map<String, Object>> payload = List.of(Map.of(
                "idempotencyKey", "unauth-draft-" + UUID.randomUUID(),
                "answer", "X",
                "flagged", false,
                "timeSpentSecs", 5,
                "lastModified", Instant.now().toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    // Test 5: POST sync missing idempotencyKey → 400
    @Test
    @Order(5)
    void sync_missingIdempotencyKey_returns400() throws Exception {
        List<Map<String, Object>> payload = List.of(Map.of(
                "answer", "X",
                "flagged", false,
                "timeSpentSecs", 5,
                "lastModified", Instant.now().toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    // Test 6: POST sync batch > 500 → 400
    @Test
    @Order(6)
    void sync_batchExceeds500_returns400() throws Exception {
        List<Map<String, Object>> large = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            large.add(Map.of(
                    "idempotencyKey", "bulk-draft-" + i + "-" + UUID.randomUUID(),
                    "answer", "A",
                    "flagged", false,
                    "timeSpentSecs", 1,
                    "lastModified", Instant.now().toString()
            ));
        }

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(large)))
                .andExpect(status().isBadRequest());
    }

    // Test 7: Response shape contains all required SyncResult fields
    @Test
    @Order(7)
    void sync_responseHasSyncResultShape() throws Exception {
        List<Map<String, Object>> payload = List.of(Map.of(
                "idempotencyKey", "shape-draft-" + UUID.randomUUID(),
                "answer", "B",
                "flagged", false,
                "timeSpentSecs", 15,
                "lastModified", Instant.now().toString()
        ));

        mockMvc.perform(post(SYNC_URL)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").isNumber())
                .andExpect(jsonPath("$.rejected").isNumber())
                .andExpect(jsonPath("$.duplicates").isNumber())
                .andExpect(jsonPath("$.rejectedKeys").isArray());
    }
}
