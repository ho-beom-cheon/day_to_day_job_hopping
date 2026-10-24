package dev.dailycareer.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.progress.ProgressService;
import dev.dailycareer.progress.ProgressViews.PeriodProgress;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScheduledReminderWorker {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final ProgressService progress;

    public ScheduledReminderWorker(JdbcTemplate jdbc, ObjectMapper json, Clock clock, ProgressService progress) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.progress = progress;
    }

    @Scheduled(fixedDelayString = "${daily-career.reminders.poll-delay-ms:60000}")
    @Transactional
    public void poll() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(SEOUL).toLocalDate();
        for (Map<String, Object> setting : jdbc.queryForList("""
                select user_id,notification_settings::text,notification_revision
                from daily_career.user_setting where scheduled_reminders_enabled
                order by user_id
                """)) {
            long user = ((Number) setting.get("user_id")).longValue();
            long revision = ((Number) setting.get("notification_revision")).longValue();
            JsonNode preferences;
            try { preferences = json.readTree(setting.get("notification_settings").toString()); }
            catch (Exception malformed) { throw new IllegalStateException("Invalid notification settings", malformed); }

            if (due(preferences.path("studyReminder"), today, now) && hasOpenStudyDay(user, today))
                enqueue(user, revision, "STUDY_REMINDER", today, today.atTime(LocalTime.parse(preferences.path("studyReminder").path("time").asText())).atZone(SEOUL).toInstant(), null);
            if (due(preferences.path("streakReminder"), today, now) && hasOpenStudyDay(user, today) && hasCompletedPreviousStudyDay(user, today))
                enqueue(user, revision, "STREAK_REMINDER", today, today.atTime(LocalTime.parse(preferences.path("streakReminder").path("time").asText())).atZone(SEOUL).toInstant(), null);
            JsonNode weekly = preferences.path("weeklyReport");
            if (weekly.path("enabled").asBoolean() && today.getDayOfWeek().name().equals(weekly.path("dayOfWeek").asText())
                    && due(weekly, today, now)) {
                Long course = activeCourse(user);
                if (course != null) {
                    LocalDate from = today.minusDays(6);
                    PeriodProgress period = progress.periodProgress(user, course, from, today);
                    ObjectNode report = json.createObjectNode()
                            .put("from", from.toString()).put("to", today.toString())
                            .put("totalSessions", period.totalSessionCount())
                            .put("completedSessions", period.completedSessionCount())
                            .put("studyMinutes", period.actualMinutes())
                            .put("firstAttempts", period.firstAttemptProblemCount())
                            .put("firstCorrect", period.firstAttemptCorrectCount());
                    enqueue(user, revision, "WEEKLY_REPORT", today,
                            today.atTime(LocalTime.parse(weekly.path("time").asText())).atZone(SEOUL).toInstant(), null, report);
                }
            }

            JsonNode test = preferences.path("testReminder");
            if (test.path("enabled").asBoolean()) {
                int beforeMinutes = test.path("beforeMinutes").asInt();
                if (beforeMinutes >= 1 && beforeMinutes <= 1440) scheduleTests(user, revision, now, beforeMinutes);
            }
        }
    }

    static boolean due(JsonNode setting, LocalDate date, Instant now) {
        if (!setting.path("enabled").asBoolean()) return false;
        try {
            Instant scheduled = date.atTime(LocalTime.parse(setting.path("time").asText())).atZone(SEOUL).toInstant();
            return !now.isBefore(scheduled) && now.isBefore(scheduled.plusSeconds(300));
        } catch (RuntimeException invalid) { return false; }
    }

    private boolean hasOpenStudyDay(long user, LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from daily_career.learning_day d
                  join daily_career.user_curriculum c on c.id=d.user_curriculum_id
                  join daily_career.learning_day_item i on i.learning_day_id=d.id
                  where d.user_id=? and d.local_date=? and d.day_type='STUDY' and c.status='ACTIVE'
                    and i.required and i.activity_type<>'TEST' and i.content_status='READY'
                    and i.status not in ('COMPLETED','SKIPPED'))
                """, Boolean.class, user, date));
    }

    private boolean hasCompletedPreviousStudyDay(long user, LocalDate today) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from daily_career.learning_day d
                  join daily_career.user_curriculum c on c.id=d.user_curriculum_id
                  where d.user_id=? and d.local_date=(select max(previous.local_date)
                    from daily_career.learning_day previous where previous.user_curriculum_id=d.user_curriculum_id
                      and previous.day_type='STUDY' and previous.local_date<?)
                    and d.local_date>=? and c.status='ACTIVE' and exists(select 1 from daily_career.learning_day_item i
                      where i.learning_day_id=d.id and i.required)
                    and not exists(select 1 from daily_career.learning_day_item i
                      where i.learning_day_id=d.id and i.required and i.status<>'COMPLETED'))
                """, Boolean.class, user, today, today.minusDays(7)));
    }

    private Long activeCourse(long user) {
        var courses = jdbc.query("""
                select id from daily_career.user_curriculum
                where user_id=? and status='ACTIVE'
                order by created_at desc,id desc limit 1
                """, (rs, row) -> rs.getLong(1), user);
        return courses.isEmpty() ? null : courses.getFirst();
    }

    private void scheduleTests(long user, long revision, Instant now, int beforeMinutes) {
        for (Map<String, Object> test : jdbc.queryForList("""
                select t.id test_id,d.local_date,
                  (d.local_date::timestamp at time zone 'Asia/Seoul') - (? * interval '1 minute') due_at
                from daily_career.learning_day_item i
                join daily_career.learning_day d on d.id=i.learning_day_id
                join daily_career.user_curriculum c on c.id=i.user_curriculum_id
                join daily_career.test_version v on v.id=i.test_version_id
                join daily_career.test t on t.id=v.test_id
                where i.user_id=? and i.activity_type='TEST' and i.status='NOT_STARTED'
                  and i.content_status='READY' and c.status in ('PLANNED','ACTIVE') and v.status='PUBLISHED' and t.enabled
                  and (t.owner_user_id is null or t.owner_user_id=?)
                  and (d.local_date::timestamp at time zone 'Asia/Seoul') - (? * interval '1 minute') <= ?
                  and (d.local_date::timestamp at time zone 'Asia/Seoul') - (? * interval '1 minute') > ?
                """, beforeMinutes, user, user, beforeMinutes, Timestamp.from(now), beforeMinutes, Timestamp.from(now.minusSeconds(300)))) {
            LocalDate date = ((Date) test.get("local_date")).toLocalDate();
            Instant dueAt = ((Timestamp) test.get("due_at")).toInstant();
            enqueue(user, revision, "TEST_REMINDER", date, dueAt, ((Number) test.get("test_id")).longValue());
        }
    }

    void enqueue(long user, long revision, String type, LocalDate date, Instant dueAt, Long testId) {
        enqueue(user, revision, type, date, dueAt, testId, null);
    }

    private void enqueue(long user, long revision, String type, LocalDate date, Instant dueAt, Long testId, JsonNode report) {
        String key = "SCHEDULED:" + type + ":" + user + ":" + date + (testId == null ? "" : ":" + testId);
        ObjectNode payload = json.createObjectNode().put("date", date.toString()).put("dueAt", dueAt.toString());
        if (report != null) payload.set("report", report);
        jdbc.update("""
                insert into daily_career.outbox_event(user_id,event_key,event_type,aggregate_type,
                    aggregate_id,aggregate_revision,payload,available_at)
                select ?,?,?,'USER_SETTING',?,?,?::jsonb,?
                from daily_career.user_setting s
                where s.user_id=? and s.scheduled_reminders_enabled
                  and s.scheduled_reminders_enabled_at<=?
                on conflict(event_key) do nothing
                """, user, key, "SCHEDULED_" + type, user, revision, payload.toString(), Timestamp.from(dueAt), user, Timestamp.from(dueAt));
    }
}
