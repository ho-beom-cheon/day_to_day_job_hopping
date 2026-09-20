package dev.dailycareer.common.trace;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class TraceIdFilterTest {
    @Test void createsTraceAndClearsMdcEvenWhenRequestFails() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "untrusted");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("traceId")).isEqualTo(response.getHeader("X-Trace-Id"));
            throw new ServletException("test");
        })).isInstanceOf(ServletException.class);
        assertThat(response.getHeader("X-Trace-Id")).matches("[a-f0-9]{32}");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test void redispatchKeepsRequestTraceAndRestoresOuterMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/test");
        var first = new MockHttpServletResponse();
        var second = new MockHttpServletResponse();
        var filter = new TraceIdFilter();
        MDC.put("traceId", "outer");
        try {
            filter.doFilter(request, first, (req, res) -> {});
            request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
            filter.doFilter(request, second, (req, res) -> assertThat(MDC.get("traceId")).isEqualTo(first.getHeader("X-Trace-Id")));
            assertThat(second.getHeader("X-Trace-Id")).isEqualTo(first.getHeader("X-Trace-Id"));
            assertThat(second.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(MDC.get("traceId")).isEqualTo("outer");
        } finally { MDC.remove("traceId"); }
    }
}
