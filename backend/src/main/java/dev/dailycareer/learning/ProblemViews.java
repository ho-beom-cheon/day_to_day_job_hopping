package dev.dailycareer.learning;

import dev.dailycareer.common.json.JsonId;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProblemViews {
    private ProblemViews() {}
    public record Option(Integer optionNo,String text) {}
    public record Problem(
            @JsonId Long problemId,@JsonId Long contentId,String category,String type,String difficulty,
            String question,List<Option> options,String source,String reviewStatus,Integer previousAttemptCount,Boolean answered) {}
    public record Attempt(
            @JsonId Long attemptId,@JsonId Long problemId,@JsonId Long contentId,String answer,Boolean correct,
            String correctAnswer,String explanation,Integer attemptNo,@JsonId Long wrongAnswerId,
            Boolean wrongAnswerCreated,OffsetDateTime submittedAt) {}
    public record Report(@JsonId Long reportId,@JsonId Long problemId,String status,OffsetDateTime createdAt) {}
    public record WrongSummary(@JsonId Long wrongAnswerId,@JsonId Long problemId,String category,String question,
                               Integer wrongCount,String status,OffsetDateTime lastWrongAt,Integer reviewCount) {}
    public record WrongAnswer(@JsonId Long wrongAnswerId,@JsonId Long problemId,String category,String question,
                              Integer wrongCount,String status,OffsetDateTime lastWrongAt,Integer reviewCount,
                              String difficulty,String myLastAnswer,String correctAnswer,String explanation,
                              OffsetDateTime firstWrongAt,OffsetDateTime resolvedAt,String resolutionType,String note,Integer revision) {}
    public record WrongPage(List<WrongSummary> content,Integer page,Integer size,Integer totalElements,Integer totalPages) {}
}
