package dev.dailycareer.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    public static String traceId(HttpServletRequest request) {
        var existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String traceId) return traceId;
        String traceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTRIBUTE, traceId);
        return traceId;
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String previous = MDC.get("traceId");
        // Generated locally: untrusted input cannot inject log lines or choose another request's trace.
        String traceId = traceId(request);
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/")) {
            response.setHeader("Cache-Control", "no-store");
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove("traceId");
            else MDC.put("traceId", previous);
        }
    }
}
