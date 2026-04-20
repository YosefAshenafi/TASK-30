package com.meridian.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private RateLimitProperties props;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        props.setEnabled(true);
        props.setDefaultCapacity(120);
        props.setDefaultRefillSeconds(60);

        // Very tight limit for test: 2 requests per minute on login
        RateLimitProperties.EndpointLimit loginLimit = new RateLimitProperties.EndpointLimit();
        loginLimit.setMethod("POST");
        loginLimit.setPath("/api/v1/auth/login");
        loginLimit.setCapacity(2);
        loginLimit.setRefillSeconds(60);
        props.setLimits(List.of(loginLimit));

        filter = new RateLimitFilter(new ObjectMapper(), props);
        chain = mock(FilterChain.class);
    }

    @Test
    void allowsRequestsUnderLimit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void returns429WhenLimitExceeded() throws Exception {
        String ip = "10.0.0.99";

        // Exhaust the bucket (capacity = 2)
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
        }

        // The 3rd request must be rate-limited
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        StringWriter body = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(body));
        // Use a real MockHttpServletResponse to capture headers
        MockHttpServletResponse rateLimitedRes = new MockHttpServletResponse();
        filter.doFilter(req, rateLimitedRes, chain);

        assertThat(rateLimitedRes.getStatus()).isEqualTo(429);
        assertThat(rateLimitedRes.getHeader("Retry-After")).isNotNull();
        // Chain should only have been called twice (the passing requests)
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void disabledFilter_passesAllRequests() throws Exception {
        props.setEnabled(false);
        filter = new RateLimitFilter(new ObjectMapper(), props);
        FilterChain newChain = mock(FilterChain.class);

        // Even if we send 100 requests they all pass
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("1.2.3.4");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, newChain);
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
        verify(newChain, times(5)).doFilter(any(), any());
    }

    @Test
    void defaultLimitAppliedForUnmappedEndpoints() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sessions");
        req.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        // Default capacity is 120 — first request must pass
        assertThat(res.getStatus()).isNotEqualTo(429);
        verify(chain).doFilter(req, res);
    }

    @Test
    void retryAfterHeaderIsPositiveInteger() throws Exception {
        String ip = "10.0.0.50";

        // Exhaust bucket
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            r.setRemoteAddr(ip);
            filter.doFilter(r, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        String retryAfter = res.getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isGreaterThan(0);
    }
}
