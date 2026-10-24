package dev.dailycareer.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.integration.IntegrationService;
import dev.dailycareer.integration.IntegrationViews.NotificationSettingsPatch;
import dev.dailycareer.integration.slack.SlackSettings;
import dev.dailycareer.notification.push.WebPushSettings;
import dev.dailycareer.progress.ProgressService;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.user.UserAccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Testcontainers
class ScheduledReminderIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6")
            .withUsername("daily_career");
    static SingleConnectionDataSource dataSource;
    static JdbcTemplate jdbc;

    @BeforeAll static void setUp() {
        dataSource = new SingleConnectionDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        Flyway.configure().dataSource(dataSource).defaultSchema("public").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll static void close() { if (dataSource != null) dataSource.destroy(); }

    @Test void consentGatesDedupesAndCancelsScheduledNotificationsWithoutExternalDelivery() {
        long user = jdbc.queryForObject("insert into daily_career.app_user(display_name) values ('reminder-test') returning id", Long.class);
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", user);
        var json = new ObjectMapper();
        var worker = new ScheduledReminderWorker(jdbc, json, Clock.system(ZoneId.of("Asia/Seoul")), mock(ProgressService.class));
        Instant dueAt = Instant.now().minusSeconds(1);
        LocalDate date = dueAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();

        worker.enqueue(user, 0, "STUDY_REMINDER", date, dueAt, null);
        assertThat(count("outbox_event", user)).isZero();

        var settings = new IntegrationService(jdbc, mock(UserAccountService.class), json,
                new WebPushSettings("", "", "", "", "", ""),
                new SlackSettings("", "", "", ""));
        assertThat(settings.update(user, patch(true), 0).scheduledRemindersEnabled()).isTrue();
        worker.enqueue(user, 1, "STUDY_REMINDER", date, dueAt, null);
        assertThat(count("outbox_event", user)).isZero(); // Consent cannot backfill an earlier slot.

        jdbc.update("update daily_career.user_setting set scheduled_reminders_enabled_at=clock_timestamp()-interval '1 minute' where user_id=?", user);
        worker.enqueue(user, 1, "STUDY_REMINDER", date, dueAt, null);
        worker.enqueue(user, 1, "STUDY_REMINDER", date, dueAt, null);
        assertThat(count("outbox_event", user)).isOne();

        var dispatcher = new OutboxDispatcher(jdbc, json, new DataSourceTransactionManager(dataSource));
        dispatcher.poll();
        assertThat(count("notification", user)).isOne();
        assertThat(jdbc.queryForObject("select event_type from daily_career.notification where user_id=?", String.class, user))
                .isEqualTo("STUDY_REMINDER");

        worker.enqueue(user, 1, "TEST_REMINDER", date, dueAt, 10L);
        worker.enqueue(user, 1, "STREAK_REMINDER", date, dueAt, null);
        assertThat(count("outbox_event", user)).isEqualTo(3);
        jdbc.update("update daily_career.outbox_event set available_at=clock_timestamp()+interval '20 minutes' where user_id=? and event_type='SCHEDULED_STREAK_REMINDER'", user);
        assertThat(settings.update(user, patch(false), 1).scheduledRemindersEnabled()).isFalse();
        dispatcher.poll();
        assertThat(count("notification", user)).isOne();
        assertThat(settings.update(user, patch(true), 2).scheduledRemindersEnabled()).isTrue();
        jdbc.update("update daily_career.outbox_event set available_at=clock_timestamp()-interval '1 second' where user_id=? and event_type='SCHEDULED_STREAK_REMINDER'", user);
        dispatcher.poll(); // A fresh consent cannot release a reminder queued for the earlier consent period.
        assertThat(count("notification", user)).isOne();
        assertThat(jdbc.queryForObject("select canceled_at is null from daily_career.notification where user_id=?", Boolean.class, user)).isTrue();
    }

    @Test void weeklyNotificationContainsCurrentCourseSnapshotAndDedupes() {
        long user = jdbc.queryForObject("insert into daily_career.app_user(display_name) values ('weekly-report-test') returning id", Long.class);
        jdbc.update("insert into daily_career.user_setting(user_id,scheduled_reminders_enabled,scheduled_reminders_enabled_at) values (?,true,?::timestamptz)",
                user, "2026-09-19T00:00:00Z");
        long curriculum = jdbc.queryForObject("insert into daily_career.curriculum(code,title) values (?, 'Weekly report test') returning id",
                Long.class, "WEEKLY-" + UUID.randomUUID());
        long version = jdbc.queryForObject("insert into daily_career.curriculum_version(curriculum_id,version_no,status,published_at) values (?,1,'PUBLISHED',clock_timestamp()) returning id", Long.class, curriculum);
        long course = jdbc.queryForObject("""
                insert into daily_career.user_curriculum(user_id,curriculum_version_id,start_date,target_end_date,projected_end_date,status)
                values (?,?,'2026-09-14','2026-10-14','2026-10-14','ACTIVE') returning id
                """, Long.class, user, version);
        long day = jdbc.queryForObject("insert into daily_career.learning_day(user_id,user_curriculum_id,local_date) values (?,?,'2026-09-19') returning id",
                Long.class, user, course);
        jdbc.update("""
                insert into daily_career.learning_day_item(user_id,user_curriculum_id,curriculum_version_id,learning_day_id,order_no,activity_type,status,completed_at)
                values (?,?,?,?,1,'LEARN','COMPLETED','2026-09-19T10:00:00Z')
                """, user, course, version, day);
        jdbc.update("""
                insert into daily_career.learning_day_item(user_id,user_curriculum_id,curriculum_version_id,learning_day_id,order_no,activity_type,status)
                values (?,?,?,?,2,'PRACTICE','NOT_STARTED')
                """, user, course, version, day);

        var json = new ObjectMapper();
        Clock clock = Clock.fixed(Instant.parse("2026-09-20T11:00:01Z"), ZoneId.of("Asia/Seoul"));
        var progress = new ProgressService(jdbc, mock(UserAccountService.class), mock(IdempotencyService.class), json, clock);
        var worker = new ScheduledReminderWorker(jdbc, json, clock, progress);
        worker.poll();
        worker.poll();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.outbox_event where user_id=? and event_type='SCHEDULED_WEEKLY_REPORT'", Integer.class, user)).isOne();

        var dispatcher = new OutboxDispatcher(jdbc, json, new DataSourceTransactionManager(dataSource));
        dispatcher.poll();
        String body = jdbc.queryForObject("select body from daily_career.notification where user_id=? and event_type='WEEKLY_REPORT'", String.class, user);
        assertThat(body).contains("2026-09-14 ~ 2026-09-20", "필수 학습 1/2개", "학습 0분", "첫 풀이 기록 없음");
    }

    private static NotificationSettingsPatch patch(boolean enabled) {
        return new NotificationSettingsPatch(null, null, null, null, null, enabled);
    }

    private static int count(String table, long user) {
        return jdbc.queryForObject("select count(*) from daily_career." + table + " where user_id=?", Integer.class, user);
    }
}
