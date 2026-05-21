package com.meridian.api;

import com.meridian.dto.TriggerBackupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackupControllerTest extends TestContainersBase {

    private static final String TRIGGER_URL = "/api/admin/backup/trigger";
    private static final String HISTORY_URL = "/api/admin/backup/history";

    private String adminToken;
    private String facultyToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        facultyToken = loginAs("faculty1", "Faculty@12345678");
    }

    // Test 1: POST /api/admin/backup/trigger as ADMIN with valid type → authorized (not 401/403)
    // Note: pg_dump is not available in the test container, so the backup process itself returns 400.
    // This test verifies that the admin role is correctly authorized to reach the endpoint.
    @Test
    void triggerBackup_asAdmin_validType_isAuthorized() throws Exception {
        TriggerBackupRequest request = new TriggerBackupRequest("FULL");

        MvcResult result = mockMvc.perform(post(TRIGGER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).as("Admin should be authorized (not 401 or 403)").isNotIn(401, 403);
    }

    // Test 2: POST /api/admin/backup/trigger as FACULTY_MENTOR → 403
    @Test
    void triggerBackup_asFacultyMentor_returns403() throws Exception {
        TriggerBackupRequest request = new TriggerBackupRequest("FULL");

        mockMvc.perform(post(TRIGGER_URL)
                        .header("Authorization", "Bearer " + facultyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test 3: POST /api/admin/backup/trigger unauthenticated → 401
    @Test
    void triggerBackup_unauthenticated_returns401() throws Exception {
        TriggerBackupRequest request = new TriggerBackupRequest("FULL");

        mockMvc.perform(post(TRIGGER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // Test 4: POST /api/admin/backup/trigger with blank type → 400 (bean validation)
    @Test
    void triggerBackup_blankType_returns400() throws Exception {
        mockMvc.perform(post(TRIGGER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // Test 5: GET /api/admin/backup/history as ADMIN → 200 with paginated structure
    @Test
    void getHistory_asAdmin_returns200WithPageStructure() throws Exception {
        mockMvc.perform(get(HISTORY_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // Test 6: GET /api/admin/backup/history as FACULTY_MENTOR → 403
    @Test
    void getHistory_asFacultyMentor_returns403() throws Exception {
        mockMvc.perform(get(HISTORY_URL)
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }

    // Test 7: GET /api/admin/backup/history unauthenticated → 401
    @Test
    void getHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(HISTORY_URL))
                .andExpect(status().isUnauthorized());
    }
}
