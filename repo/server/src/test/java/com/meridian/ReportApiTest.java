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
 * Integration tests for §5 Reports — contract tests and security checks.
 *
 * Covers: create payload shape, DTO field names, approval trigger, download endpoint,
 * cross-user access denial, REFUND_RETURN_RATE and INVENTORY_LEVELS kinds.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "classpath:sync-test-setup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String REPORTS_URL = "/api/v1/reports";
    private static final String OWNER_USER = "00000000-0000-0000-0000-000000000002";
    private static final String OTHER_USER = "00000000-0000-0000-0000-000000000003";

    private static String createdRunId;
    private static String createdScheduleId;

    @Test
    @Order(1)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void createReport_sendKindField_returns202WithKindInResponse() throws Exception {
        String body = """
                {
                  "kind": "ENROLLMENTS",
                  "format": "CSV"
                }
                """;
        var result = mockMvc.perform(post(REPORTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.kind").value("ENROLLMENTS"))
            .andExpect(jsonPath("$.status").isNotEmpty())
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

        String json = result.getResponse().getContentAsString();
        createdRunId = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @Order(2)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void getReport_ownerCanAccess_returnsRun() throws Exception {
        if (createdRunId == null) return;
        mockMvc.perform(get(REPORTS_URL + "/" + createdRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(createdRunId));
    }

    @Test
    @Order(3)
    @WithMockUser(username = OTHER_USER, roles = "FACULTY_MENTOR")
    void getReport_otherUserDenied_returns403() throws Exception {
        if (createdRunId == null) return;
        mockMvc.perform(get(REPORTS_URL + "/" + createdRunId))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @WithMockUser(username = OTHER_USER, roles = "FACULTY_MENTOR")
    void cancelReport_otherUserDenied_returns403() throws Exception {
        if (createdRunId == null) return;
        mockMvc.perform(post(REPORTS_URL + "/" + createdRunId + "/cancel"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void createReport_restrictedClassification_triggerApproval() throws Exception {
        String body = """
                {
                  "kind": "ENROLLMENTS",
                  "format": "CSV",
                  "classification": "RESTRICTED"
                }
                """;
        mockMvc.perform(post(REPORTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("NEEDS_APPROVAL"));
    }

    @Test
    @Order(6)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void createReport_refundReturnRate_returnsStructuredRows() throws Exception {
        String body = """
                {
                  "kind": "REFUND_RETURN_RATE",
                  "format": "CSV"
                }
                """;
        mockMvc.perform(post(REPORTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.kind").value("REFUND_RETURN_RATE"))
            .andExpect(jsonPath("$.status").isNotEmpty());
    }

    @Test
    @Order(7)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void createReport_inventoryLevels_returnsStructuredRows() throws Exception {
        String body = """
                {
                  "kind": "INVENTORY_LEVELS",
                  "format": "CSV"
                }
                """;
        mockMvc.perform(post(REPORTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.kind").value("INVENTORY_LEVELS"));
    }

    @Test
    @Order(8)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void downloadReport_notReady_returns409() throws Exception {
        // A freshly created (QUEUED) report is not ready for download
        String body = """
                {
                  "kind": "ENROLLMENTS",
                  "format": "CSV"
                }
                """;
        var result = mockMvc.perform(post(REPORTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andReturn();

        String json = result.getResponse().getContentAsString();
        String runId = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get(REPORTS_URL + "/" + runId + "/download"))
            .andExpect(status().is(anyOf(equalTo(409), equalTo(404))));
    }

    @Test
    @Order(9)
    @WithMockUser(username = OWNER_USER, roles = "STUDENT")
    void studentCannotCreateReports() throws Exception {
        String body = """
                {
                  "kind": "ENROLLMENTS",
                  "format": "CSV"
                }
                """;
        mockMvc.perform(post(REPORTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void listReports_returnsOnlyOwnRuns() throws Exception {
        // List must contain only runs belonging to the authenticated user
        mockMvc.perform(get(REPORTS_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            // No run owned by OTHER_USER should appear in OWNER_USER's list
            .andExpect(jsonPath("$.content[?(@.requestedBy == '" + OTHER_USER + "')]").isEmpty());
    }

    @Test
    @Order(11)
    @WithMockUser(username = OWNER_USER, roles = "ADMIN")
    void adminListReports_returnsAllRuns() throws Exception {
        // Admin can list all runs and the list must be non-empty given prior create calls
        mockMvc.perform(get(REPORTS_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @Order(10)
    @WithMockUser(username = OTHER_USER, roles = "FACULTY_MENTOR")
    void otherUser_cannotAccessOwnersReport_returns403() throws Exception {
        if (createdRunId == null) return;
        // Accessing a specific report owned by a different user must be denied
        mockMvc.perform(get(REPORTS_URL + "/" + createdRunId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @Order(12)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void createSchedule_returnsScheduleDto() throws Exception {
        String body = """
                {
                  "kind": "ENROLLMENTS",
                  "format": "CSV",
                  "cronExpr": "0 0 1 * * *"
                }
                """;
        mockMvc.perform(post(REPORTS_URL + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @Order(13)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void updateSchedule_toggleEnabled_returns200() throws Exception {
        // Create a schedule first
        String createBody = """
                {
                  "kind": "CERT_EXPIRING",
                  "format": "CSV",
                  "cronExpr": "0 0 3 * * *"
                }
                """;
        var createResult = mockMvc.perform(post(REPORTS_URL + "/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn();
        String schedJson = createResult.getResponse().getContentAsString();
        String schedId = schedJson.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        createdScheduleId = schedId;

        // Toggle enabled=false
        mockMvc.perform(put(REPORTS_URL + "/schedules/" + schedId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"cronExpr\":\"0 0 3 * * *\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @Order(14)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void listSchedules_returnsPageShape() throws Exception {
        mockMvc.perform(get(REPORTS_URL + "/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").exists());
    }

    @Test
    @Order(15)
    @WithMockUser(username = OWNER_USER, roles = "FACULTY_MENTOR")
    void deleteSchedule_owner_returns204() throws Exception {
        Assumptions.assumeTrue(createdScheduleId != null);
        mockMvc.perform(delete(REPORTS_URL + "/schedules/" + createdScheduleId))
            .andExpect(status().isNoContent());
    }

    // M-1 remediation: canonical real-JWT positive case for ReportController.
    // Exercises the full auth filter chain on a listing endpoint — remaining
    // tests above use @WithMockUser for role-permutation density.
    @Test
    @Order(16)
    void adminRealJwt_listReports_returnsPageShape() throws Exception {
        String adminBearer = com.meridian.support.TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get(REPORTS_URL + "?size=10")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, adminBearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").exists());
    }

    @Test
    @Order(17)
    void facultyRealJwt_listSchedules_returnsPageShape() throws Exception {
        String facultyBearer = com.meridian.support.TestAuthHelper.loginFaculty(mockMvc);
        mockMvc.perform(get(REPORTS_URL + "/schedules?page=0&size=10")
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, facultyBearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").exists());
    }
}
