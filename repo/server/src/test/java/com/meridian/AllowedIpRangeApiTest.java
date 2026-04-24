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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B-26 / B-27 / B-28 (§A.7) — AllowedIpRangeController coverage.
 * Security-critical governance surface — admins only.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AllowedIpRangeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    private static String createdRangeId;

    @Test
    @Order(1)
    void listAllowedIpRanges_adminJwt_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/allowed-ip-ranges")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(2)
    void listAllowedIpRanges_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/allowed-ip-ranges")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void listAllowedIpRanges_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/allowed-ip-ranges"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void createAllowedIpRange_adminJwt_returns201() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = """
            {
              "cidr": "192.0.2.0/24",
              "roleScope": "CORPORATE_MENTOR",
              "note": "test subnet"
            }
            """;
        MvcResult res = mockMvc.perform(post("/api/v1/admin/allowed-ip-ranges")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cidr").value("192.0.2.0/24"))
            .andExpect(jsonPath("$.roleScope").value("CORPORATE_MENTOR"))
            .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        createdRangeId = json.get("id").asText();
    }

    @Test
    @Order(5)
    void createAllowedIpRange_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/admin/allowed-ip-ranges")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cidr\":\"10.0.0.0/8\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void createAllowedIpRange_emptyCidr_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(post("/api/v1/admin/allowed-ip-ranges")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cidr\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void listAllowedIpRanges_afterCreate_containsEntry() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/allowed-ip-ranges")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.cidr == '192.0.2.0/24')]").exists());
    }

    @Test
    @Order(8)
    void deleteAllowedIpRange_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(delete("/api/v1/admin/allowed-ip-ranges/00000000-0000-0000-0000-000000000001")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void deleteAllowedIpRange_adminJwt_returns204ThenSecondReturns404() throws Exception {
        Assumptions.assumeTrue(createdRangeId != null, "createAllowedIpRange_adminJwt must have run first");
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(delete("/api/v1/admin/allowed-ip-ranges/" + createdRangeId)
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isNoContent());

        // Second delete of the same id should surface 404 per controller logic
        mockMvc.perform(delete("/api/v1/admin/allowed-ip-ranges/" + createdRangeId)
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isNotFound());
    }
}
