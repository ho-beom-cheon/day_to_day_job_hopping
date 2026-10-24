package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dailycareer.common.json.JsonId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class DailyTaskViews {
    private DailyTaskViews() {}

    public record CompetencyRef(String code, String title, String areaTitle, String priority) {}
    public record Week(int target, int completed) {}
    public record AiState(boolean enabled, BigDecimal limitKrw, BigDecimal remainingKrw) {}
    public record TaskSummary(@JsonId Long taskId, String status, String format, CompetencyRef competency, String planReason,
                              String title, Integer estimatedMinutes, String failureCode) {}
    public record Today(TaskSummary task, Week week, AiState ai, List<CardSummary> recentCards) {}
    public record CardSummary(@JsonId Long taskId, String competencyCode, String title, String interviewLine, OffsetDateTime updatedAt) {}

    /** modelAnswerPoints stays null until the main answer is submitted. */
    public record Material(String title, String situation, String material, String question, int estimatedMinutes, List<String> modelAnswerPoints) {}
    public record SheetRef(int version, String reviewLabel, String markdown) {}
    public record Turn(int turnNo, String kind, String question, String answer, boolean submitted, boolean pending,
                       String feedback, List<String> checkPoints, List<String> checkPointsMet, Integer scoreDelta) {}
    public record Criterion(String criterionId, String description, int points, Integer earnedPoints, String reason) {}
    public record Result(String status, Integer baseScore, Integer finalScore, String feedback, List<String> missingPoints,
                         String improvedAnswer, String holdReason, boolean scoreExcluded) {}
    public record Card(String title, String body, String interviewLine, String reviewPrompt) {}
    public record Task(@JsonId Long taskId, String status, String format, String timeMode, String gradingMode, int followupLimit,
                       CompetencyRef competency, String planReason, String failureCode, Material task, SheetRef sheet,
                       List<Turn> turns, List<Criterion> rubric, Result result, JsonNode cardDraft, Card card,
                       Integer usefulness, boolean pending, int revision) {}

    public record AnswerRequest(String answer, Boolean sheetOpened) {}
    public record SelfGradeRequest(List<String> metCriteria) {}
    public record CardRequest(String title, String body, String interviewLine, String reviewPrompt) {}
    public record FeedbackRequest(Integer usefulness, String note) {}
    public record ReportRequest(String target, String reason, String note, Boolean excludeScore) {}
}
