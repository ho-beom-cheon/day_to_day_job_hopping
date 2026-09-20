package dev.dailycareer.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dailycareer.common.api.ApiEnvelope;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.common.concurrency.Revisions;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ResponseEntity;

/** Store data and safe resource headers, never cookies, credentials or a previous request's meta. */
public record StoredReply(int status, JsonNode data, String etag, String location) {
    public StoredReply {
        if (status != 200 && status != 201 && status != 202) throw new IllegalArgumentException("Expected a documented success status");
        if (etag != null) Revisions.parse(etag);
        if (location != null) {
            URI uri = URI.create(location);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getFragment() != null
                    || !uri.getPath().startsWith("/api/v1/") || location.contains("\r") || location.contains("\n")) {
                throw new IllegalArgumentException("Location must be an application API path");
            }
        }
        if (data != null) data = data.deepCopy();
    }

    @Override public JsonNode data() { return data == null ? null : data.deepCopy(); }

    public ResponseEntity<ApiEnvelope<JsonNode>> toResponse(HttpServletRequest request) {
        return ApiResponses.success(request, status, data(), etag, location);
    }
}
