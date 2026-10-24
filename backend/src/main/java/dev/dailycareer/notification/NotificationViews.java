package dev.dailycareer.notification;

import dev.dailycareer.common.json.JsonId;
import java.time.OffsetDateTime;
import java.util.List;

public final class NotificationViews {
    private NotificationViews() {}

    public record Notification(@JsonId Long notificationId, String type, String title, String message,
                               String targetPath, Boolean read, OffsetDateTime createdAt, OffsetDateTime readAt) {}
    public record NotificationPage(List<Notification> content, Integer page, Integer size,
                                   Integer totalElements, Integer totalPages, Integer unreadCount) {}
    public record ReadAllRequest(OffsetDateTime through) {}
    public record ReadAllResult(Integer updatedCount, OffsetDateTime through) {}
}
