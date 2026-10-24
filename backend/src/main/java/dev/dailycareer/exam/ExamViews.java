package dev.dailycareer.exam;

import dev.dailycareer.common.json.JsonId;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class ExamViews {
    private ExamViews() {}
    public record Option(Integer optionNo,String text) {}
    public record Exam(@JsonId Long testId,@JsonId Long curriculumId,String title,String description,String type,
                       Integer problemCount,Integer timeLimitMinutes,BigDecimal passScore,LocalDate scheduledDate,
                       OffsetDateTime availableFrom,OffsetDateTime availableUntil,Integer attemptCount,Integer maxAttemptCount,
                       @JsonId Long activeAttemptId,String questionSetVersion) {}
    public record ExamPage(List<Exam> content,Integer page,Integer size,Integer totalElements,Integer totalPages) {}
    public record Problem(@JsonId Long problemId,Integer sequence,String category,String type,String question,List<Option> options,
                          Integer points,String answer,Integer answerRevision,OffsetDateTime savedAt) {}
    public record Attempt(@JsonId Long attemptId,@JsonId Long testId,Integer attemptNo,String status,String gradingStatus,
                          String gradingErrorCode,String submissionReason,OffsetDateTime startedAt,OffsetDateTime expiresAt,
                          OffsetDateTime submittedAt,OffsetDateTime completedAt,Boolean acceptingAnswers,Boolean submissionDue,
                          Long remainingSeconds,Integer answeredCount,Integer totalProblemCount,String questionSetVersion,List<Problem> problems) {}
    public record SavedAnswer(@JsonId Long attemptId,@JsonId Long problemId,String answer,Integer answerRevision,OffsetDateTime savedAt) {}
    public record SubmitResult(@JsonId Long attemptId,String status,String gradingStatus,String submissionReason,
                               OffsetDateTime submittedAt,String resultPath) {}
    public record CategoryScore(String category,Integer correctCount,Integer problemCount,Integer earnedPoints,Integer totalPoints,BigDecimal score) {}
    public record ResultProblem(@JsonId Long problemId,String category,String question,String myAnswer,String correctAnswer,
                                Boolean correct,Integer points,Integer earnedPoints,String explanation) {}
    public record Result(@JsonId Long attemptId,@JsonId Long testId,String title,BigDecimal score,BigDecimal passScore,Boolean passed,
                         Integer correctCount,Integer wrongCount,Integer unansweredCount,Integer totalCount,Integer earnedPoints,
                         Integer totalPoints,OffsetDateTime submittedAt,OffsetDateTime completedAt,List<CategoryScore> categoryScores,
                         List<ResultProblem> problems) {}
    public record AttemptSummary(@JsonId Long attemptId,@JsonId Long testId,String title,Integer attemptNo,String status,
                                 BigDecimal score,OffsetDateTime startedAt,OffsetDateTime submittedAt,OffsetDateTime completedAt) {}
    public record AttemptPage(List<AttemptSummary> content,Integer page,Integer size,Integer totalElements,Integer totalPages) {}
}
