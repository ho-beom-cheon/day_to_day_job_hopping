package dev.dailycareer.progress;

import dev.dailycareer.common.json.JsonId;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProgressViews {
    private ProgressViews() {}
    public record Overall(@JsonId Long curriculumId,Integer totalLearningDays,Integer completedDays,Integer totalRequiredSessions,
            Integer completedRequiredSessions,Double completionRate,Double adherenceRate,Double masteryRate,Integer totalStudyMinutes,Integer completedEstimatedMinutes,
            Integer firstAttemptProblemCount,Integer firstAttemptCorrectCount,Double firstAttemptAccuracy,Integer streakDays,
            Integer longestStreakDays,Integer extraLearningCount,Integer completedExtraLearningCount,
            Integer extraPracticeAttemptCount,Integer extraPracticeCorrectCount,
            Integer aiPracticeAttemptCount,Integer aiPracticeCorrectCount,OffsetDateTime asOf) {}
    public record Category(String category,Integer totalSessionCount,Integer completedSessionCount,Double progressRate,Double masteryRate,
            Integer firstAttemptProblemCount,Integer firstAttemptCorrectCount,Double firstAttemptAccuracy) {}
    public record DailyProgress(LocalDate date,String status,Integer plannedMinutes,Integer actualMinutes,
            Integer completedSessionCount,Integer totalSessionCount,Double progressRate,Integer attemptCount,Integer correctCount,
            Double attemptAccuracy,Integer firstAttemptProblemCount,Integer firstAttemptCorrectCount,Double firstAttemptAccuracy) {}
    public record PeriodProgress(@JsonId Long curriculumId,LocalDate from,LocalDate to,Integer plannedMinutes,Integer actualMinutes,
            Integer totalSessionCount,Integer completedSessionCount,Double progressRate,Double adherenceRate,Double masteryRate,
            Integer firstAttemptProblemCount,Integer firstAttemptCorrectCount,Double firstAttemptAccuracy,Integer studyDays,
            Integer restDays,List<DailyProgress> dailyStats,OffsetDateTime asOf) {}
    public record CalendarDay(LocalDate date,@JsonId Long learningDayId,Integer dayNo,String kind,String status,String title,
            Double progressRate,String restReason,List<String> testIds,Boolean overdue) {}
    public record LearningCalendar(@JsonId Long curriculumId,Integer year,Integer month,Integer scheduleRevision,List<CalendarDay> days) {}
    public record Evaluation(@JsonId Long evaluationId,@JsonId Long curriculumId,Integer monthNo,Integer reportVersion,LocalDate from,
            LocalDate to,String dataVersion,Integer requiredSessionCount,Integer completedSessionCount,Double progressRate,Double adherenceRate,
            Double masteryRate,Integer studyMinutes,Integer firstAttemptProblemCount,Integer firstAttemptCorrectCount,Double firstAttemptAccuracy,
            Double monthlyTestScore,String assessmentStatus,List<Category> categoryProgress,OffsetDateTime createdAt) {}
    public record EvaluationPage(List<Evaluation> content,Integer page,Integer size,Integer totalElements,Integer totalPages) {}
    public record EvaluationCreate(@JsonId Long curriculumId,Integer monthNo) {}
}
