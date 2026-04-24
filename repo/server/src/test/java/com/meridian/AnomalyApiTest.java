package com.meridian;

import com.meridian.support.TestAuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-29 (§A.7) — AnomalyController coverage. Security-audit surface; admin-only.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnomalyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void listAnomalies_adminJwt_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/anomalies").header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(2)
    void listAnomalies_withResolvedFilter_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/anomalies?resolved=true")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(3)
    void listAnomalies_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/anomalies").header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void listAnomalies_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/anomalies"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void resolveAnomaly_unknownId_returns404() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/anomalies/00000000-0000-0000-0000-ffffffffffff/resolve")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    void resolveAnomaly_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/anomalies/00000000-0000-0000-0000-000000000aa1/resolve")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void resolveAnomaly_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/anomalies/00000000-0000-0000-0000-000000000aa1/resolve"))
            .andExpect(status().isUnauthorized());
    }
}
