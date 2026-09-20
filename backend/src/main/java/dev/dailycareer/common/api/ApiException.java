package dev.dailycareer.common.api;

import java.util.List;
import dev.dailycareer.common.api.ApiEnvelope.ValidationDetail;

public final class ApiException extends RuntimeException {
    private final ApiErrorCode code;
    private final List<ValidationDetail> details;

    public ApiException(ApiErrorCode code) { this(code, List.of()); }
    public ApiException(ApiErrorCode code, List<ValidationDetail> details) {
        super(code.name());
        this.code = code;
        this.details = details.stream().limit(100).map(detail -> new ValidationDetail(
                bounded(detail.field(), 200), bounded(detail.message(), 500))).toList();
    }
    private static String bounded(String text, int maximum) {
        if (text == null || text.isBlank()) return "입력값을 확인해 주세요.";
        return text.substring(0, Math.min(maximum, text.length()));
    }
    public ApiErrorCode code() { return code; }
    public List<ValidationDetail> details() { return details; }
}
