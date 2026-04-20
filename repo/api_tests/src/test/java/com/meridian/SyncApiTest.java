package com.meridian;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for §3 Training Sessions — sync conflict matrix.
 *
 * Covers: CREATED, UPDATED, NOOP, OLDER_CLIENT_TIMESTAMP, IDEMPOTENCY_MISMATCH.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "classpath:sync-test-setup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SyncApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String SYNC_URL = "/api/v1/sessions/sync";
    private static final String SESSION_UUID = "01900000-0000-7000-8000-000000000001";

    @Test
    @Order(1)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncNewSession_returnsCreated() throws Exception {
        String body = syncPayload(SESSION_UUID, "2026-04-20T09:00:00Z", "idem-key-1");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-key-1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0].status").value("CREATED"));
    }

    @Test
    @Order(2)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncSameBodySameKey_returnsNoop() throws Exception {
        String body = syncPayload(SESSION_UUID, "2026-04-20T09:00:00Z", "idem-key-1");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-key-1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0].status").value("NOOP"));
    }

    @Test
    @Order(3)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncNewerTimestamp_returnsUpdated() throws Exception {
        String body = syncPayload(SESSION_UUID, "2026-04-20T10:00:00Z", "idem-key-2");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-key-2")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0].status").value("UPDATED"));
    }

    @Test
    @Order(4)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncOlderTimestamp_returnsOlderClientTimestampConflict() throws Exception {
        String body = syncPayload(SESSION_UUID, "2026-04-20T08:00:00Z", "idem-key-3");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-key-3")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conflicts[0].reason").value("OLDER_CLIENT_TIMESTAMP"));
    }

    private static final String ACTIVITY_UUID = "00000000-0000-0000-0000-000000000a01";

    @Test
    @Order(5)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncNewSet_returnsCreated() throws Exception {
        String body = syncSetPayload(SESSION_UUID, ACTIVITY_UUID, "2026-04-20T09:00:00Z", "set-idem-key-1");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "set-idem-key-1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0].status").value("CREATED"));
    }

    @Test
    @Order(6)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncSetOlderTimestamp_returnsConflict() throws Exception {
        String body = syncSetPayload(SESSION_UUID, ACTIVITY_UUID, "2026-04-20T08:00:00Z", "set-idem-key-2");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "set-idem-key-2")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conflicts[0].reason").value("OLDER_CLIENT_TIMESTAMP"));
    }

    @Test
    @Order(7)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncSessionIdempotencyMismatch_returnsConflict() throws Exception {
        // same key as order-1 but different body (different timestamp)
        String body = syncPayload(SESSION_UUID, "2026-04-20T11:00:00Z", "idem-key-1");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "idem-key-1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conflicts[0].reason").value("IDEMPOTENCY_MISMATCH"));
    }

    @Test
    @Order(8)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncSetSameKey_returnsNoop() throws Exception {
        // same key and same body as order 5 → NOOP
        String body = syncSetPayload(SESSION_UUID, ACTIVITY_UUID, "2026-04-20T09:00:00Z", "set-idem-key-1");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "set-idem-key-1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0].status").value("NOOP"));
    }

    @Test
    @Order(9)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncSetNewerTimestamp_returnsUpdated() throws Exception {
        String body = syncSetPayload(SESSION_UUID, ACTIVITY_UUID, "2026-04-20T10:00:00Z", "set-idem-key-3");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "set-idem-key-3")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied[0].status").value("UPDATED"));
    }

    @Test
    @Order(10)
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void syncSetIdempotencyMismatch_returnsConflict() throws Exception {
        // same key as order 5 but different body (different timestamp) → IDEMPOTENCY_MISMATCH
        String body = syncSetPayload(SESSION_UUID, ACTIVITY_UUID, "2026-04-20T11:00:00Z", "set-idem-key-1");
        mockMvc.perform(post(SYNC_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "set-idem-key-1")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conflicts[0].reason").value("IDEMPOTENCY_MISMATCH"));
    }

    private String syncPayload(String sessionId, String clientUpdatedAt, String idemKey) {
        return """
            {
              "sessions": [{
                "id": "%s",
                "courseId": "00000000-0000-0000-0000-000000000c10",
                "cohortId": null,
                "startedAt": "2026-04-20T09:00:00Z",
                "endedAt": null,
                "restSecondsDefault": 60,
                "status": "IN_PROGRESS",
                "clientUpdatedAt": "%s",
                "idempotencyKey": "%s"
              }],
              "sets": []
            }
            """.formatted(sessionId, clientUpdatedAt, idemKey);
    }

    private String syncSetPayload(String sessionId, String activityId,
                                   String clientUpdatedAt, String idemKey) {
        return """
            {
              "sessions": [],
              "sets": [{
                "idempotencyKey": "%s",
                "sessionId": "%s",
                "activityId": "%s",
                "setIndex": 1,
                "restSeconds": 60,
                "completedAt": null,
                "notes": null,
                "clientUpdatedAt": "%s"
              }]
            }
            """.formatted(idemKey, sessionId, activityId, clientUpdatedAt);
    }
}
