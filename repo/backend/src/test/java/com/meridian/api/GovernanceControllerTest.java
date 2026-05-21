package com.meridian.api;

import com.meridian.dto.PermissionUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GovernanceControllerTest extends TestContainersBase {

    // Seeded admin user id from V2 migration
    private static final String ADMIN_USER_ID = "33333333-0000-0000-0000-000000000001";
    // Seeded student user id
    private static final String STUDENT_USER_ID = "33333333-0000-0000-0000-000000000004";

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: GET /governance/users/{id}/permissions as self → 200
    @Test
    void getPermissions_asSelf_returns200() throws Exception {
        mockMvc.perform(get("/api/governance/users/" + STUDENT_USER_ID + "/permissions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    // Test 2: GET /governance/users/{id}/permissions as different non-admin user → 403
    @Test
    void getPermissions_asOtherNonAdmin_returns403() throws Exception {
        // student1 tries to read admin's permissions
        mockMvc.perform(get("/api/governance/users/" + ADMIN_USER_ID + "/permissions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 3: GET /governance/users/{id}/permissions as ADMIN → 200
    @Test
    void getPermissions_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/governance/users/" + STUDENT_USER_ID + "/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 4: PUT /governance/users/{id}/permissions as ADMIN → 200
    @Test
    void updatePermission_asAdmin_returns200() throws Exception {
        PermissionUpdateRequest request = new PermissionUpdateRequest("employeeId", "INTERNAL");

        mockMvc.perform(put("/api/governance/users/" + STUDENT_USER_ID + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // Test 5: PUT /governance/users/{id}/permissions as non-admin → 403
    @Test
    void updatePermission_asNonAdmin_returns403() throws Exception {
        PermissionUpdateRequest request = new PermissionUpdateRequest("employeeId", "INTERNAL");

        mockMvc.perform(put("/api/governance/users/" + STUDENT_USER_ID + "/permissions")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // Test 6: GET /audit/events as ADMIN → 200
    @Test
    void getAuditEvents_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 7: GET /audit/events as STUDENT → 403
    @Test
    void getAuditEvents_asStudent_returns403() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // Test 8: GET /admin/anomalies as ADMIN → 200
    @Test
    void getAnomalies_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/anomalies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // Test 9: GET /admin/anomalies as non-admin → 403
    @Test
    void getAnomalies_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/anomalies")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
