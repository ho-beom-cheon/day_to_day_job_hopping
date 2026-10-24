package dev.dailycareer.user;

import java.time.OffsetDateTime;
import java.util.List;

public final class SettingsViews {
    private SettingsViews() {}

    public record StudyPolicy(Integer dailyStudyMinutes,Integer restDaysPerWeek,List<String> restWeekdays) {}
    public record Settings(StudyPolicy studyPolicy,String weekdayStudyStartTime,String weekendStudyStartTime,
                           String theme,String language,String timeZone,Integer revision,OffsetDateTime updatedAt) {}
    public record SettingsPatch(StudyPolicy studyPolicy,String weekdayStudyStartTime,String weekendStudyStartTime,String theme) {}
}
