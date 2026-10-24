package dev.dailycareer.dashboard;

import dev.dailycareer.exam.ExamViews;
import dev.dailycareer.learning.LearningViews;
import dev.dailycareer.progress.ProgressViews;
import dev.dailycareer.user.UserView;
import dev.dailycareer.common.json.JsonId;
import java.time.LocalDate;
import java.util.List;

public final class DashboardViews {
    private DashboardViews() {}
    public record CharacterState(String assetKey,String mood,Boolean rewardEnabled) {}
    public record RecentSession(@JsonId Long sessionId,String title,String category,String status,LocalDate scheduledDate) {}
    public record Dashboard(UserView user,LearningViews.Curriculum curriculum,LearningViews.LearningDay today,
            ProgressViews.Overall progress,ProgressViews.PeriodProgress weeklyProgress,Integer unresolvedWrongAnswerCount,
            ExamViews.Exam nextTest,CharacterState character,Boolean onboardingRequired,List<RecentSession> recentSessions) {}
}
