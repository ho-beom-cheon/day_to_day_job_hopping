package dev.dailycareer.integration.slack;

import dev.dailycareer.common.json.JsonId;
import java.time.OffsetDateTime;
import java.util.List;

public final class SlackViews {
    private SlackViews() {}

    public record JobReceipt(@JsonId Long jobId, String status, String statusPath,
                             OffsetDateTime createdAt, Boolean reused) {}
    public record SlackDelivery(@JsonId Long deliveryId, String eventType, String status,
                                OffsetDateTime createdAt, OffsetDateTime completedAt, String errorCode) {}
    public record SlackDeliveryPage(List<SlackDelivery> content, Integer page, Integer size,
                                    Integer totalElements, Integer totalPages) {}
}
