package com.polyglotmesh.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Correlation ids must be safe to echo, present in the logging context only for
 * the duration of the request, and generated when the caller supplies nothing
 * usable.
 */
class RequestIdFilterTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesARequestIdAndPublishesIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/execute");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(duringChain));

        String header = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(header).matches(UUID_PATTERN);
        assertThat(duringChain.get()).isEqualTo(header);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void keepsAWellFormedSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "trace-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringChain = new AtomicReference<>();

        filter.doFilter(request, response, capture(duringChain));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo("trace-12345678");
        assertThat(duringChain.get()).isEqualTo("trace-12345678");
    }

    @Test
    void replacesUnsafeSuppliedRequestIds() throws Exception {
        for (String unsafe : new String[] {
                "short", "has space", "line\nbreak", "slash/inside", "x".repeat(129)}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, unsafe);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, capture(new AtomicReference<>()));

            assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                    .as("unsafe header %s", unsafe)
                    .isNotEqualTo(unsafe)
                    .matches(UUID_PATTERN);
        }
    }

    @Test
    void restoresAPreviousMdcValue() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "outer-12345678");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capture(new AtomicReference<>()));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("outer-12345678");
    }

    @Test
    void resolveAcceptsOnlyConservativeIds() {
        assertThat(RequestIdFilter.resolve("trace-12345678")).isEqualTo("trace-12345678");
        assertThat(RequestIdFilter.resolve("a.b_c:d-1234")).isEqualTo("a.b_c:d-1234");
        assertThat(RequestIdFilter.resolve(null)).matches(UUID_PATTERN);
        assertThat(RequestIdFilter.resolve("")).matches(UUID_PATTERN);
        assertThat(RequestIdFilter.resolve("   ")).matches(UUID_PATTERN);
        assertThat(RequestIdFilter.resolve("trace 12345678")).matches(UUID_PATTERN);
    }

    private static FilterChain capture(AtomicReference<String> target) {
        return (request, response) -> target.set(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
