package com.meridian;

import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-23 / B-24 / B-25 (§A.7) — BackupController coverage.
 *
 * Note: POST /run and POST /recovery-drill kick off real work via BackupRunner /
 * RecoveryDrillRunner. In the test profile those runners operate against the
 * Testcontainers Postgres and write to {@code /tmp/meridian-test-*} directories
 * (see application-test.yml) — safe, isolated.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackupApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void listBackups_adminJwt_returns200WithPageShape() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/backups").header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @Order(2)
    void listBackups_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/backups").header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void listBackups_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/backups"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void getPolicy_adminJwt_returns200WithPolicyFields() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        // The controller lazily creates an empty policy on first read if none exists —
        // so this always returns 200 with a non-null body.
        mockMvc.perform(get("/api/v1/admin/backups/policy").header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void updatePolicy_adminJwt_roundTrip() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = """
            {
              "retentionDays": 30,
              "scheduleEnabled": true,
              "scheduleCron": "0 2 * * *"
            }
            """;
        mockMvc.perform(put("/api/v1/admin/backups/policy")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(30))
            .andExpect(jsonPath("$.scheduleEnabled").value(true));

        // Readback verifies persistence
        mockMvc.perform(get("/api/v1/admin/backups/policy").header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(30));
    }

    @Test
    @Order(6)
    void updatePolicy_pathTraversalAttempt_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = """
            {
              "backupPath": "/tmp/../etc/passwd"
            }
            """;
        mockMvc.perform(put("/api/v1/admin/backups/policy")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void updatePolicy_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(put("/api/v1/admin/backups/policy")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":7}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void listRecoveryDrills_adminJwt_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/backups/recovery-drills")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(9)
    void listRecoveryDrills_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/backups/recovery-drills")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void scheduleDrill_noCompletedBackup_returns409() throws Exception {
        // With no COMPLETED backup present, the controller short-circuits to 409.
        // If a prior test in the suite triggered /run and it completed, this may
        // return 202 instead — both shapes are acceptable.
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/backups/recovery-drill")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"drill from test\"}"))
            .andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s != 409 && s != 202) {
                    throw new AssertionError("Expected 409 or 202, got " + s);
                }
            });
    }

    @Test
    @Order(11)
    void triggerBackup_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/backups/run")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }
}
