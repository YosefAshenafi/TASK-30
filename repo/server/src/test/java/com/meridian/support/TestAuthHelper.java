package com.meridian.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared login helper for real-JWT integration tests.
 *
 * Remediation of M-1 (§A.3): every admin/business-surface *ApiTest.java should
 * exercise the real JwtAuthenticationFilter for at least one positive case by
 * obtaining a token here instead of using @WithMockUser.
 *
 * Seeded credentials (see repo/server/src/main/resources/db/migration/local/):
 *   - admin     / Admin@123!    (ADMIN)
 *   - student1  / Test@123!     (STUDENT)
 *   - student2  / Test@123!     (STUDENT)
 *   - mentor1   / Test@123!     (CORPORATE_MENTOR)
 *   - faculty1  / Test@123!     (FACULTY_MENTOR)
 */
public final class TestAuthHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestAuthHelper() {}

    /** Log in and return only the access token (Bearer-ready). */
    public static String login(MockMvc mockMvc, String username, String password) throws Exception {
        String body = """
            {
              "username": "%s",
              "password": "%s",
              "deviceFingerprint": "test-helper-fingerprint"
            }
            """.formatted(username, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode json = MAPPER.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    /** `Authorization: Bearer <token>` header value for MockMvc `.header(...)`. */
    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Log in and return the Authorization header value directly. */
    public static String loginAsBearer(MockMvc mockMvc, String username, String password) throws Exception {
        return bearer(login(mockMvc, username, password));
    }

    public static String loginAdmin(MockMvc mockMvc) throws Exception {
        return loginAsBearer(mockMvc, "admin", "Admin@123!");
    }

    public static String loginStudent1(MockMvc mockMvc) throws Exception {
        return loginAsBearer(mockMvc, "student1", "Test@123!");
    }

    public static String loginStudent2(MockMvc mockMvc) throws Exception {
        return loginAsBearer(mockMvc, "student2", "Test@123!");
    }

    public static String loginMentor(MockMvc mockMvc) throws Exception {
        return loginAsBearer(mockMvc, "mentor1", "Test@123!");
    }

    public static String loginFaculty(MockMvc mockMvc) throws Exception {
        return loginAsBearer(mockMvc, "faculty1", "Test@123!");
    }
}
