package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiProvider {
    boolean enabled();
    String name();
    String model();
    Completion complete(String purpose, JsonNode input) throws Exception;
    default boolean enabled(long user) { return enabled(); }
    default String name(long user) { return name(); }
    default String model(long user) { return model(); }
    default String credentialReference(long user) { return null; }
    default boolean supports(long user, String purpose) { return true; }
    default Completion complete(long user, String credential, String model, String purpose, JsonNode input) throws Exception {
        return complete(purpose, input);
    }
    record Completion(JsonNode result, String requestId, long inputTokens, long outputTokens) {}
}
