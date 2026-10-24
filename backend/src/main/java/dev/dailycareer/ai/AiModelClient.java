package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/** One structured-output model call. IOException and InterruptedException mean the provider result is unknown. */
public interface AiModelClient {
    Completion complete(String model, AiRequest request) throws IOException, InterruptedException;

    record Tokens(long input, long cachedInput, long output, long reasoning) {}
    record Completion(JsonNode result, String requestId, Tokens tokens) {}
}
