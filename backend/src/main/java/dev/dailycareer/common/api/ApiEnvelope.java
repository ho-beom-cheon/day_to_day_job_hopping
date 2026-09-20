package dev.dailycareer.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** OpenAPI v1.2.1: null data/error/retryAfterSeconds remain present. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiEnvelope<T>(boolean success, T data, ApiError error, Meta meta) {
    public record Meta(String traceId, OffsetDateTime serverTime) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ApiError(String code, String message, List<ValidationDetail> details,
                           boolean retryable, Integer retryAfterSeconds) {}
    public record ValidationDetail(String field, String message) {}
}
