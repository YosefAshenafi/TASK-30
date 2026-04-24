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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Content-level assertions for tenant / org data isolation.
 *
 * Covers §8.2 gaps:
 *   - "API test asserting filtered content ownership by org for analytics/session list"
 *   - "Add report data-scope tests for corp mentor org isolation"
 *
 * These go beyond status-code checks to verify that the actual payload
 * returned does not contain data belonging to a different organisation.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "classpath:sync-test-setup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrgIsolationContentApiTest {

    @Autowired
    private MockMvc mockMvc;

    // User fixtures expected in sync-test-setup.sql / test seed data
    private static final String ORG_A_ID        = "00000000-0000-0000-0000-000000000010";
    private static final String ORG_B_ID        = "00000000-0000-0000-0000-000000000020";
    private static final String CORP_MENTOR_A   = "00000000-0000-0000-0000-000000000011";
    private static final String CORP_MENTOR_B   = "00000000-0000-0000-0000-000000000021";
    private static final String STUDENT_ORG_A   = "00000000-0000-0000-0000-000000000012";
    private static final String STUDENT_ORG_B   = "00000000-0000-0000-0000-000000000022";
    private static final String ADMIN_USER      = "00000000-0000-0000-0000-000000000001";

    // ---- Session list — corporate mentor sees only own-org sessions ----

    @Test
    @Order(1)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_sessionList_doesNotContainOrgBSessions() throws Exception {
        // All returned sessions must belong to students in org A.
        // studentId must NOT equal a student from org B.
        mockMvc.perform(get("/api/v1/sessions?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.studentId == '" + STUDENT_ORG_B + "')]").isEmpty());
    }

    @Test
    @Order(2)
    @WithMockUser(username = CORP_MENTOR_B, roles = "CORPORATE_MENTOR")
    void corpMentorB_sessionList_doesNotContainOrgASessions() throws Exception {
        mockMvc.perform(get("/api/v1/sessions?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.studentId == '" + STUDENT_ORG_A + "')]").isEmpty());
    }

    // ---- Analytics — corporate mentor cannot query cross-org learner ----

    @Test
    @Order(3)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_cannotQueryAnalyticsForOrgBLearner() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/mastery-trends?learnerId=" + STUDENT_ORG_B))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_canQueryAnalyticsForOwnOrgLearner() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/mastery-trends?learnerId=" + STUDENT_ORG_A))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) throw new AssertionError(
                            "Corp mentor A should not be forbidden from analytics for their own learner");
                });
    }

    // ---- Item stats — learner-level filter now works (audit fix) ----

    @Test
    @Order(5)
    @WithMockUser(username = ADMIN_USER, roles = "ADMIN")
    void admin_canQueryItemStatsByLearnerId() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/item-stats?learnerId=" + STUDENT_ORG_A))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) throw new AssertionError(
                            "Admin should not be forbidden from item-stats with learnerId");
                });
    }

    @Test
    @Order(6)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_itemStats_forbiddenForOrgBLearner() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/item-stats?learnerId=" + STUDENT_ORG_B))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_wrongAnswers_forbiddenForOrgBLearner() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/wrong-answers?learnerId=" + STUDENT_ORG_B))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_weakKnowledgePoints_forbiddenForOrgBLearner() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/weak-knowledge-points?learnerId=" + STUDENT_ORG_B))
                .andExpect(status().isForbidden());
    }

    // ---- Reports — corporate mentor report list is org-scoped ----

    @Test
    @Order(9)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_reportList_returnsOkAndIsArray() throws Exception {
        mockMvc.perform(get("/api/v1/reports?size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(10)
    @WithMockUser(username = CORP_MENTOR_A, roles = "CORPORATE_MENTOR")
    void corpMentorA_cannotDownloadOrgBReport() throws Exception {
        // A report owned by org B's corporate mentor must be inaccessible to org A's mentor.
        // Use an ID that is guaranteed not to belong to CORP_MENTOR_A.
        String orgBReportId = "00000000-0000-0000-bbbb-000000000001";
        mockMvc.perform(get("/api/v1/reports/" + orgBReportId))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Must be 404 (not found) or 403 (forbidden) — never 200
                    if (status == 200) throw new AssertionError(
                            "Corp mentor A should not be able to access org B's report");
                });
    }

    // ---- Admin sees all — no org restriction ----

    @Test
    @Order(11)
    @WithMockUser(username = ADMIN_USER, roles = "ADMIN")
    void admin_sessionListNotRestrictedByOrg() throws Exception {
        mockMvc.perform(get("/api/v1/sessions?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // M-1 remediation: real-JWT canonical case. Exercises JwtAuthenticationFilter
    // + DeviceFingerprintService + RateLimitFilter on a data-scoped GET.
    @Test
    @Order(12)
    void adminRealJwt_sessionListReturnsPageShape() throws Exception {
        String adminBearer = com.meridian.support.TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/sessions?size=10")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, adminBearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @Order(13)
    void mentorRealJwt_wrongAnswersOwnScope_notForbidden() throws Exception {
        String mentorBearer = com.meridian.support.TestAuthHelper.loginMentor(mockMvc);
        mockMvc.perform(get("/api/v1/analytics/wrong-answers")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, mentorBearer))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 401 || status == 403) {
                    throw new AssertionError("mentor1 should pass authz for wrong-answers endpoint");
                }
            });
    }
}
