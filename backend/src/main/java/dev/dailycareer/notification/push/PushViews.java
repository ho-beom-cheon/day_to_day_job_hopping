package dev.dailycareer.notification.push;

import dev.dailycareer.common.json.JsonId;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class PushViews {
    private PushViews() {}
    public record PushKeys(String p256dh, String auth) {}
    public record PushSubscribe(UUID deviceId, String deviceName, String endpoint, Long expirationTime, PushKeys keys) {}
    public record PushSubscription(@JsonId Long subscriptionId, UUID deviceId, String deviceName, Boolean active,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record DeletedResult(Boolean deleted) {}
}
