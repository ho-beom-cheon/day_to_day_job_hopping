package dev.dailycareer.integration.notion;

interface NotionProvider {
    enum FailureKind { REAUTH_REQUIRED, RETRYABLE, REJECTED, UNKNOWN }

    record OAuthToken(String accessToken, String workspaceId, String workspaceName) {}

    final class Failure extends RuntimeException {
        private final FailureKind kind;
        Failure(FailureKind kind) { super(kind.name()); this.kind = kind; }
        FailureKind kind() { return kind; }
    }

    OAuthToken exchange(String code);
    void revoke(String token);
}
