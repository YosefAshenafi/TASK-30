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
 * B-33 / B-34 / B-35 (§A.7) — NotificationController coverage.
 *
 * User-scoped endpoints — any authenticated user can read their own notifications,
 * but not another user's (404 / object-level authz).
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void listNotifications_studentJwt_returns200WithPageShape() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.total").exists());
    }

    @Test
    @Order(2)
    void listNotifications_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void listUnreadNotifications_studentJwt_returns200() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/notifications?unread=true")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @Order(4)
    void unreadCount_studentJwt_returnsNumber() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").isNumber());
    }

    @Test
    @Order(5)
    void unreadCount_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void markRead_unknownId_returns404() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/notifications/00000000-0000-0000-0000-ffffffffffff/read")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void markRead_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/00000000-0000-0000-0000-000000000aa1/read"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void markAllRead_studentJwt_returns204() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        mockMvc.perform(post("/api/v1/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isNoContent());
    }

    @Test
    @Order(9)
    void markAllRead_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/read-all"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    void unreadCount_afterMarkAllRead_isZero() throws Exception {
        String student = TestAuthHelper.loginStudent1(mockMvc);
        // order(8) just drained; count must be 0
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
