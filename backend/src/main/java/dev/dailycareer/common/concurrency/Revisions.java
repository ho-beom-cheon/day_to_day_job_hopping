package dev.dailycareer.common.concurrency;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

public final class Revisions {
    private Revisions() {}

    public static int required(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders("If-Match"));
        if (values.isEmpty()) throw new ApiException(ApiErrorCode.PRECONDITION_REQUIRED);
        if (values.size() != 1) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        return parse(values.getFirst());
    }

    public static int parse(String etag) {
        if (etag == null) throw new ApiException(ApiErrorCode.PRECONDITION_REQUIRED);
        if (etag.length() > 24 || !etag.matches("\"[0-9]+\"")) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        try {
            long value = Long.parseLong(etag.substring(1, etag.length() - 1));
            if (value > Integer.MAX_VALUE) throw new NumberFormatException();
            return (int) value;
        } catch (NumberFormatException exception) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
    }

    public static String etag(long revision) {
        if (revision < 0 || revision > Integer.MAX_VALUE) throw new IllegalArgumentException("Revision is outside the API range");
        return "\"" + revision + "\"";
    }

    /** Call under the resource lock, or use expected in the atomic UPDATE predicate. */
    public static void requireMatch(int expected, long current) {
        if (expected != current) throw new ApiException(ApiErrorCode.PRECONDITION_FAILED);
    }
}
