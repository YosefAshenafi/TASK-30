package com.meridian;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Object-level classification tests — verifies that a user who cannot view
 * a CONFIDENTIAL or RESTRICTED course is also denied access to that course's
 * child resources (cohorts, assessment items, activities, knowledge points).
 *
 * Fixture: {@code classification-test-setup.sql} seeds one PUBLIC and one
 * CONFIDENTIAL course with child resources under both.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "classpath:classification-test-setup.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ClassificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String PUBLIC_COURSE = "00000000-0000-0000-0000-00000000cc10";
    private static final String CONFIDENTIAL_COURSE = "00000000-0000-0000-0000-00000000cc11";

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void student_canReadPublicCourseCohorts() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + PUBLIC_COURSE + "/cohorts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void student_cannotReadConfidentialCourseCohorts() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/cohorts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void student_cannotReadConfidentialCourseItems() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/assessment-items"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void student_cannotReadConfidentialCourseActivities() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/activities"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000002", roles = "STUDENT")
    void student_cannotReadConfidentialCourseKnowledgePoints() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/knowledge-points"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000050", roles = "CORPORATE_MENTOR")
    void corporateMentor_cannotReadConfidentialCourseItems() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/assessment-items"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000060", roles = "FACULTY_MENTOR")
    void facultyMentor_canReadConfidentialCourseItems() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/assessment-items"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000070", roles = "ADMIN")
    void admin_canReadConfidentialCourseItems() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + CONFIDENTIAL_COURSE + "/assessment-items"))
                .andExpect(status().isOk());
    }

    // M-1 remediation: real-JWT canonical case exercises the full auth filter chain
    // (JwtAuthenticationFilter, device-fingerprint check, rate-limit filter) on a
    // classification-gated read — mocked-principal variants above are kept for
    // rapid role permutation coverage only.
    @Test
    void student1RealJwt_canReadPublicCourseCohorts() throws Exception {
        String studentBearer = com.meridian.support.TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/courses/" + PUBLIC_COURSE + "/cohorts")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, studentBearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
