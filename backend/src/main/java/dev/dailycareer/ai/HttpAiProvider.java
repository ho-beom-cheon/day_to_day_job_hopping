package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.integration.browser.BrowserConnections;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class HttpAiProvider implements AiProvider {
    private final BrowserConnections connections;
    private final OpenRouterCompletion openRouter;
    private final OpenAiCompletion openAi;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String endpoint, key, model, providerType, openAiKey, openAiModel;

    HttpAiProvider(ObjectMapper json, @Value("${AI_PROVIDER_ENDPOINT:}") String endpoint,
                   @Value("${AI_PROVIDER_API_KEY:}") String key, @Value("${AI_PROVIDER_MODEL:}") String model,
                   @Value("${AI_PROVIDER_TYPE:}") String providerType,
                   @Value("${OPENAI_API_KEY:}") String openAiKey,
                   @Value("${OPENAI_MODEL:gpt-6-luna}") String openAiModel,
                   BrowserConnections connections, OpenRouterCompletion openRouter, OpenAiCompletion openAi) {
        this.json = json;
        this.endpoint = endpoint;
        this.key = key;
        this.model = model;
        this.providerType = providerType;
        this.openAiKey = openAiKey;
        this.openAiModel = openAiModel;
        this.connections = connections;
        this.openRouter = openRouter;
        this.openAi = openAi;
    }

    private boolean directOpenAi() { return "openai".equalsIgnoreCase(providerType); }
    @Override public boolean enabled(long user) { return credentialReference(user) != null || enabled(); }
    @Override public String credentialReference(long user) {
        return directOpenAi() ? null : connections.reference(user, "openrouter");
    }
    @Override public String name(long user) { return credentialReference(user) != null ? "OPENROUTER" : name(); }
    @Override public String model(long user) { return credentialReference(user) != null ? connections.model() : model(); }
    @Override public boolean supports(long user, String purpose) {
        if(AiLessonContent.PURPOSE.equals(purpose) || StudyEvaluationContract.purpose(purpose)) return directOpenAi();
        return credentialReference(user) == null && !directOpenAi()
                || "DAILY_FEEDBACK".equals(purpose) || "PRACTICE_PROBLEMS".equals(purpose);
    }
    @Override public Completion complete(long user, String reference, String jobModel, String purpose, JsonNode input) throws Exception {
        if (reference != null) return openRouter.complete(user, reference, jobModel, purpose, input);
        if (directOpenAi()) return openAi.complete(openAiKey, jobModel, purpose, input);
        return complete(purpose, input);
    }
    @Override public boolean enabled() {
        return directOpenAi() ? !openAiKey.isBlank() && !openAiModel.isBlank()
                : !endpoint.isBlank() && !key.isBlank() && !model.isBlank();
    }
    @Override public String name() { return directOpenAi() ? "OPENAI" : enabled() ? "HTTP_GATEWAY" : "DISABLED"; }
    @Override public String model() { return directOpenAi() ? openAiModel : enabled() ? model : "none"; }
    @Override public Completion complete(String purpose, JsonNode input) throws Exception {
        if (directOpenAi()) return openAi.complete(openAiKey, openAiModel, purpose, input);
        if (!enabled()) throw new IllegalStateException("AI provider is disabled");
        String body = json.writeValueAsString(Map.of("purpose", purpose, "model", model, "input", input));
        var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("AI provider status " + response.statusCode());
        JsonNode root = json.readTree(response.body()), result = root.path("result");
        if (result.isMissingNode() || result.isNull()) throw new IllegalStateException("AI provider result missing");
        return new Completion(result, root.path("requestId").asText(null),
                root.path("inputTokens").asLong(0), root.path("outputTokens").asLong(0));
    }
}
