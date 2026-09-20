package dev.dailycareer.common.api;

import dev.dailycareer.common.trace.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Explicit wrapping keeps Actuator, redirects and future streaming endpoints separate. */
public final class ApiResponses {
    private ApiResponses() {}

    public static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/");
    }

    public static ApiEnvelope.Meta meta(HttpServletRequest request) {
        return new ApiEnvelope.Meta(TraceIdFilter.traceId(request), OffsetDateTime.now(ZoneOffset.ofHours(9)));
    }

    public static HttpHeaders headers(HttpServletRequest request) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl("no-store");
        headers.set("X-Trace-Id", TraceIdFilter.traceId(request));
        return headers;
    }

    public static <T> ResponseEntity<ApiEnvelope<T>> ok(HttpServletRequest request, T data) {
        return success(request, 200, data, null, null);
    }

    public static <T> ResponseEntity<ApiEnvelope<T>> success(HttpServletRequest request, int status, T data,
                                                          String etag, String location) {
        var headers = headers(request);
        if (etag != null) headers.setETag(etag);
        if (location != null) headers.set(HttpHeaders.LOCATION, location);
        return ResponseEntity.status(status).headers(headers).body(new ApiEnvelope<>(true, data, null, meta(request)));
    }

    public static ResponseEntity<ApiEnvelope<Void>> failure(HttpServletRequest request, ApiException exception) {
        var code = exception.code();
        var headers = headers(request);
        if (code.retryAfterSeconds() != null) headers.set(HttpHeaders.RETRY_AFTER, code.retryAfterSeconds().toString());
        var error = new ApiEnvelope.ApiError(code.name(), code.message(), exception.details(), code.retryable(), code.retryAfterSeconds());
        return ResponseEntity.status(code.status()).headers(headers).body(new ApiEnvelope<>(false, null, error, meta(request)));
    }
}
