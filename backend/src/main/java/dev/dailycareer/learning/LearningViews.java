package dev.dailycareer.learning;

import dev.dailycareer.common.json.JsonId;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Public DTOs follow OpenAPI v1.2.1, with IDs distinct from numeric revisions. */
public final class LearningViews {
    private LearningViews() {}
    public record CurriculumTemplate(
            @JsonId Long templateId,
            String templateVersion,
            String title,
            String description,
            Integer durationMonths,
            Integer totalWeeks,
            Integer totalLearningDays,
            Integer totalRequiredSessions,
            OffsetDateTime publishedAt) {}
    public record Curriculum(
            @JsonId Long curriculumId,
            @JsonId Long templateId,
            String templateVersion,
            String title,
            LocalDate startDate,
            LocalDate nominalEndDate,
            LocalDate endDate,
            Integer totalWeeks,
            Integer totalLearningDays,
            Integer totalRequiredSessions,
            Integer completedRequiredSessions,
            Integer currentWeek,
            Integer currentDayNo,
            String status,
            Double completionRate,
            StudyPolicy studyPolicy,
            Integer scheduleRevision,
            OffsetDateTime createdAt) {}
    public record StudyPolicy(
            Integer dailyStudyMinutes,
            Integer restDaysPerWeek,
            List<String> restWeekdays) {}
    public record CurriculumMonth(
            Integer monthNo,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            Integer requiredSessionCount,
            Integer completedSessionCount,
            Double progressRate,
            List<Integer> weekNumbers) {}
    public record WeekSummary(
            Integer weekNo,
            Integer monthNo,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            Integer requiredSessionCount,
            Integer completedSessionCount,
            Double progressRate) {}
    public record WeekDetail(
            Integer weekNo,
            Integer monthNo,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            Integer requiredSessionCount,
            Integer completedSessionCount,
            Double progressRate,
            List<String> learningGoals,
            List<LearningDay> learningDays) {}
    public record LearningDay(
            @JsonId Long learningDayId,
            @JsonId Long curriculumId,
            LocalDate date,
            Integer dayNo,
            Integer weekNo,
            Integer monthNo,
            String kind,
            String title,
            String description,
            String status,
            Integer estimatedMinutes,
            Integer actualMinutes,
            Double progressRate,
            Integer completedSessionCount,
            Integer totalSessionCount,
            Integer scheduleRevision,
            List<SessionSummary> sessions) {}
    public record Session(
            @JsonId Long sessionId,
            @JsonId Long learningDayId,
            @JsonId Long curriculumId,
            LocalDate scheduledDate,
            String category,
            String title,
            String description,
            Integer sequence,
            Boolean required,
            String status,
            Integer estimatedMinutes,
            Integer actualMinutes,
            String timeSource,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            Integer requiredContentCount,
            Integer completedRequiredContentCount,
            Integer revision) {}
    public record SessionSummary(
            @JsonId Long sessionId,
            String category,
            String title,
            Integer sequence,
            Boolean required,
            Integer estimatedMinutes,
            String status) {}
    public record SessionCompleteResult(
            Session session,
            DayProgress dayProgress) {}
    public record DayProgress(
            @JsonId Long learningDayId,
            Double progressRate,
            Integer completedSessionCount,
            Integer totalSessionCount,
            Boolean dayCompleted) {}
    public record ContentSummary(
            @JsonId Long contentId,
            @JsonId Long contentVersionId,
            String title,
            String type,
            Integer sequence,
            Boolean required,
            String status,
            String completionRule) {}
    public record LearningContent(
            @JsonId Long contentId,
            @JsonId Long contentVersionId,
            String title,
            String type,
            Integer sequence,
            Boolean required,
            String status,
            String completionRule,
            @JsonId Long sessionId,
            String learningObjective,
            String body,
            List<String> keyPoints,
            List<String> interviewPoints,
            List<ProblemSummary> problems,
            OffsetDateTime completedAt) {}
    public record ProblemSummary(
            @JsonId Long problemId,
            String title,
            String type) {}
    public record ContentCompleteResult(
            @JsonId Long contentId,
            String status,
            OffsetDateTime completedAt,
            Integer requiredContentCount,
            Integer completedRequiredContentCount) {}
}
