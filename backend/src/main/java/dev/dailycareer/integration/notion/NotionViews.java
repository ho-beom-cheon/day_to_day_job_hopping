package dev.dailycareer.integration.notion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class NotionViews {
    private NotionViews() {}

    public static final List<String> ENTITY_TYPES = List.of(
            "LEARNING_RESULT", "PROBLEM", "TEST_RESULT", "WRONG_ANSWER", "WEEKLY_REPORT", "MONTHLY_REPORT");

    public record Authorization(String authorizationUrl, OffsetDateTime expiresAt) {}
    public record DisconnectResult(Boolean disconnected, String providerRevocationStatus) {}
    public record Target(String entityType, String databaseId, String dataSourceId) {}
    public record Targets(List<Target> targets, OffsetDateTime updatedAt, Integer revision) {}
    public record TargetsPut(List<Target> targets) {}
    public record AvailableTarget(String title, String databaseId, String dataSourceId) {}
    public record AvailableTargets(List<AvailableTarget> content, String nextCursor) {}
    public record SyncRequest(String curriculumId, List<String> entityTypes, LocalDate from, LocalDate to) {}
    public record JobReceipt(String jobId, String status, String statusPath, OffsetDateTime createdAt, Boolean reused) {}
    public record SyncFailure(String entityType, String sourceId, String code, String message, Boolean retryable) {}
    public record SyncJob(String syncId, String curriculumId, String status, Integer connectionRevision,
                          Integer targetsRevision, List<String> entityTypes, LocalDate from, LocalDate to,
                          Integer createdCount, Integer updatedCount, Integer skippedCount, Integer failedCount,
                          Integer totalCount, OffsetDateTime startedAt, OffsetDateTime completedAt,
                          OffsetDateTime createdAt, List<SyncFailure> failures) {}
    public record SyncPage(List<SyncJob> content, Integer page, Integer size, Integer totalElements, Integer totalPages) {}
}
