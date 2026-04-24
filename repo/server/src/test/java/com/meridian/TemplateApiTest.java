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
 * B-30 / B-31 / B-32 (§A.7) — TemplateController (notification templates) coverage.
 *
 * XSS hardening surface: the stored body feeds into TemplateRenderer (already
 * unit-tested for escaping). This test pins the PUT endpoint's authz + key-404 shape.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void listTemplates_adminJwt_returns200() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(get("/api/v1/admin/notification-templates")
                .header(HttpHeaders.AUTHORIZATION, admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(2)
    void listTemplates_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/admin/notification-templates")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void listTemplates_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notification-templates"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void updateTemplate_unknownKey_returns404() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        String body = """
            {
              "subject": "Test",
              "bodyMarkdown": "Hello **{{name}}**",
              "variables": ["name"]
            }
            """;
        mockMvc.perform(put("/api/v1/admin/notification-templates/this_template_does_not_exist")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void updateTemplate_studentJwt_returns403() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        String body = """
            {
              "subject": "Test",
              "bodyMarkdown": "hi",
              "variables": []
            }
            """;
        mockMvc.perform(put("/api/v1/admin/notification-templates/approval_created")
                .header(HttpHeaders.AUTHORIZATION, student)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void updateTemplate_anonymous_returns401() throws Exception {
        String body = """
            {
              "subject": "x",
              "bodyMarkdown": "y",
              "variables": []
            }
            """;
        mockMvc.perform(put("/api/v1/admin/notification-templates/approval_created")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void updateTemplate_emptyBody_returns400() throws Exception {
        String admin = TestAuthHelper.loginAdmin(mockMvc);
        mockMvc.perform(put("/api/v1/admin/notification-templates/approval_created")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
