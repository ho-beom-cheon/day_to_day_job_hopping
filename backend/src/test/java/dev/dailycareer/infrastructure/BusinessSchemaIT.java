package dev.dailycareer.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL upgrade path, including the V34 legacy learning reset. */
@Testcontainers
class BusinessSchemaIT {
    private static final List<String> KEPT_TABLES = List.of("ai_budget", "ai_job", "ai_usage", "app_user", "audit_event",
            "browser_connection", "browser_oauth_state", "idempotency_record", "integration_connection", "notification",
            "notification_delivery", "notification_preference", "notion_credential", "notion_oauth_state", "notion_revocation",
            "oidc_login_state", "outbox_event", "push_subscription", "user_identity", "user_setting",
            "ai_model_price", "competency_map_version", "competency", "competency_sheet_version", "career_fact", "resume_claim",
            "user_competency", "task_bank_item", "daily_task", "daily_task_turn", "daily_task_result", "answer_card",
            "task_feedback", "task_issue_report");
    private static final List<String> DROPPED_TABLES = List.of("curriculum", "user_curriculum", "learning_day", "learning_content",
            "problem", "test", "wrong_answer", "extra_learning", "study_evaluation", "ai_practice_run", "ai_conversation",
            "notion_sync_batch", "notion_target", "integration_mapping", "sync_job", "subject");

    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6")
            .withUsername("daily_career");
    static SingleConnectionDataSource dataSource;
    static JdbcTemplate jdbc;
    static int upgradeCount;
    static String v1Checksum;
    static long legacyUser;

    @BeforeAll static void upgradeFromV1() {
        dataSource = new SingleConnectionDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).defaultSchema("public").target("1").load().migrate();
        v1Checksum = jdbc.queryForObject("select checksum::text from public.flyway_schema_history where version='1'", String.class);
        jdbc.update("""
                insert into public.spring_session values
                ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',1,1,1800,9999999999999,'upgrade-fixture')
                """);
        jdbc.update("insert into public.spring_session_attributes values ('00000000-0000-0000-0000-000000000001','fixture',decode('010203','hex'))");
        upgradeCount = Flyway.configure().dataSource(dataSource).defaultSchema("public").target("3").load().migrate().migrationsExecuted;
        legacyUser = jdbc.queryForObject("insert into daily_career.app_user(display_name) values (?) returning id", Long.class, "legacy-" + "x".repeat(70));
        upgradeCount += Flyway.configure().dataSource(dataSource).defaultSchema("public").target("8").load().migrate().migrationsExecuted;
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", legacyUser);
        upgradeCount += Flyway.configure().dataSource(dataSource).defaultSchema("public").target("33").load().migrate().migrationsExecuted;
        long curriculum = id("insert into daily_career.curriculum(code,title) values (?, 'legacy') returning id", UUID.randomUUID().toString());
        long version = id("insert into daily_career.curriculum_version(curriculum_id,version_no) values (?,1) returning id", curriculum);
        jdbc.update("insert into daily_career.user_curriculum(user_id,curriculum_version_id,start_date,target_end_date,projected_end_date) values (?,?,current_date,current_date+30,current_date+30)", legacyUser, version);
        outbox(legacyUser, "legacy-event");
        jdbc.update("insert into daily_career.idempotency_record(user_id,operation_scope,idempotency_key,request_digest,expires_at) values (?,'legacy','legacy-key','fixture',now()+interval '1 day')", legacyUser);
        upgradeCount += Flyway.configure().dataSource(dataSource).defaultSchema("public").load().migrate().migrationsExecuted;
    }

    @AfterAll static void closeConnection() { if (dataSource != null) dataSource.destroy(); }

    @Test void upgradesV1WithoutLosingSessionsAndCanRunAgain() {
        assertThat(upgradeCount).isEqualTo(34);
        assertThat(jdbc.queryForObject("select scheduled_reminders_enabled from daily_career.user_setting where user_id=?", Boolean.class, legacyUser)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.app_user where display_name=? and email is null and profile_image_url is null", Integer.class, "legacy-" + "x".repeat(70))).isEqualTo(1);
        assertThat(jdbc.queryForMap("select daily_goal_minutes,rest_days_per_week,weekday_study_start_time,weekend_study_start_time,theme from daily_career.user_setting where user_id=?", legacyUser))
                .containsEntry("daily_goal_minutes", 60).containsEntry("rest_days_per_week", 2)
                .containsEntry("weekday_study_start_time", "19:30").containsEntry("weekend_study_start_time", "10:00").containsEntry("theme", "SYSTEM");
        assertThat(jdbc.queryForObject("select checksum::text from public.flyway_schema_history where version='1'", String.class)).isEqualTo(v1Checksum);
        assertThat(jdbc.queryForObject("select encode(attribute_bytes,'hex') from public.spring_session_attributes where attribute_name='fixture'", String.class)).isEqualTo("010203");
        // Use a fresh connection as on application restart, after $user schema now exists.
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .defaultSchema("public").load();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where tablename='flyway_schema_history' and schemaname='public'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where tablename='flyway_schema_history' and schemaname='daily_career'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select version from public.flyway_schema_history order by installed_rank", String.class))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 35).mapToObj(Integer::toString).toList());
    }

    @Test void resetRemovesLegacyLearningAndKeepsAccountsSettingsAndIntegrations() {
        List<String> tables = jdbc.queryForList("select tablename from pg_tables where schemaname='daily_career'", String.class);
        assertThat(tables).containsAll(KEPT_TABLES).doesNotContainAnyElementsOf(DROPPED_TABLES);
        assertThat(jdbc.queryForObject("select count(*) from pg_views where schemaname='daily_career'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_proc where pronamespace='daily_career'::regnamespace", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='daily_career' and table_name='ai_job' and column_name='conversation_id'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.outbox_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.idempotency_record", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.user_setting where user_id=?", Integer.class, legacyUser)).isEqualTo(1);
    }

    @Test void scopesIdempotencyAndRollsBackOutboxWithBusinessChanges() {
        long user = user(), other = user();
        String insert = "insert into daily_career.idempotency_record(user_id,operation_scope,idempotency_key,request_digest,expires_at) values (?,'submit','same-action','fixture',now()+interval '1 day')";
        jdbc.update(insert, user);
        rejects("23505", insert, user);
        jdbc.update(insert, other);
        String eventKey = UUID.randomUUID().toString();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(tx -> {
            jdbc.update("update daily_career.app_user set display_name='rolled back' where id=?", user);
            outbox(user, eventKey);
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select display_name from daily_career.app_user where id=?", String.class, user)).isEqualTo("fixture");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.outbox_event where event_key=?", Integer.class, eventKey)).isZero();
        transaction.executeWithoutResult(tx -> {
            jdbc.update("update daily_career.app_user set display_name='committed' where id=?", user);
            outbox(user, eventKey);
        });
        assertThat(jdbc.queryForObject("select status from daily_career.outbox_event where event_key=?", String.class, eventKey)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select display_name from daily_career.app_user where id=?", String.class, user)).isEqualTo("committed");
    }

    private static long user() { return id("insert into daily_career.app_user(display_name) values ('fixture') returning id"); }

    private static void outbox(long user, String key) {
        jdbc.update("insert into daily_career.outbox_event(user_id,event_key,event_type,aggregate_type,aggregate_id,aggregate_revision,payload,available_at) values (?,?,'FIXTURE','USER',?,0,'{}',now())", user, key, user);
    }

    private static long id(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    private static void rejects(String sqlState, String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).satisfies(error -> {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(sqlState);
        });
    }
}
