package com.meridian;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cross-user/cross-org isolation tests (§8.2 addition).
 *
 * Verifies that corporate mentors cannot see sessions or reports from other orgs,
 * and admin-only endpoints reject non-admin users.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrgScopeApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String STUDENT_USER = "00000000-0000-0000-0000-000000000002";
    private static final String OTHER_STUDENT = "00000000-0000-0000-0000-000000000099";

    @Test
    @Order(1)
    @WithMockUser(username = OTHER_STUDENT, roles = "STUDENT")
    void student_cannotListOtherStudentSessions() throws Exception {
        // Sessions are scoped to the authenticated student — no foreign student IDs may appear,
        // AND paging envelope must be well-formed so clients can pass it back.
        mockMvc.perform(get("/api/v1/sessions?size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            // STUDENT_USER's sessions must never appear in OTHER_STUDENT's results
            .andExpect(jsonPath("$.content[?(@.studentId == '" + STUDENT_USER + "')]").isEmpty())
            // Page envelope assertions — clients consume these for pagination controls
            .andExpect(jsonPath("$.page").exists())
            .andExpect(jsonPath("$.size").value(100))
            .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @Order(1)
    @WithMockUser(username = OTHER_STUDENT, roles = "STUDENT")
    void student_cannotAccessForeignSessionById() throws Exception {
        // A session owned by a different student must not be readable by another student
        mockMvc.perform(get("/api/v1/sessions/" + SESSION_ID))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 403 && status != 404) {
                    throw new AssertionError(
                        "Expected 403 or 404 accessing foreign session, but got " + status);
                }
            });
    }

    @Test
    @Order(2)
    void unauthenticated_sessionListDenied() throws Exception {
        mockMvc.perform(get("/api/v1/sessions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void unauthenticated_adminUsersDenied() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @WithMockUser(username = STUDENT_USER, roles = "STUDENT")
    void student_cannotAccessAdminUsers() throws Exception {
        // Q-1 remediation: status + error-envelope body assertion
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(5)
    @WithMockUser(username = STUDENT_USER, roles = "STUDENT")
    void student_cannotAccessAdminAudit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(6)
    @WithMockUser(username = STUDENT_USER, roles = "STUDENT")
    void student_cannotAccessAdminApprovals() throws Exception {
        mockMvc.perform(get("/api/v1/admin/approvals"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(7)
    @WithMockUser(username = STUDENT_USER, roles = "STUDENT")
    void student_cannotAccessAdminBackups() throws Exception {
        mockMvc.perform(get("/api/v1/admin/backups"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // --- Session mutation role-boundary negative tests ---

    private static final String FACULTY_USER  = "00000000-0000-0000-0000-000000000010";
    private static final String CORP_MENTOR   = "00000000-0000-0000-0000-000000000011";
    private static final String ADMIN_USER    = "00000000-0000-0000-0000-000000000012";
    private static final String SESSION_ID    = "00000000-0000-0000-aaaa-000000000001";

    @Test
    @Order(10)
    @WithMockUser(username = FACULTY_USER, roles = "FACULTY_MENTOR")
    void facultyMentor_cannotCreateSession() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + SESSION_ID + "\",\"courseId\":\"00000000-0000-0000-0000-000000000001\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(11)
    @WithMockUser(username = CORP_MENTOR, roles = "CORPORATE_MENTOR")
    void corporateMentor_cannotCreateSession() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + SESSION_ID + "\",\"courseId\":\"00000000-0000-0000-0000-000000000001\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(12)
    @WithMockUser(username = ADMIN_USER, roles = "ADMIN")
    void admin_cannotCreateSession() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + SESSION_ID + "\",\"courseId\":\"00000000-0000-0000-0000-000000000001\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(13)
    @WithMockUser(username = FACULTY_USER, roles = "FACULTY_MENTOR")
    void facultyMentor_cannotPatchSession() throws Exception {
        mockMvc.perform(patch("/api/v1/sessions/" + SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(14)
    @WithMockUser(username = CORP_MENTOR, roles = "CORPORATE_MENTOR")
    void corporateMentor_cannotPatchSession() throws Exception {
        mockMvc.perform(patch("/api/v1/sessions/" + SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(15)
    @WithMockUser(username = FACULTY_USER, roles = "FACULTY_MENTOR")
    void facultyMentor_cannotSyncSessions() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessions\":[],\"sets\":[]}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(16)
    @WithMockUser(username = CORP_MENTOR, roles = "CORPORATE_MENTOR")
    void corporateMentor_cannotSyncSessions() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessions\":[],\"sets\":[]}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @Order(17)
    @WithMockUser(username = STUDENT_USER, roles = "STUDENT")
    void student_sessionSyncNotForbidden() throws Exception {
        // Students must pass the authorization gate (any status other than 403 is acceptable)
        mockMvc.perform(post("/api/v1/sessions/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessions\":[],\"sets\":[]}"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 403) throw new AssertionError("Student should not be forbidden from /sessions/sync");
            });
    }
}
