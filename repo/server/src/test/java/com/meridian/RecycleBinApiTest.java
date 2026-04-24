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
 * B-18 / B-19 / B-20 (§A.7) — RecycleBinController coverage.
 *
 * Destructive endpoint surface — every DELETE path must be explicitly authz-checked.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecycleBinApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void getRetentionPolicy_adminJwt_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/recycle-bin/policy")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").isNumber());
    }

    @Test
    @Order(2)
    void getRetentionPolicy_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/recycle-bin/policy")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void listRecycleBin_defaultType_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/recycle-bin")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(4)
    void listRecycleBin_usersType_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/recycle-bin?type=users")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(5)
    void listRecycleBin_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/recycle-bin"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void restore_unknownId_returns404() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/recycle-bin/courses/00000000-0000-0000-0000-ffffffffffff/restore")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void restore_unknownType_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/recycle-bin/widgets/00000000-0000-0000-0000-000000000001/restore")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void restore_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/recycle-bin/courses/00000000-0000-0000-0000-000000000001/restore")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void hardDelete_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(delete("/api/v1/admin/recycle-bin/courses/00000000-0000-0000-0000-000000000001")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    void hardDelete_anonymous_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/recycle-bin/courses/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    void hardDelete_unknownType_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(delete("/api/v1/admin/recycle-bin/widgets/00000000-0000-0000-0000-000000000001")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isBadRequest());
    }
}
