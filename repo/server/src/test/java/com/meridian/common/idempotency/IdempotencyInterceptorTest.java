package com.meridian.common.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IdempotencyInterceptor}.
 *
 * Validates the interceptor's single responsibility: extracting the
 * {@code Idempotency-Key} header and exposing it to downstream handlers as a
 * request attribute.
 */
class IdempotencyInterceptorTest {

    private final IdempotencyInterceptor interceptor = new IdempotencyInterceptor();

    @Test
    void preHandle_withValidIdempotencyKey_setsAttributeAndContinues() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader("Idempotency-Key", "abc-123-request-key");

        boolean cont = interceptor.preHandle(req, res, new Object());

        assertThat(cont).isTrue();
        assertThat(req.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTR))
                .isEqualTo("abc-123-request-key");
    }

    @Test
    void preHandle_withoutHeader_leavesAttributeUnset() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean cont = interceptor.preHandle(req, res, new Object());

        assertThat(cont).isTrue();
        assertThat(req.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTR)).isNull();
    }

    @Test
    void preHandle_withBlankHeader_leavesAttributeUnset() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader("Idempotency-Key", "   ");

        boolean cont = interceptor.preHandle(req, res, new Object());

        assertThat(cont).isTrue();
        assertThat(req.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTR)).isNull();
    }

    @Test
    void preHandle_withEmptyStringHeader_leavesAttributeUnset() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader("Idempotency-Key", "");

        boolean cont = interceptor.preHandle(req, res, new Object());

        assertThat(cont).isTrue();
        assertThat(req.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTR)).isNull();
    }

    @Test
    void preHandle_whitespacePaddedKey_isStoredVerbatim() {
        // The interceptor does not trim — downstream callers receive the raw value.
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader("Idempotency-Key", " paddedkey ");

        interceptor.preHandle(req, res, new Object());

        assertThat(req.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTR))
                .isEqualTo(" paddedkey ");
    }

    @Test
    void preHandle_alwaysReturnsTrue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        // Regardless of header state the interceptor must not short-circuit the chain.
        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();

        req.addHeader("Idempotency-Key", "x");
        assertThat(interceptor.preHandle(req, res, new Object())).isTrue();
    }
}
