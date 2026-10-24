package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.integration.browser.BrowserConnections;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class OpenRouterCompletion {
    private final BrowserConnections connections;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public OpenRouterCompletion(BrowserConnections connections, ObjectMapper json) { this.connections = connections; this.json = json; }
    public AiProvider.Completion complete(long user, String reference, String model, String purpose, JsonNode input) throws Exception {
        boolean practice = "PRACTICE_PROBLEMS".equals(purpose);
        if (!practice && !"DAILY_FEEDBACK".equals(purpose)) throw new IllegalArgumentException("Unsupported OpenRouter purpose");
        String key = connections.credential(user, "openrouter", reference).path("key").asText();
        String instructions = practice ? AiPracticeQuestions.instructions() : StudyPrompts.feedbackInstructions();
        JsonNode safeInput=practice?StudyPrompts.practiceInput(json,input):StudyPrompts.feedbackInput(json,input);
        String body = json.writeValueAsString(Map.of("model", model, "stream", false, "max_tokens", practice ? 16000 : 1800,
                "response_format", Map.of("type", "json_object"), "messages", List.of(
                        Map.of("role", "system", "content", instructions),
                        Map.of("role", "user", "content", safeInput.toString()))));
        var request = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .timeout(Duration.ofSeconds(practice ? 150 : 60)).header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json").header("X-Title", "Daily Leap")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("OpenRouter request rejected");
        var root = json.readTree(response.body());
        JsonNode answer = json.readTree(root.path("choices").path(0).path("message").path("content").asText());
        JsonNode result = practice ? answer : normalize(json, answer, input);
        return new AiProvider.Completion(result, root.path("id").asText(null),
                Math.max(0, root.path("usage").path("prompt_tokens").asLong()),
                Math.max(0, root.path("usage").path("completion_tokens").asLong()));
    }
    static ObjectNode normalize(ObjectMapper json, JsonNode answer, JsonNode input) {
        if (answer == null || !answer.isObject()) throw new IllegalArgumentException("Invalid AI response");
        ObjectNode result = json.createObjectNode().put("type", "DAILY_FEEDBACK")
                .put("curriculumId", input.path("curriculumId").asText()).put("date", input.path("date").asText());
        JsonNode summary = answer.path("summary");
        if (!summary.isTextual() || summary.asText().isBlank() || summary.asText().length() > 5000
                || !summary.asText().matches("(?s).*[가-힣].*")) throw new IllegalArgumentException("Invalid Korean feedback");
        result.set("summary", summary);
        for (String field : List.of("strengths", "weaknesses", "nextStudyFocus")) {
            JsonNode values = answer.path(field);
            if (!values.isArray() || values.size() > 10) throw new IllegalArgumentException("Invalid feedback list");
            if (input.has("studyFocus") && !"nextStudyFocus".equals(field) && !values.isEmpty())
                throw new IllegalArgumentException("Learner performance must not be inferred from topics");
            for (JsonNode item : values) if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 1000)
                throw new IllegalArgumentException("Invalid feedback item");
            result.set(field, values);
        }
        return result;
    }
}
