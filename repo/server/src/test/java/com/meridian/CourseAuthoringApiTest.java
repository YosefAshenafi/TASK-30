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
 * B-40 … B-45 (§A.7) — Course authoring coverage.
 * Covers PUT /courses/{id}, activity/knowledge-point/assessment-item create+update.
 *
 * Positive cases use the real-JWT admin token (M-1 remediation). Negative cases
 * verify STUDENT role is blocked by ClassificationPolicy#canModify.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CourseAuthoringApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private static String adminToken;
    private static String studentToken;
    private static String createdCourseId;
    private static String createdAssessmentItemId;

    @BeforeAll
    static void ctx() {
        // placeholder — tokens fetched lazily per test
    }

    @Test
    @Order(1)
    void loginAdminAndStudent() throws Exception {
        adminToken = TestAuthHelper.loginAdmin(mockMvc);
        studentToken = TestAuthHelper.loginStudent1(mockMvc);
    }

    @Test
    @Order(2)
    void createCourse_adminJwt_returns201() throws Exception {
        Assumptions.assumeTrue(adminToken != null);
        String code = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String body = """
            {
              "code": "%s",
              "title": "Authoring Test Course",
              "version": "2026.1",
              "classification": "INTERNAL"
            }
            """.formatted(code);
        MvcResult res = mockMvc.perform(post("/api/v1/courses")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(code))
            .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        createdCourseId = json.get("id").asText();
    }

    @Test
    @Order(3)
    void createCourse_studentJwt_returns403() throws Exception {
        Assumptions.assumeTrue(studentToken != null);
        String body = """
            {
              "code": "UNAUTH-001",
              "title": "Should not be created",
              "version": "2026.1",
              "classification": "INTERNAL"
            }
            """;
        mockMvc.perform(post("/api/v1/courses")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void updateCourse_adminJwt_reflectsNewTitle() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        String body = """
            {
              "code": "UPD-001",
              "title": "Updated Title",
              "version": "2026.2",
              "classification": "INTERNAL"
            }
            """;
        mockMvc.perform(put("/api/v1/courses/" + createdCourseId)
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @Order(5)
    void updateCourse_studentJwt_returns403() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        String body = """
            {
              "code": "HACK-001",
              "title": "unauthorised",
              "version": "1.0",
              "classification": "INTERNAL"
            }
            """;
        mockMvc.perform(put("/api/v1/courses/" + createdCourseId)
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void updateCourse_unknownId_returns404() throws Exception {
        Assumptions.assumeTrue(adminToken != null);
        String body = """
            {
              "code": "X",
              "title": "x",
              "version": "1.0",
              "classification": "INTERNAL"
            }
            """;
        mockMvc.perform(put("/api/v1/courses/00000000-0000-0000-0000-ffffffffffff")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void createActivity_adminJwt_returns201orPath() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        String body = """
            {
              "name": "Test Activity",
              "description": "hands-on practice",
              "sortOrder": 1
            }
            """;
        mockMvc.perform(post("/api/v1/courses/" + createdCourseId + "/activities")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s != 201 && s != 200) {
                    throw new AssertionError("Expected 201/200 for activity create, got " + s);
                }
            });
    }

    @Test
    @Order(8)
    void createActivity_studentJwt_returns403() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        String body = """
            {
              "name": "Unauthorised",
              "description": "nope",
              "sortOrder": 1
            }
            """;
        mockMvc.perform(post("/api/v1/courses/" + createdCourseId + "/activities")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void createKnowledgePoint_adminJwt_returns201orPath() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        String body = """
            {
              "name": "Test KP",
              "description": "kp description"
            }
            """;
        mockMvc.perform(post("/api/v1/courses/" + createdCourseId + "/knowledge-points")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s != 201 && s != 200) {
                    throw new AssertionError("Expected 201/200 for KP create, got " + s);
                }
            });
    }

    @Test
    @Order(10)
    void createKnowledgePoint_studentJwt_returns403() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        mockMvc.perform(post("/api/v1/courses/" + createdCourseId + "/knowledge-points")
                .header(HttpHeaders.AUTHORIZATION, studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"nope\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void listCohorts_returns200() throws Exception {
        // seeded course with cohorts
        mockMvc.perform(get("/api/v1/courses/00000000-0000-0000-0000-000000000200/cohorts")
                .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(12)
    void listAssessmentItems_returnsPage() throws Exception {
        mockMvc.perform(get("/api/v1/courses/00000000-0000-0000-0000-000000000200/assessment-items")
                .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(13)
    void createAssessmentItem_adminJwt_returns201() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        String body = """
            {
              "courseId": "%s",
              "type": "SINGLE",
              "stem": "What is 2 + 2?",
              "choices": ["3","4","5"]
            }
            """.formatted(createdCourseId);
        MvcResult res = mockMvc.perform(post("/api/v1/assessment-items")
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.courseId").value(createdCourseId))
            .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        createdAssessmentItemId = json.get("id").asText();
    }

    @Test
    @Order(14)
    void updateAssessmentItem_adminJwt_returns200() throws Exception {
        Assumptions.assumeTrue(createdAssessmentItemId != null);
        String body = """
            {
              "courseId": "%s",
              "type": "SINGLE",
              "stem": "Updated stem",
              "choices": ["A","B","C"]
            }
            """.formatted(createdCourseId);
        mockMvc.perform(put("/api/v1/assessment-items/" + createdAssessmentItemId)
                .header(HttpHeaders.AUTHORIZATION, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(createdAssessmentItemId))
            .andExpect(jsonPath("$.stem").value("Updated stem"));
    }

    @Test
    @Order(15)
    void listCourses_returnsPage() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(16)
    void deleteCourse_adminJwt_returns204() throws Exception {
        Assumptions.assumeTrue(createdCourseId != null);
        mockMvc.perform(delete("/api/v1/courses/" + createdCourseId)
                .header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    @Order(17)
    void deleteCourse_studentJwt_returns403() throws Exception {
        // attempt to delete the seeded CPR-101 course; student must be blocked
        mockMvc.perform(delete("/api/v1/courses/00000000-0000-0000-0000-000000000200")
                .header(HttpHeaders.AUTHORIZATION, studentToken))
            .andExpect(status().isForbidden());
    }
}
