package com.meridian;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B-1 (§A.7): `GET /api/v1/health` schema-drift guard.
 *
 * Docker healthcheck depends on `$.status=="UP"` — tests here pin the shape so a
 * silent response-schema change is caught before the container restart loop.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_returnsUpAndVersion() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void healthEndpoint_acceptsAnonymous() throws Exception {
        // /api/v1/health is in the permitAll list of SecurityConfig; no Authorization header required
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk());
    }

    @Test
    void healthEndpoint_returnsRequestIdHeader() throws Exception {
        // Observability regression guard — RequestIdFilter must attach X-Request-Id on every response
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "test-req-health-001"))
            .andExpect(status().isOk())
            .andExpect(result -> {
                String hdr = result.getResponse().getHeader("X-Request-Id");
                if (hdr == null || hdr.isBlank()) {
                    throw new AssertionError("X-Request-Id header not attached to /health response");
                }
            });
    }
}
