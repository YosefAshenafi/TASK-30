package com.meridian.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestIdFilter}.
 *
 * Verifies:
 *  - A request without the X-Request-Id header is assigned a UUID that is
 *    both echoed back on the response and visible via MDC while the filter
 *    chain runs.
 *  - A request that already has X-Request-Id is honored end-to-end.
 *  - MDC is cleaned up after the chain completes, even on exception paths.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void missingHeader_assignsUuidAndPropagatesToResponseAndMdc() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (request, response) -> mdcDuringChain.set(MDC.get(RequestIdFilter.REQUEST_ID_HEADER));

        filter.doFilter(req, res, chain);

        String assigned = res.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(assigned).isNotBlank();
        // Valid UUID format
        assertThat(assigned).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(mdcDuringChain.get()).isEqualTo(assigned);
        // Request attribute must match the generated id
        assertThat(req.getAttribute(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(assigned);
    }

    @Test
    void suppliedHeader_isHonoredEndToEnd() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        String clientId = "client-supplied-trace-id-42";
        req.addHeader(RequestIdFilter.REQUEST_ID_HEADER, clientId);
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (request, response) -> mdcDuringChain.set(MDC.get(RequestIdFilter.REQUEST_ID_HEADER));

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(clientId);
        assertThat(req.getAttribute(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(clientId);
        assertThat(mdcDuringChain.get()).isEqualTo(clientId);
    }

    @Test
    void blankHeader_isTreatedAsMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "   ");
        FilterChain chain = (request, response) -> {};

        filter.doFilter(req, res, chain);

        String assigned = res.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(assigned).isNotBlank();
        assertThat(assigned.trim()).isNotEqualTo("");
    }

    @Test
    void mdcIsClearedAfterChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {};

        filter.doFilter(req, res, chain);

        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_HEADER)).isNull();
    }

    @Test
    void mdcIsClearedEvenWhenChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {
            throw new RuntimeException("simulated downstream failure");
        };

        try {
            filter.doFilter(req, res, chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get(RequestIdFilter.REQUEST_ID_HEADER)).isNull();
    }
}
