package com.meridian.api;

import com.meridian.dto.LoginRequest;
import com.meridian.dto.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest extends TestContainersBase {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ME_URL = "/api/auth/me";
    private static final String LOGOUT_URL = "/api/auth/logout";
    private static final String REFRESH_URL = "/api/auth/refresh";

    // Test 1: Valid registration returns 201
    @Test
    @Order(1)
    void register_withValidBody_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "newuser_auth_test",
                "SecurePass1!23456",
                null
        );

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(
                        "Registration successful. Awaiting administrator approval."));
    }

    // Test 2: Duplicate username returns 409
    @Test
    @Order(2)
    void register_duplicateUsername_returns409() throws Exception {
        RegisterRequest first = new RegisterRequest(
                "duplicate_user_test",
                "SecurePass1!23456",
                null
        );

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isConflict());
    }

    // Test 3: Password too short returns 400
    @Test
    @Order(3)
    void register_passwordTooShort_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "shortpass_user",
                "Short1!",
                null
        );

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Test 4: Password with no symbol returns 400
    @Test
    @Order(4)
    void register_passwordNoSymbol_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "nosymbol_user",
                "NoSymbol123456",
                null
        );

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // Test 5: Login with pending user returns 400 with "pending" message
    @Test
    @Order(5)
    void login_withPendingUser_returns400() throws Exception {
        RegisterRequest reg = new RegisterRequest(
                "pending_login_test",
                "Pending@12345678",
                null
        );
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("pending_login_test", "Pending@12345678");
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("pending")));
    }

    // Test 6: Login with valid active user returns 200 with nested user contract
    @Test
    @Order(6)
    void login_withActiveAdmin_returns200WithNestedUserContract() throws Exception {
        LoginRequest login = new LoginRequest("admin", "Admin@12345678");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn", notNullValue()))
                .andExpect(jsonPath("$.user", notNullValue()))
                .andExpect(jsonPath("$.user.id", notNullValue()))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role", notNullValue()))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true));
    }

    // Test 7: Login response does NOT expose flat role/userId/username (contract consistency)
    @Test
    @Order(7)
    void login_response_hasNoTopLevelRoleField() throws Exception {
        LoginRequest login = new LoginRequest("admin", "Admin@12345678");

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
        // top-level should have user object, not flat role/userId
        assertThat(root.has("user")).isTrue();
        assertThat(root.has("role")).isFalse();
        assertThat(root.has("userId")).isFalse();
    }

    // Test 8: Login with wrong password returns 401
    @Test
    @Order(8)
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest login = new LoginRequest("admin", "WrongPassword999!");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    // Test 9: GET /me authenticated returns 200 with username, no passwordHash
    @Test
    @Order(9)
    void getMe_authenticated_returns200WithUsernameNoPasswordHash() throws Exception {
        String token = loginAs("admin", "Admin@12345678");

        MvcResult result = mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("password_hash");
    }

    // Test 10: GET /me unauthenticated returns 401
    @Test
    @Order(10)
    void getMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 11: Logout authenticated returns 204
    @Test
    @Order(11)
    void logout_authenticated_returns204() throws Exception {
        LoginRequest login = new LoginRequest("admin", "Admin@12345678");

        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        jakarta.servlet.http.Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        String token = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        var requestBuilder = post(LOGOUT_URL)
                .header("Authorization", "Bearer " + token);

        if (refreshCookie != null) {
            requestBuilder = requestBuilder.cookie(refreshCookie);
        }

        mockMvc.perform(requestBuilder)
                .andExpect(status().isNoContent());
    }

    // Test 12: Refresh cookie has Secure and HttpOnly attributes
    @Test
    @Order(12)
    void login_refreshCookieHasSecureAndHttpOnly() throws Exception {
        LoginRequest login = new LoginRequest("admin", "Admin@12345678");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth/refresh"));
    }

    // Test 13: POST /api/auth/refresh with valid refresh cookie → 200 with full token payload
    @Test
    @Order(13)
    void refresh_withValidCookie_returns200WithTokenPayload() throws Exception {
        // First login to obtain a real refresh token cookie
        LoginRequest login = new LoginRequest("admin", "Admin@12345678");
        MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();

        // Use the refresh token to get a new access token
        MvcResult refreshResult = mockMvc.perform(post(REFRESH_URL)
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn", notNullValue()))
                .andExpect(jsonPath("$.user", notNullValue()))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(cookie().exists("refreshToken"))
                .andReturn();

        String body = refreshResult.getResponse().getContentAsString();
        assertThat(body).contains("accessToken");
        assertThat(body).doesNotContain("passwordHash");
    }

    // Test 14: POST /api/auth/refresh with no cookie → 401
    @Test
    @Order(14)
    void refresh_withMissingCookie_returns401() throws Exception {
        mockMvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 15: POST /api/auth/refresh with invalid/expired token value → 401
    @Test
    @Order(15)
    void refresh_withInvalidToken_returns401WithErrorShape() throws Exception {
        Cookie badCookie = new Cookie("refreshToken", "not-a-valid-jwt-token-value");
        badCookie.setPath("/api/auth/refresh");

        MvcResult result = mockMvc.perform(post(REFRESH_URL)
                        .cookie(badCookie))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Error response must have a message field (standard error envelope)
        if (!body.isEmpty()) {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            assertThat(root.has("message") || root.has("error")).isTrue();
        }
    }
}
