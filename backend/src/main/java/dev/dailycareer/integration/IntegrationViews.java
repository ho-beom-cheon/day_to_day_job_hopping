package dev.dailycareer.integration;

import java.time.OffsetDateTime;
import java.util.List;

public final class IntegrationViews {
    private IntegrationViews() {}
    public record TimedReminder(Boolean enabled,String time) {}
    public record TestReminder(Boolean enabled,Integer beforeMinutes) {}
    public record WeeklyReminder(Boolean enabled,String dayOfWeek,String time) {}
    public record NotificationSettings(TimedReminder studyReminder,TestReminder testReminder,TimedReminder streakReminder,
            WeeklyReminder weeklyReport,Boolean pushEnabled,Boolean scheduledRemindersEnabled,OffsetDateTime updatedAt,Integer revision) {}
    public record NotificationSettingsPatch(TimedReminder studyReminder,TestReminder testReminder,TimedReminder streakReminder,
            WeeklyReminder weeklyReport,Boolean pushEnabled,Boolean scheduledRemindersEnabled) {}
    public record PushConfig(Boolean enabled,String publicKey) {}
    public record NotionConnection(Boolean connected,String status,String workspaceName,String workspaceId,String notionVersion,
            Integer connectionRevision) {}
    public record SlackConnection(Boolean configured,String channelName,Boolean enabled,List<String> eventTypes,
            OffsetDateTime updatedAt,Integer revision) {}
    public record SlackPatch(Boolean enabled,List<String> eventTypes) {}
}
