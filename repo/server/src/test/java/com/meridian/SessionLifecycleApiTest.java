package com.meridian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-11 … B-14 (§A.7) — Session state-machine HTTP coverage.
 * create → pause → continue → complete, plus 409 on illegal transitions.
 * Uses real-JWT login as student1 (M-1 remediation).
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionLifecycleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private static String studentToken;
    private static String sessionId;
    private static String setId;

    @Test
    @Order(1)
    void login_asStudent_obtainsBearer() throws Exception {
        studentToken = TestAuthHelper.loginStudent1(mockMvc);
        Assumptions.assumeTrue(studentToken != null && studentToken.startsWith("Bearer "),
                "Must obtain a real JWT for student1 before lifecycle tests");
    }

    @Test
    @Order(2)
    void createSession_studentJwt_returns201() throws Exception {
        Assumptions.assumeTrue(studentToken != null);
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
        MvcResult res = mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(sessionId))
            .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        if (!sessionId.equals(json.get("id").asText())) {
            throw new AssertionError("Session id mismatch");
        }
    }

    @Test
    @Order(3)
    void pauseSession_whenInProgress_returns200() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/pause")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @Order(4)
    void pauseSession_whenAlreadyPaused_returns409() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/pause")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void continueSession_whenPaused_returns200AndStatusInProgress() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/continue")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @Order(6)
    void completeSession_returns200AndStatusCompleted() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/complete")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @Order(7)
    void pauseCompletedSession_returns409() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/pause")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isConflict());
    }

    @Test
    @Order(8)
    void getSession_byOwner_returns200() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @Order(9)
    void getSession_byForeignStudent_returns403or404() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String otherToken = TestAuthHelper.loginStudent2(mockMvc);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s != 403 && s != 404) {
                    throw new AssertionError("Expected 403/404 for foreign session access, got " + s);
                }
            });
    }

    @Test
    @Order(10)
    void createSession_anonymous_returns401() throws Exception {
        String body = """
            {
              "id": "%s",
              "courseId": "00000000-0000-0000-0000-000000000200",
              "restSecondsDefault": 30,
              "startedAt": "2026-04-24T11:00:00Z",
              "clientUpdatedAt": "2026-04-24T11:00:00Z"
            }
            """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    void createSession_adminJwt_returns403() throws Exception {
        // POST /sessions is restricted to STUDENT role in SecurityConfig
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = """
            {
              "id": "%s",
              "courseId": "00000000-0000-0000-0000-000000000200",
              "restSecondsDefault": 30,
              "startedAt": "2026-04-24T12:00:00Z",
              "clientUpdatedAt": "2026-04-24T12:00:00Z"
            }
            """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(12)
    void createSet_studentJwt_returns201() throws Exception {
        Assumptions.assumeTrue(sessionId != null);
        String body = """
            {
              "activityId": "00000000-0000-0000-0000-000000000300",
              "setIndex": 1,
              "restSeconds": 60,
              "notes": "first set",
              "clientUpdatedAt": "2026-04-24T12:05:00Z"
            }
            """;
        MvcResult res = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/sets")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        setId = json.get("id").asText();
    }

    @Test
    @Order(13)
    void patchSet_studentJwt_returns200() throws Exception {
        Assumptions.assumeTrue(sessionId != null && setId != null);
        String body = """
            {
              "notes": "patched note",
              "restSeconds": 45,
              "clientUpdatedAt": "2026-04-24T12:06:00Z"
            }
            """;
        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/sets/" + setId)
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(setId))
            .andExpect(jsonPath("$.notes").value("patched note"));
    }
}
