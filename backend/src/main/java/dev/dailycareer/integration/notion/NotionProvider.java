package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;

interface NotionProvider {
    enum FailureKind { INVALID_TARGET, REAUTH_REQUIRED, RETRYABLE, REJECTED, UNKNOWN }
    enum WriteResult { CREATED, UPDATED, SKIPPED }

    record OAuthToken(String accessToken, String workspaceId, String workspaceName) {}
    record WriteOutcome(WriteResult result, String externalObjectId) {}

    final class Failure extends RuntimeException {
        private final FailureKind kind;
        Failure(FailureKind kind) { super(kind.name()); this.kind = kind; }
        FailureKind kind() { return kind; }
    }

    OAuthToken exchange(String code);
    void revoke(String token);
    void validateTarget(String token, NotionViews.Target target);
    default NotionViews.AvailableTargets availableTargets(String token, String query, String cursor) {
        return new NotionViews.AvailableTargets(java.util.List.of(), null);
    }
    WriteOutcome upsert(String token, NotionViews.Target target, String sourceKey,
                        long sourceRevision, JsonNode payload, String knownExternalObjectId);
}
