package com.meridian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sync-test-setup.sql",
        "classpath:classification-test-setup.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class NoMockAuthCoverageApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void usersMe_withRealToken_returnsProfile() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("student1"));
    }

    @Test
    void adminAudit_withRealToken_returnsPageShape() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/audit")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").exists());
    }

    @Test
    void sessionsPatch_withRealToken_updatesStatus() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        String sessionId = UUID.randomUUID().toString();

        String createBody = """
            {
              "id":"%s",
              "courseId":"00000000-0000-0000-0000-000000000c10",
              "cohortId":null,
              "restSecondsDefault":60,
              "startedAt":"2026-04-20T09:00:00Z",
              "clientUpdatedAt":"2026-04-20T09:00:00Z"
            }
            """.formatted(sessionId);

        mockMvc.perform(post("/api/v1/sessions")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(sessionId));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId)
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status":"PAUSED",
                      "clientUpdatedAt":"2026-04-20T09:05:00Z"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void sessionsSync_withRealToken_returnsSyncEnvelope() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/sessions/sync")
                .header(HttpHeaders.AUTHORIZATION, student)
                .header("Idempotency-Key", "no-mock-sync-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessions":[],
                      "sets":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").isArray())
            .andExpect(jsonPath("$.conflicts").isArray());
    }

    @Test
    void analyticsEndpoints_withRealMentorToken_returnStructuredEnvelope() throws Exception {
        String mentor = TestAuthHelper.loginMentor(mockMvc);
        String learnerId = "00000000-0000-0000-0000-000000000101";

        // Contract: mentor JWT must be accepted AND the response must be 200 OK
        // with a JSON envelope of the declared shape. No tolerance for 5xx — a
        // 500 here indicates either an auth/mapping regression or a SQL bug and
        // must fail the build. The permissive "anything but 401/403" logic this
        // test used to contain masked exactly that class of regression.

        mockMvc.perform(get("/api/v1/analytics/mastery-trends?learnerId=" + learnerId)
                .header(HttpHeaders.AUTHORIZATION, mentor))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.scope").value("LEARNER"))
            .andExpect(jsonPath("$.points").isArray());

        mockMvc.perform(get("/api/v1/analytics/weak-knowledge-points?learnerId=" + learnerId)
                .header(HttpHeaders.AUTHORIZATION, mentor))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/api/v1/analytics/item-stats?learnerId=" + learnerId)
                .header(HttpHeaders.AUTHORIZATION, mentor))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items").isArray());

        // wrong-answers is in the same role bucket as mastery-trends; assert the
        // same deterministic 200+envelope contract.
        mockMvc.perform(get("/api/v1/analytics/wrong-answers?learnerId=" + learnerId)
                .header(HttpHeaders.AUTHORIZATION, mentor))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void analyticsItemStats_withStudentJwt_returns403Deterministic() throws Exception {
        // item-stats is explicitly role-gated — no STUDENT. This must always be
        // 403 (deterministic contract assertion) so regressions that open the
        // endpoint up are caught immediately.
        String student = TestAuthHelper.loginStudent1(mockMvc);
        String learnerId = "00000000-0000-0000-0000-000000000101";

        mockMvc.perform(get("/api/v1/analytics/item-stats?learnerId=" + learnerId)
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void analyticsMasteryTrends_anonymous_returns401Deterministic() throws Exception {
        // No Authorization header → 401. Pins the auth contract.
        mockMvc.perform(get("/api/v1/analytics/mastery-trends"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsEndpoints_withRealFacultyToken_coverLifecycle() throws Exception {
        String faculty = TestAuthHelper.loginFaculty(mockMvc);

        MvcResult createRunResult = mockMvc.perform(post("/api/v1/reports")
                .header(HttpHeaders.AUTHORIZATION, faculty)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "kind":"ENROLLMENTS",
                      "format":"CSV"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();

        String runId = readId(createRunResult);

        mockMvc.perform(get("/api/v1/reports/" + runId)
                .header(HttpHeaders.AUTHORIZATION, faculty))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId));

        mockMvc.perform(get("/api/v1/reports/" + runId + "/download")
                .header(HttpHeaders.AUTHORIZATION, faculty))
            .andExpect(status().is(anyOf(equalTo(409), equalTo(404))));

        mockMvc.perform(post("/api/v1/reports/" + runId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, faculty))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reports")
                .header(HttpHeaders.AUTHORIZATION, faculty))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        MvcResult createScheduleResult = mockMvc.perform(post("/api/v1/reports/schedules")
                .header(HttpHeaders.AUTHORIZATION, faculty)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "kind":"ENROLLMENTS",
                      "format":"CSV",
                      "cronExpr":"0 0 2 * * *"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andReturn();

        String scheduleId = readId(createScheduleResult);

        mockMvc.perform(put("/api/v1/reports/schedules/" + scheduleId)
                .header(HttpHeaders.AUTHORIZATION, faculty)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled":false,
                      "cronExpr":"0 0 3 * * *"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/v1/reports/schedules")
                .header(HttpHeaders.AUTHORIZATION, faculty))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(delete("/api/v1/reports/schedules/" + scheduleId)
                .header(HttpHeaders.AUTHORIZATION, faculty))
            .andExpect(status().isNoContent());
    }

    @Test
    void courseChildReadEndpoints_withRealStudentToken_returnOk() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        String publicCourse = "00000000-0000-0000-0000-00000000cc10";

        mockMvc.perform(get("/api/v1/courses/" + publicCourse + "/activities")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/courses/" + publicCourse + "/knowledge-points")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    private static String readId(MvcResult result) throws Exception {
        JsonNode json = MAPPER.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }
}
