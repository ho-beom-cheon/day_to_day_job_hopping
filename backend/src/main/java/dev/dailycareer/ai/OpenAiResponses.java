package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** OpenAI Responses API with strict structured output. */
@Component
public class OpenAiResponses implements AiModelClient {
    private static final URI RESPONSES = URI.create("https://api.openai.com/v1/responses");
    private final ObjectMapper json;
    private final AiSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public OpenAiResponses(ObjectMapper json, AiSettings settings) {
        this.json = json;
        this.settings = settings;
    }

    @Override public Completion complete(String model, AiRequest request) throws IOException, InterruptedException {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("store", false);
        body.put("max_output_tokens", request.maxOutputTokens());
        body.put("reasoning", Map.of("effort", request.reasoningEffort()));
        body.put("input", List.of(Map.of("role", "developer", "content", request.instructions()),
                Map.of("role", "user", "content", request.input())));
        body.put("text", Map.of("format", Map.of("type", "json_schema", "name", request.schemaName(), "strict", true,
                "schema", request.schema())));
        var http = HttpRequest.newBuilder(RESPONSES).timeout(Duration.ofSeconds(150))
                .header("Authorization", "Bearer " + settings.apiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = this.http.send(http, HttpResponse.BodyHandlers.ofString());
        JsonNode root = json.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw providerError(response.statusCode(), root);
        JsonNode usage = root.path("usage");
        long input = Math.max(0, usage.path("input_tokens").asLong());
        long output = Math.max(0, usage.path("output_tokens").asLong());
        var tokens = new Tokens(input, Math.min(input, Math.max(0, usage.path("input_tokens_details").path("cached_tokens").asLong())),
                output, Math.min(output, Math.max(0, usage.path("output_tokens_details").path("reasoning_tokens").asLong())));
        String requestId = root.path("id").asText(null);
        if (!"completed".equals(root.path("status").asText())) throw new IncompleteException(requestId, tokens);
        return new Completion(json.readTree(outputText(root)), requestId, tokens);
    }

    static String outputText(JsonNode response) {
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText()) && !content.path("text").asText().isBlank())
                    return content.path("text").asText();
            }
        }
        throw new IllegalArgumentException("OpenAI response text missing");
    }

    static RuntimeException providerError(int status, JsonNode response) {
        JsonNode error = response.path("error");
        if (status == 429 && ("credit_balance_exhausted".equals(error.path("code").asText())
                || "insufficient_quota".equals(error.path("type").asText()))) return new QuotaExceededException();
        return new IllegalStateException("OpenAI request rejected (HTTP " + status + ")");
    }

    static final class QuotaExceededException extends RuntimeException {
        QuotaExceededException() { super("OpenAI quota exhausted"); }
    }

    /** The provider billed tokens but stopped early, usually at max_output_tokens. */
    static final class IncompleteException extends RuntimeException {
        private final String requestId;
        private final Tokens tokens;
        IncompleteException(String requestId, Tokens tokens) {
            super("OpenAI response incomplete");
            this.requestId = requestId;
            this.tokens = tokens;
        }
        String requestId() { return requestId; }
        Tokens tokens() { return tokens; }
    }
}
