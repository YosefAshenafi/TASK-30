package com.meridian;

import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-15 / B-16 / B-17 (§A.7) — AttemptDraft offline-first endpoint coverage.
 *
 * The controller enforces session ownership — a student may only read/write
 * drafts on their own session. Authz tests pin this behaviour.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttemptDraftApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static String studentToken;
    private static String sessionId;
    private static final String ITEM_ID = "00000000-0000-0000-0000-000000000600";

    @Test
    @Order(1)
    void setup_createSession() throws Exception {
        studentToken = TestAuthHelper.loginStudent1(mockMvc);
        sessionId = UUID.randomUUID().toString();
        String body = """
            {
              "id": "%s",
              "courseId": "00000000-0000-0000-0000-000000000200",
              "restSecondsDefault": 30,
              "startedAt": "2026-04-24T10:00:00Z",
              "clientUpdatedAt": "2026-04-24T10:00:00Z"
            }
            """.formatted(sessionId);
        mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    void upsertDraft_ownerStudent_returns202() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String draftId = "draft-" + UUID.randomUUID();
        String body = """
            {
              "id": "%s",
              "sessionId": "%s",
              "itemId": "%s",
              "chosenAnswer": "100-120",
              "clientUpdatedAt": "2026-04-24T10:05:00Z"
            }
            """.formatted(draftId, sessionId, ITEM_ID);
        mockMvc.perform(post("/api/v1/sessions/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(draftId))
            .andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @Test
    @Order(3)
    void listDrafts_ownerStudent_returns200WithList() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].sessionId").value(sessionId));
    }

    @Test
    @Order(4)
    void listDrafts_foreignStudent_returns403() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String otherToken = TestAuthHelper.loginStudent2(mockMvc);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void upsertDraft_foreignStudent_returns403() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String otherToken = TestAuthHelper.loginStudent2(mockMvc);
        String body = """
            {
              "id": "foreign-%s",
              "sessionId": "%s",
              "itemId": "%s",
              "chosenAnswer": "guess",
              "clientUpdatedAt": "2026-04-24T10:06:00Z"
            }
            """.formatted(UUID.randomUUID(), sessionId, ITEM_ID);
        mockMvc.perform(post("/api/v1/sessions/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void upsertDraft_anonymous_returns401() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String body = """
            {
              "id": "anon-%s",
              "sessionId": "%s",
              "itemId": "%s",
              "chosenAnswer": "x",
              "clientUpdatedAt": "2026-04-24T10:07:00Z"
            }
            """.formatted(UUID.randomUUID(), sessionId, ITEM_ID);
        mockMvc.perform(post("/api/v1/sessions/attempt-drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void submitAttempts_ownerStudent_returns200WithResult() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/submit-attempts")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saved").isNumber())
            .andExpect(jsonPath("$.correct").isNumber());
    }

    @Test
    @Order(8)
    void listDrafts_afterSubmit_isEmpty() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(9)
    void clearDrafts_foreignStudent_returns403() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String otherToken = TestAuthHelper.loginStudent2(mockMvc);
        mockMvc.perform(delete("/api/v1/sessions/" + sessionId + "/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void clearDrafts_ownerStudent_returns204() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(delete("/api/v1/sessions/" + sessionId + "/attempt-drafts")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isNoContent());
    }
}
