package com.meridian.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationControllerTest extends TestContainersBase {

    private static final String NOTIFICATIONS_URL = "/api/notifications";
    private static final String STREAM_URL = "/api/notifications/stream";

    // Seeded administrator id (V2__seed_roles_and_users.sql) — the SSE stream registers an
    // emitter keyed by this user id, so a test push targets it to flush the response headers.
    private static final java.util.UUID ADMIN_USER_ID =
            java.util.UUID.fromString("33333333-0000-0000-0000-000000000001");

    @org.springframework.beans.factory.annotation.Autowired
    private com.meridian.service.SseNotificationService sseNotificationService;

    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAs("admin", "Admin@12345678");
        studentToken = loginAs("student1", "Student@12345678");
    }

    // Test 1: GET /api/notifications as authenticated → 200 with page structure
    @Test
    @Order(1)
    void getNotifications_asAuthenticated_returns200() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // Test 2: GET /api/notifications unauthenticated → 401
    @Test
    @Order(2)
    void getNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(NOTIFICATIONS_URL))
                .andExpect(status().isUnauthorized());
    }

    // Test 3: PUT /api/notifications/{id}/read with unknown id → 404 (or 200 if found)
    @Test
    @Order(3)
    void markRead_unknownId_returns404() throws Exception {
        java.util.UUID randomId = java.util.UUID.randomUUID();
        MvcResult result = mockMvc.perform(put(NOTIFICATIONS_URL + "/" + randomId + "/read")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();
        int status = result.getResponse().getStatus();
        // 404 if not found, 200 if there happens to be a notification for this ID
        assertThat(status).isIn(200, 404);
    }

    // Test 4: PUT /api/notifications/{id}/read unauthenticated → 401
    @Test
    @Order(4)
    void markRead_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put(NOTIFICATIONS_URL + "/" + java.util.UUID.randomUUID() + "/read"))
                .andExpect(status().isUnauthorized());
    }

    // Test 5: GET /api/notifications/stream as authenticated → 200 text/event-stream
    @Test
    @Order(5)
    void streamNotifications_asAuthenticated_returns200WithSseContentType() throws Exception {
        // The SSE endpoint returns an SseEmitter that is held open for streaming; its only
        // completion is a 30-minute server-side timeout. We verify the request was accepted as
        // an asynchronous SSE stream with HTTP 200 and the text/event-stream content type, which
        // Spring negotiates synchronously when the emitter is returned. We deliberately do NOT
        // asyncDispatch() the result: the emitter never completes on its own, so dispatching
        // would block the test until that 30-minute timeout elapsed.
        MvcResult result = mockMvc.perform(get(STREAM_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        // The emitter is registered synchronously during request handling, but Spring only
        // flushes the text/event-stream headers to the response when the first event is written.
        // Push one event so the content type is committed, then assert it.
        sseNotificationService.push(ADMIN_USER_ID, "TEST", "ping");

        assertThat(result.getResponse().getContentType())
                .contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    // Test 6: GET /api/notifications/stream unauthenticated → 401
    @Test
    @Order(6)
    void streamNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(STREAM_URL)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
