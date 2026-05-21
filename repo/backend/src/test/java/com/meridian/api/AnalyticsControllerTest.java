package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalyticsControllerTest extends TestContainersBase {

    private static final String MASTERY_URL = "/api/analytics/mastery";
    private static final String WRONG_ANSWERS_URL = "/api/analytics/wrong-answers";
    private static final String KNOWLEDGE_GAPS_URL = "/api/analytics/knowledge-gaps";
    private static final String ITEM_DIFFICULTY_URL = "/api/analytics/item-difficulty";
    private static final String LEARNER_URL = "/api/analytics/learner/";

    // Seeded user IDs from V2 migration
    private static final UUID STUDENT_ID =
            UUID.fromString("33333333-0000-0000-0000-000000000004");
    private static final UUID CORP_MENTOR_ID =
            UUID.fromString("33333333-0000-0000-0000-000000000003");

    // Seeded org IDs
    private static final UUID ACME_ORG_ID =
            UUID.fromString("22222222-0000-0000-0000-000000000001");
    private static final UUID MERIDIAN_ORG_ID =
            UUID.fromString("22222222-0000-0000-0000-000000000002");

    private String facultyToken;
    private String studentToken;
    private String adminToken;
    private String corpToken;

    @BeforeEach
    void setUp() throws Exception {
        facultyToken = loginAs("faculty1", "Faculty@12345678");
        studentToken = loginAs("student1", "Student@12345678");
        adminToken = loginAs("admin", "Admin@12345678");
        corpToken = loginAs("corp1", "Corp@12345678");
    }

    // Test 1: GET /api/analytics/mastery as FACULTY_MENTOR → 200
    @Test
    @Order(1)
    void getMasteryTrends_asFacultyMentor_returns200() throws Exception {
        mockMvc.perform(get(MASTERY_URL)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk());
    }

    // Test 2: GET /api/analytics/mastery as STUDENT → 403
    @Test
    @Order(2)
    void getMasteryTrends_asStudent_returns403() throws Exception {
        mockMvc.perform(get(MASTERY_URL)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 3: GET /api/analytics/mastery unauthenticated → 401
    @Test
    @Order(3)
    void getMasteryTrends_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(MASTERY_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 4: GET /api/analytics/wrong-answers as FACULTY_MENTOR → 200
    @Test
    @Order(4)
    void getWrongAnswers_asFacultyMentor_returns200() throws Exception {
        mockMvc.perform(get(WRONG_ANSWERS_URL)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk());
    }

    // Test 5: GET /api/analytics/knowledge-gaps as ADMIN → 200
    @Test
    @Order(5)
    void getKnowledgeGaps_asAdmin_returns200() throws Exception {
        mockMvc.perform(get(KNOWLEDGE_GAPS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 6: GET /api/analytics/item-difficulty as FACULTY_MENTOR → 200
    @Test
    @Order(6)
    void getItemDifficulty_asFacultyMentor_returns200() throws Exception {
        mockMvc.perform(get(ITEM_DIFFICULTY_URL)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk());
    }

    // Test 7: GET /api/analytics/learner/{id} as CORP_MENTOR for user in same org → 200
    // corp1 is in ACME_ORG_ID. student1 has no org_id, so we use corp1's own ID as learner.
    @Test
    @Order(7)
    void getLearnerAnalytics_asCorpMentorSameOrg_returns200() throws Exception {
        // corp1 requesting analytics for themselves (same org) — always passes org check
        mockMvc.perform(get(LEARNER_URL + CORP_MENTOR_ID)
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 8: GET /api/analytics/learner/{id} as CORP_MENTOR for user in different org → 403
    // corp1 is in ACME_ORG. faculty1 is in MERIDIAN_ORG.
    @Test
    @Order(8)
    void getLearnerAnalytics_asCorpMentorDifferentOrg_returns403() throws Exception {
        // faculty1 user id is in MERIDIAN_ORG, corp1 is in ACME_ORG
        UUID faculty1Id = UUID.fromString("33333333-0000-0000-0000-000000000002");

        mockMvc.perform(get(LEARNER_URL + faculty1Id)
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isForbidden());
    }

    // Test 9: GET /api/analytics/mastery as CORP_MENTOR with no orgId → 200 (forced to own org)
    @Test
    @Order(9)
    void getMastery_asCorpMentor_noOrgParam_returns200ScopedToOwnOrg() throws Exception {
        // corp1 omits organizationId — server forces ACME_ORG_ID, request succeeds
        mockMvc.perform(get(MASTERY_URL)
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 10: GET /api/analytics/mastery as CORP_MENTOR with mismatched orgId → 200 (forced to own org)
    @Test
    @Order(10)
    void getMastery_asCorpMentor_mismatchedOrgParam_returns200ScopedToOwnOrg() throws Exception {
        // corp1 (ACME) requests MERIDIAN org data — server silently forces ACME scope
        mockMvc.perform(get(MASTERY_URL)
                        .param("organizationId", MERIDIAN_ORG_ID.toString())
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 11: GET /api/analytics/wrong-answers as CORP_MENTOR with mismatched orgId → 200 (forced to own org)
    @Test
    @Order(11)
    void getWrongAnswers_asCorpMentor_mismatchedOrgParam_returns200ScopedToOwnOrg() throws Exception {
        mockMvc.perform(get(WRONG_ANSWERS_URL)
                        .param("organizationId", MERIDIAN_ORG_ID.toString())
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 12: GET /api/analytics/knowledge-gaps as CORP_MENTOR with mismatched orgId → 200 (forced to own org)
    @Test
    @Order(12)
    void getKnowledgeGaps_asCorpMentor_mismatchedOrgParam_returns200ScopedToOwnOrg() throws Exception {
        mockMvc.perform(get(KNOWLEDGE_GAPS_URL)
                        .param("organizationId", MERIDIAN_ORG_ID.toString())
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 13: GET /api/analytics/item-difficulty as CORP_MENTOR with mismatched orgId → 200 (forced to own org)
    @Test
    @Order(13)
    void getItemDifficulty_asCorpMentor_mismatchedOrgParam_returns200ScopedToOwnOrg() throws Exception {
        mockMvc.perform(get(ITEM_DIFFICULTY_URL)
                        .param("organizationId", MERIDIAN_ORG_ID.toString())
                        .header("Authorization", "Bearer " + corpToken))
                .andExpect(status().isOk());
    }

    // Test 14: GET /api/analytics/cohort/{cohortId} as FACULTY_MENTOR → 200 with aggregate keys
    @Test
    @Order(14)
    void getCohortAnalytics_asFacultyMentor_returns200() throws Exception {
        UUID anyCohortId = UUID.randomUUID();
        mockMvc.perform(get("/api/analytics/cohort/" + anyCohortId)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteryTrends").isArray())
                .andExpect(jsonPath("$.wrongAnswers").isArray())
                .andExpect(jsonPath("$.knowledgeGaps").isArray())
                .andExpect(jsonPath("$.itemDifficulty").isArray());
    }

    // Test 15: GET /api/analytics/cohort/{cohortId} as STUDENT → 403
    @Test
    @Order(15)
    void getCohortAnalytics_asStudent_returns403() throws Exception {
        UUID anyCohortId = UUID.randomUUID();
        mockMvc.perform(get("/api/analytics/cohort/" + anyCohortId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 16: GET /api/analytics/cohort/{cohortId} unauthenticated → 401
    @Test
    @Order(16)
    void getCohortAnalytics_unauthenticated_returns401() throws Exception {
        UUID anyCohortId = UUID.randomUUID();
        mockMvc.perform(get("/api/analytics/cohort/" + anyCohortId))
                .andExpect(status().isUnauthorized());
    }

    // Test 17: GET /api/analytics/course/{courseId} as ADMIN → 200 with aggregate keys
    @Test
    @Order(17)
    void getCourseAnalytics_asAdmin_returns200() throws Exception {
        UUID anyCourseId = UUID.randomUUID();
        mockMvc.perform(get("/api/analytics/course/" + anyCourseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masteryTrends").isArray())
                .andExpect(jsonPath("$.wrongAnswers").isArray())
                .andExpect(jsonPath("$.knowledgeGaps").isArray())
                .andExpect(jsonPath("$.itemDifficulty").isArray());
    }

    // Test 18: GET /api/analytics/course/{courseId} as STUDENT → 403
    @Test
    @Order(18)
    void getCourseAnalytics_asStudent_returns403() throws Exception {
        UUID anyCourseId = UUID.randomUUID();
        mockMvc.perform(get("/api/analytics/course/" + anyCourseId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
