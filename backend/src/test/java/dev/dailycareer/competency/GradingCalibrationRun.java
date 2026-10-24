package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.ai.AiModelClient;
import dev.dailycareer.ai.AiSettings;
import dev.dailycareer.ai.OpenAiResponses;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grading consistency check with the real model (design §6.3). Never part of a normal build:
 * run with {@code -Dtest=GradingCalibrationRun -Dcalibration.run=true} and OPENAI_API_KEY set.
 */
@EnabledIfSystemProperty(named = "calibration.run", matches = "true")
class GradingCalibrationRun {
    private final ObjectMapper json = new ObjectMapper();

    @Test void calibrationAnswersStayInRangeOrderedAndStable() throws Exception {
        var prompts = new LoopPrompts(json);
        var model = new OpenAiResponses(json, new AiSettings("openai", System.getenv("OPENAI_API_KEY"), System.getenv().getOrDefault("OPENAI_MODEL", "gpt-6-luna")));
        ArrayNode report = json.createArrayNode();
        List<String> failures = new ArrayList<>();
        long input = 0, cached = 0, output = 0;
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:competency/v1/sheets/*.md")) {
            SheetDocument sheet = SheetDocument.parse(resource.getContentAsString(StandardCharsets.UTF_8));
            JsonNode calibration = json.readTree(new ClassPathResource("competency/v1/calibration/" + sheet.code() + ".json").getInputStream());
            double previous = -1;
            for (JsonNode answer : calibration.path("answers")) {
                int[] scores = new int[2];
                for (int run = 0; run < 2; run++) {
                    ObjectNode provider = json.createObjectNode();
                    ObjectNode task = provider.putObject("task");
                    JsonNode source = calibration.path("task");
                    task.put("title", source.path("title").asText()).put("situation", source.path("situation").asText())
                            .put("material", source.path("material").asText("")).put("question", source.path("question").asText());
                    provider.set("rubric", calibration.path("rubric"));
                    provider.put("sheetExcerpt", sheet.gradingExcerpt()).put("answer", answer.path("text").asText())
                            .put("followupAllowed", false).put("pressureStyle", false);
                    AiModelClient.Completion completion = model.complete(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-6-luna"),
                            prompts.request(LoopPrompts.Role.GRADING, provider));
                    input += completion.tokens().input();
                    cached += completion.tokens().cachedInput();
                    output += completion.tokens().output();
                    scores[run] = TaskValidation.grading(json, completion.result(), calibration.path("rubric")).score();
                }
                String label = answer.path("label").asText();
                int min = answer.path("expectedMin").asInt(), max = answer.path("expectedMax").asInt();
                double average = (scores[0] + scores[1]) / 2.0;
                report.addObject().put("code", sheet.code()).put("label", label).put("expectedMin", min).put("expectedMax", max)
                        .put("run1", scores[0]).put("run2", scores[1]);
                for (int score : scores) if (score < min || score > max) failures.add(sheet.code() + " " + label + " score " + score + " outside " + min + "-" + max);
                if (Math.abs(scores[0] - scores[1]) > 10) failures.add(sheet.code() + " " + label + " unstable " + scores[0] + " vs " + scores[1]);
                if (average <= previous) failures.add(sheet.code() + " " + label + " is not above the previous label");
                previous = average;
            }
        }
        double usd = ((input - cached) * 0.10 + cached * 0.01 + output * 0.50) / 1_000_000;
        ObjectNode summary = json.createObjectNode().put("inputTokens", input).put("cachedInputTokens", cached).put("outputTokens", output)
                .put("estimatedUsd", usd).put("estimatedKrwAt1500", usd * 1500);
        summary.set("results", report);
        summary.set("failures", json.valueToTree(failures));
        Path out = Path.of("target", "grading-calibration.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
        System.out.println("CALIBRATION " + json.writeValueAsString(summary));
        assertThat(failures).isEmpty();
    }
}
