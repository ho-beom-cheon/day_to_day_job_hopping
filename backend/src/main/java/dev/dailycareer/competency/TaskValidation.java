package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Server checks on AI output (design §6.2). Failures throw IllegalArgumentException so the worker records a quality rejection. */
final class TaskValidation {
    private static final Pattern CODE_WRITING = Pattern.compile("코드를\\s*(직접\\s*)?(작성|구현)|구현하(세요|시오|라)|작성하(세요|시오)[^.]*코드");
    private TaskValidation() {}

    record GeneratedTask(ObjectNode task, ArrayNode rubric, ArrayNode reserveFollowups, String fingerprint) {}
    record Grading(String status, int score, ArrayNode criteria) {}

    static List<GeneratedTask> tasks(ObjectMapper json, JsonNode result, String format, List<String> sheetCriteria, Set<String> knownFingerprints) {
        var valid = new ArrayList<GeneratedTask>();
        var seen = new HashSet<>(knownFingerprints);
        for (JsonNode item : result.path("tasks")) {
            try {
                GeneratedTask task = task(json, item, format, sheetCriteria);
                if (seen.add(task.fingerprint())) valid.add(task);
            } catch (IllegalArgumentException rejected) {
                // One malformed task does not discard the others in the same batch.
            }
        }
        if (valid.isEmpty()) throw new IllegalArgumentException("No generated task passed validation");
        return valid;
    }

    static GeneratedTask task(ObjectMapper json, JsonNode item, String format, List<String> sheetCriteria) {
        String title = text(item, "title"), situation = text(item, "situation"), question = text(item, "question");
        String material = item.path("material").asText("");
        if (!"INTERVIEW".equals(format) && material.isBlank()) throw new IllegalArgumentException("Material required");
        if (CODE_WRITING.matcher(question).find()) throw new IllegalArgumentException("Code-writing tasks are excluded");
        int minutes = item.path("estimatedMinutes").asInt(-1);
        if (minutes < 10 || minutes > 35) throw new IllegalArgumentException("Estimated minutes out of range");
        ArrayNode rubric = rubric(item.path("rubric"), sheetCriteria);
        ArrayNode reserve = json.createArrayNode();
        for (JsonNode followup : item.path("reserveFollowups")) {
            if (!followup.path("question").asText("").isBlank()) reserve.add(followup);
        }
        if (reserve.isEmpty()) throw new IllegalArgumentException("Reserve follow-up required");
        ObjectNode task = json.createObjectNode().put("title", title).put("situation", situation).put("material", material)
                .put("question", question).put("estimatedMinutes", minutes);
        task.set("modelAnswerPoints", item.path("modelAnswerPoints"));
        return new GeneratedTask(task, rubric, reserve, CompetencyCatalog.sha256(normalize(title) + "|" + normalize(question)));
    }

    static ArrayNode rubric(JsonNode rubric, List<String> sheetCriteria) {
        if (!rubric.isArray() || rubric.size() < 3 || rubric.size() > 5) throw new IllegalArgumentException("Rubric needs 3-5 criteria");
        int total = 0;
        var ids = new HashSet<String>();
        for (JsonNode criterion : rubric) {
            String id = text(criterion, "criterionId");
            int points = criterion.path("points").asInt(-1);
            if (!ids.add(id) || points <= 0 || text(criterion, "description").isBlank()) throw new IllegalArgumentException("Invalid rubric criterion");
            if (!criterion.path("sheetCriteria").isArray() || criterion.path("sheetCriteria").isEmpty()) throw new IllegalArgumentException("Rubric must cite the sheet");
            for (JsonNode reference : criterion.path("sheetCriteria")) {
                if (!sheetCriteria.contains(reference.asText())) throw new IllegalArgumentException("Rubric cites a criterion outside the sheet");
            }
            total += points;
        }
        if (total != 100) throw new IllegalArgumentException("Rubric points must total 100");
        return (ArrayNode) rubric;
    }

    static Grading grading(ObjectMapper json, JsonNode result, JsonNode rubric) {
        String status = result.path("status").asText();
        if (!"GRADED".equals(status) && !"HOLD".equals(status)) throw new IllegalArgumentException("Unknown grading status");
        if ("HOLD".equals(status)) {
            if (result.path("holdReason").asText("").isBlank()) throw new IllegalArgumentException("Hold needs a reason");
            return new Grading(status, 0, json.createArrayNode());
        }
        ArrayNode criteria = json.createArrayNode();
        int score = 0;
        var graded = new HashSet<String>();
        for (JsonNode item : result.path("criteria")) {
            String id = item.path("criterionId").asText();
            JsonNode criterion = find(rubric, id);
            int earned = item.path("earnedPoints").asInt(-1);
            if (criterion == null || !graded.add(id) || earned < 0 || earned > criterion.path("points").asInt())
                throw new IllegalArgumentException("Grading does not match the rubric");
            score += earned;
            criteria.add(json.createObjectNode().put("criterionId", id).put("description", criterion.path("description").asText())
                    .put("points", criterion.path("points").asInt()).put("earnedPoints", earned).put("reason", item.path("reason").asText("")));
        }
        if (graded.size() != rubric.size()) throw new IllegalArgumentException("Every rubric criterion must be graded");
        return new Grading(status, score, criteria);
    }

    /** Follow-up corrections stay within ±15 of the main answer score in total (design §4.2). */
    static int delta(int requested, int cumulative) {
        int clamped = Math.max(-15, Math.min(15, requested));
        return Math.max(-15 - cumulative, Math.min(15 - cumulative, clamped));
    }

    static JsonNode find(JsonNode rubric, String id) {
        for (JsonNode criterion : rubric) if (id.equals(criterion.path("criterionId").asText())) return criterion;
        return null;
    }

    private static String text(JsonNode node, String key) {
        String value = node.path(key).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value.strip();
    }

    private static String normalize(String value) { return value.replaceAll("\\s+", "").toLowerCase(); }
}
