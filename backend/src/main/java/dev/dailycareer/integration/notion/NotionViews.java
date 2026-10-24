package dev.dailycareer.integration.notion;

import java.time.OffsetDateTime;

public final class NotionViews {
    private NotionViews() {}

    public record Authorization(String authorizationUrl, OffsetDateTime expiresAt) {}
    public record DisconnectResult(Boolean disconnected, String providerRevocationStatus) {}
}
