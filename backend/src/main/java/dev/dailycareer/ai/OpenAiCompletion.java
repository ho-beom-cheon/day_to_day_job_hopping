package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
final class OpenAiCompletion {
    private static final URI RESPONSES = URI.create("https://api.openai.com/v1/responses");
    private static final String INSTRUCTIONS = StudyPrompts.feedbackInstructions();
    private final ObjectMapper json;
    private final HttpClient http;

    OpenAiCompletion(ObjectMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    AiProvider.Completion complete(String key, String model, String purpose, JsonNode input) throws Exception {
        boolean practice = "PRACTICE_PROBLEMS".equals(purpose);
        boolean lesson = AiLessonContent.PURPOSE.equals(purpose);
        boolean study=purpose.startsWith("STUDY_");
        StudyPrompts.Role role=study?StudyPrompts.Role.valueOf(purpose.substring(6)):null;
        if (!practice && !study && !lesson && !"DAILY_FEEDBACK".equals(purpose)) throw new IllegalArgumentException("Unsupported OpenAI purpose");
        String schemaName=study?switch(role){case GRADING->"grading";case LEARNING,SOURCE_AUTHORING->"learning";case VALIDATION,GAP_AUDIT->"audit";default->"assessment";}:null;
        JsonNode safeInput=practice?StudyPrompts.practiceInput(json,input):lesson?AiLessonContent.providerInput(json,input):study?input:StudyPrompts.feedbackInput(json,input);
        var requestBody = new LinkedHashMap<String,Object>(Map.of(
                "model", model, "store", false, "max_output_tokens", practice || study || lesson ? 16000 : 1800,
                "input", List.of(Map.of("role", "developer", "content", practice ? AiPracticeQuestions.instructions() : lesson?AiLessonContent.instructions():study?StudyPrompts.instructions(role):INSTRUCTIONS),
                        Map.of("role", "user", "content", safeInput.toString())),
                "text", Map.of("format", Map.of("type", "json_schema", "name", practice ? "practice_questions" : lesson?"personal_learning":study?"study_"+schemaName:"daily_feedback", "strict", true,
                        "schema", practice ? AiPracticeQuestions.schema() : lesson?AiLessonContent.schema():study?StudyPrompts.schema(json,schemaName):schema()))));
        if(lesson) {
            requestBody.put("tools",List.of(Map.of("type","web_search","filters",Map.of("allowed_domains",AiLessonContent.DOMAINS))));
            requestBody.put("tool_choice","required");
            requestBody.put("include",List.of("web_search_call.action.sources"));
        }
        String body=json.writeValueAsString(requestBody);
        var request = HttpRequest.newBuilder(RESPONSES).timeout(Duration.ofSeconds(practice || study || lesson ? 150 : 60))
                .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = json.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw providerError(response.statusCode(), root);
        if (!"completed".equals(root.path("status").asText())) throw new IllegalStateException("OpenAI response incomplete");
        JsonNode generated = json.readTree(outputText(root));
        if(lesson && generated instanceof ObjectNode object)
            object.set("observedSourceUrls",json.valueToTree(AiLessonContent.observedSources(root)));
        JsonNode result = practice || study || lesson ? generated : OpenRouterCompletion.normalize(json, generated, input);
        JsonNode usage = root.path("usage");
        return new AiProvider.Completion(result, root.path("id").asText(null),
                Math.max(0, usage.path("input_tokens").asLong()), Math.max(0, usage.path("output_tokens").asLong()));
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
                || "insufficient_quota".equals(error.path("type").asText())))
            return new QuotaExceededException();
        return new IllegalStateException("OpenAI request rejected (HTTP " + status + ")");
    }

    static final class QuotaExceededException extends RuntimeException {
        QuotaExceededException() { super("OpenAI quota exhausted"); }
    }

    private static Map<String, Object> schema() {
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("summary", "strengths", "weaknesses", "nextStudyFocus"),
                "properties", Map.of("summary", Map.of("type", "string"),
                        "strengths", Map.of("type", "array", "items", Map.of("type", "string")),
                        "weaknesses", Map.of("type", "array", "items", Map.of("type", "string")),
                        "nextStudyFocus", Map.of("type", "array", "items", Map.of("type", "string"))));
    }
}
