package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL contract tests; source dictionary and independent business invariants. */
@Testcontainers
class BusinessSchemaIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6")
            .withUsername("daily_career");
    static SingleConnectionDataSource dataSource;
    static JdbcTemplate jdbc;
    static int upgradeCount;
    static String v1Checksum;

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
        jdbc.update("insert into daily_career.app_user(display_name) values (?)", "legacy-" + "x".repeat(70));
        upgradeCount += Flyway.configure().dataSource(dataSource).defaultSchema("public").load().migrate().migrationsExecuted;
    }

    @AfterAll static void closeConnection() { if (dataSource != null) dataSource.destroy(); }

    @Test void upgradesV1WithoutLosingSessionsAndCanRunAgain() {
        assertThat(upgradeCount).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.app_user where display_name=? and email is null and profile_image_url is null", Integer.class, "legacy-" + "x".repeat(70))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select checksum::text from public.flyway_schema_history where version='1'", String.class)).isEqualTo(v1Checksum);
        assertThat(jdbc.queryForObject("select encode(attribute_bytes,'hex') from public.spring_session_attributes where attribute_name='fixture'", String.class)).isEqualTo("010203");
        // Use a fresh connection as on application restart, after $user schema now exists.
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .defaultSchema("public").load();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where tablename='flyway_schema_history' and schemaname='public'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where tablename='flyway_schema_history' and schemaname='daily_career'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select version from public.flyway_schema_history order by installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test void catalogMatchesEveryArchivedColumnConstraintAndIndex() throws Exception {
        jdbc.execute("set search_path to public"); // Stable qualified catalog rendering, independent of the DB role.
        JsonNode source;
        try (var stream = getClass().getResourceAsStream("/database/design-v0.1.json")) {
            source = new ObjectMapper().readTree(stream);
        }
        var names = new ArrayList<String>();
        for (var table : source.get("tables")) {
            String name = table.get("name").asText();
            names.add(name);
            // PostgreSQL normalizes types/defaults/expressions independently of our migration text.
            var definitions = new ArrayList<String>();
            for (var column : table.get("columns")) {
                String definition = column.get("name").asText() + " " + column.get("type").asText();
                String value = column.get("default").asText();
                if (value.equals("IDENTITY")) definition += " generated always as identity";
                else if (!value.equals("—")) definition += " default " + value;
                if (column.get("nullability").asText().equals("불가")) definition += " not null";
                definitions.add(definition);
            }
            int checkNo = 0;
            if (name.equals("app_user")) {
                definitions.add("profile_image_url varchar(2048)");
                definitions.add("check (profile_image_url IS NULL OR profile_image_url ~ '^https://[^[:space:]]+$')");
            }
            if (name.equals("wrong_answer")) {
                definitions.add("review_count integer default 0 not null");
                definitions.add("resolution_type varchar(32)");
                definitions.add("resolution_attempt_id bigint");
            }
            for (var check : table.get("checks")) {
                String expression = check.asText();
                if (name.equals("test") && expression.startsWith("test_type IN")) {
                    expression = "test_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'FINAL')";
                }
                if (name.equals("problem_version") && expression.startsWith("question_type IN")) {
                    expression = "question_type IN ('SINGLE_CHOICE', 'TRUE_FALSE', 'MULTI_CHOICE', 'SHORT_ANSWER', 'ESSAY', 'CODE', 'INTERVIEW')";
                }
                definitions.add("constraint ck_" + name + "_" + String.format("%02d", ++checkNo) + " check (" + expression + ")");
            }
            if (name.equals("wrong_answer")) {
                definitions.add("constraint ck_wrong_answer_03 check (review_count >= 0)");
                definitions.add("constraint ck_wrong_answer_04 check ((status = 'MASTERED' AND mastered_at IS NOT NULL AND resolution_type IN ('MANUAL', 'CORRECT_RETRY')) OR (status <> 'MASTERED' AND mastered_at IS NULL AND resolution_type IS NULL AND resolution_attempt_id IS NULL))");
                definitions.add("constraint ck_wrong_answer_05 check ((resolution_type = 'MANUAL' AND resolution_attempt_id IS NULL) OR (resolution_type = 'CORRECT_RETRY' AND resolution_attempt_id IS NOT NULL) OR resolution_type IS NULL)");
            }
            jdbc.execute("create temporary table expected_dictionary (" + String.join(",", definitions) + ")");
            try {
                assertThat(columns("daily_career." + name)).as(name + " columns/types/defaults/nullability/identity")
                        .isEqualTo(columns("pg_temp.expected_dictionary"));
                assertThat(constraints("daily_career." + name, "c")).as(name + " CHECK expressions")
                        .isEqualTo(constraints("pg_temp.expected_dictionary", "c"));
                assertThat(constraints("daily_career." + name, "p")).as(name + " PK")
                        .containsExactly(Map.of("definition", "PRIMARY KEY (" + table.get("primaryKey").asText() + ")"));
                var uniques = new ArrayList<Map<String, Object>>();
                for (var key : table.get("uniqueKeys")) uniques.add(Map.of("definition", "UNIQUE (" + key.asText() + ")"));
                assertThat(constraints("daily_career." + name, "u")).as(name + " UNIQUE").containsExactlyInAnyOrderElementsOf(uniques);
                var foreignKeys = new ArrayList<Map<String, Object>>();
                for (var fk : table.get("foreignKeys")) {
                    foreignKeys.add(Map.of("definition", "FOREIGN KEY (" + fk.get("columns").asText() + ") REFERENCES daily_career."
                            + fk.get("targetTable").asText() + "(" + fk.get("targetColumns").asText() + ") ON DELETE " + fk.get("onDelete").asText()));
                }
                if (name.equals("wrong_answer")) foreignKeys.add(Map.of("definition", "FOREIGN KEY (resolution_attempt_id, user_id) REFERENCES daily_career.problem_attempt(id, user_id) ON DELETE RESTRICT"));
                assertThat(constraints("daily_career." + name, "f")).as(name + " FK targets/columns/deletion")
                        .containsExactlyInAnyOrderElementsOf(foreignKeys);
                for (var index : table.get("indexes")) {
                    String candidate = index.asText();
                    boolean unique = candidate.startsWith("UNIQUE ");
                    String[] parts = candidate.replaceFirst("^UNIQUE ", "").split(" ", 2);
                    jdbc.execute("create " + (unique ? "unique " : "") + "index expected_index on expected_dictionary " + parts[1]);
                    assertThat(indexDefinition("daily_career." + parts[0])).as(name + " " + parts[0])
                            .isEqualTo(indexDefinition("pg_temp.expected_index"));
                    jdbc.execute("drop index pg_temp.expected_index");
                }
                int expectedIndexes = 1 + table.get("uniqueKeys").size() + table.get("indexes").size();
                assertThat(jdbc.queryForObject("select count(*) from pg_index where indrelid=?::regclass", Integer.class, "daily_career." + name))
                        .as(name + " no missing or extra indexes").isEqualTo(expectedIndexes);
                for (var column : table.get("columns")) {
                    assertThat(jdbc.queryForObject("select col_description(attrelid, attnum) from pg_attribute where attrelid=?::regclass and attname=?", String.class,
                            "daily_career." + name, column.get("name").asText())).isEqualTo(column.get("description").asText());
                }
            } finally {
                jdbc.execute("drop table pg_temp.expected_dictionary");
            }
        }
        names.add("schedule_preview");
        names.add("oidc_login_state");
        names.addAll(List.of("curriculum_release","curriculum_unit_detail","learning_content_detail","learning_content_problem",
                "curriculum_item_content","assigned_learning_day","assigned_session_detail","assigned_content","problem_report"));
        assertThat(jdbc.queryForList("select tablename from pg_tables where schemaname='daily_career'", String.class))
                .containsExactlyInAnyOrderElementsOf(names);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='daily_career'", Integer.class)).isEqualTo(631);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where connamespace='daily_career'::regnamespace and contype='f'", Integer.class)).isEqualTo(123);
    }

    @Test void problemPracticeExtensionHasReportOwnershipAndImmutableVersionLink() {
        assertThat(columns("daily_career.problem_report")).anySatisfy(row->{assertThat(row.get("attname")).isEqualTo("user_id");assertThat(row.get("type")).isEqualTo("bigint");assertThat(row.get("attnotnull")).isEqualTo(true);})
                .anySatisfy(row->{assertThat(row.get("attname")).isEqualTo("problem_version_id");assertThat(row.get("type")).isEqualTo("bigint");assertThat(row.get("attnotnull")).isEqualTo(true);})
                .anySatisfy(row->{assertThat(row.get("attname")).isEqualTo("status");assertThat(row.get("default_value")).isEqualTo("'RECEIVED'::character varying");});
        assertThat(constraints("daily_career.problem_report","f")).contains(
                Map.of("definition","FOREIGN KEY (user_id) REFERENCES daily_career.app_user(id) ON DELETE RESTRICT"),
                Map.of("definition","FOREIGN KEY (problem_version_id, problem_id) REFERENCES daily_career.problem_version(id, problem_id) ON DELETE RESTRICT"));
    }

    @Test void rejectsCrossOwnerReferencesInvalidStatesAndDuplicateActiveCourses() {
        long user = user(), other = user(), course = course(user);
        rejects("23503", "insert into daily_career.user_identity(user_id,issuer,subject) values (-1,'issuer','subject')");
        rejects("23503", "insert into daily_career.schedule_change(user_id,user_curriculum_id,from_revision,to_revision,reason_code,before_snapshot,after_snapshot) values (?, ?,0,1,'TEST','{}','{}')", other, course);
        rejects("23505", "insert into daily_career.user_curriculum(user_id,curriculum_version_id,start_date,target_end_date,projected_end_date) select user_id,curriculum_version_id,start_date,target_end_date,projected_end_date from daily_career.user_curriculum where id=?", course);
        rejects("23514", "update daily_career.user_curriculum set schedule_revision=-1 where id=?", course);
        rejects("23514", "update daily_career.app_user set status='WITHDRAWN' where id=?", user);
        rejects("23514", "insert into daily_career.test(title,test_type) values ('invalid','YEARLY')");
        rejects("23502", "insert into daily_career.app_user(display_name) values (null)");
        jdbc.update("insert into daily_career.user_identity(user_id,issuer,subject) values (?,'issuer','unique-subject')", user);
        rejects("23505", "insert into daily_career.user_identity(user_id,issuer,subject) values (?,'issuer','unique-subject')", other);
    }

    @Test void preservesSubmittedAttemptAcrossFailedGradingAndSelectsOnlyOneSuccessfulRun() {
        long user = user(), course = course(user);
        long test = id("insert into daily_career.test(title,test_type) values ('final fixture','FINAL') returning id");
        long version = id("insert into daily_career.test_version(test_id,version_no,time_limit_seconds,pass_percent,rule_version) values (?,1,600,60,'fixture') returning id", test);
        long attempt = id("insert into daily_career.test_attempt(user_id,user_curriculum_id,test_version_id,attempt_no,started_at,deadline_at,status,submitted_at,submission_digest) values (?,?,?,1,now(),now()+interval '10 minutes','SUBMITTED',now(),'immutable-fixture') returning id", user, course, version);
        long subject = id("insert into daily_career.subject(code,name) values ('grading-fixture','fixture') returning id");
        long problem = id("insert into daily_career.problem(subject_id,title) values (?,'fixture') returning id", subject);
        long problemVersion = id("insert into daily_career.problem_version(problem_id,version_no,question_type,difficulty,prompt_markdown,answer_key,rubric,explanation_markdown,grading_rule_version) values (?,1,'ESSAY',1,'fixture','{}','{}','fixture','fixture') returning id", problem);
        long item = id("insert into daily_career.test_item(test_version_id,problem_id,problem_version_id,item_no,max_score) values (?,?,?,1,10) returning id", version, problem, problemVersion);
        long answer = id("insert into daily_career.test_attempt_item(user_id,test_attempt_id,test_version_id,test_item_id,display_order,question_snapshot,grading_snapshot,answer_payload) values (?,?,?,?,1,'{}','{}','{\"text\":\"saved answer\"}') returning id", user, attempt, version, item);
        long failed = run(user, attempt, 1, "FAILED", false);
        rejects("23514", "update daily_career.grading_run set is_current=true where id=?", failed);
        long succeeded = run(user, attempt, 2, "SUCCEEDED", true);
        String result = "insert into daily_career.grading_result(user_id,grading_run_id,test_attempt_id,test_attempt_item_id,earned_score,max_score,rubric_result,feedback_markdown) values (?,?,?,?,?,10,'{}','fixture')";
        rejects("23514", result, user, succeeded, attempt, answer, 11);
        jdbc.update(result, user, succeeded, attempt, answer, 8);
        rejects("23505", "insert into daily_career.grading_run(user_id,test_attempt_id,run_no,grader_type,rule_version,input_digest,status,is_current) values (?,?,3,'RULE','fixture','fixture','SUCCEEDED',true)", user, attempt);
        long replacement = run(user, attempt, 3, "SUCCEEDED", false);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> {
            jdbc.update("update daily_career.grading_run set is_current=false where id=?", succeeded);
            jdbc.update("update daily_career.grading_run set is_current=true where id=?", replacement);
        });
        assertThat(jdbc.queryForObject("select submission_digest from daily_career.test_attempt where id=? and status='SUBMITTED'", String.class, attempt)).isEqualTo("immutable-fixture");
        assertThat(jdbc.queryForObject("select answer_payload->>'text' from daily_career.test_attempt_item where id=?", String.class, answer)).isEqualTo("saved answer");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.grading_run where test_attempt_id=?", Integer.class, attempt)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select id from daily_career.grading_run where test_attempt_id=? and is_current", Long.class, attempt)).isEqualTo(replacement);
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

    @Test void previewHasTenMinuteLifetimeOwnershipAndConditionalSingleConsumption() {
        long user = user(), other = user(), course = course(user);
        String sql = "insert into daily_career.schedule_preview(user_id,user_curriculum_id,schedule_revision,action,request_snapshot,preview_snapshot) values (?,?,0,'POLICY_APPLY','{}','{}')";
        rejects("23503", sql, other, course);
        long preview = id(sql + " returning id", user, course);
        assertThat(jdbc.queryForObject("select extract(epoch from (expires_at-created_at))::integer from daily_career.schedule_preview where id=?", Integer.class, preview)).isEqualTo(600);
        rejects("23514", "update daily_career.schedule_preview set consumed_at=expires_at where id=?", preview);
        rejects("23514", "update daily_career.schedule_preview set request_snapshot='[]' where id=?", preview);
        rejects("23514", "update daily_career.schedule_preview set schedule_revision=-1 where id=?", preview);
        String consume = "update daily_career.schedule_preview set consumed_at=clock_timestamp() where id=? and user_id=? and schedule_revision=? and consumed_at is null and expires_at>clock_timestamp()";
        assertThat(jdbc.update(consume, preview, user, 1)).isZero();
        assertThat(jdbc.update(consume, preview, other, 0)).isZero();
        assertThat(jdbc.update(consume, preview, user, 0)).isEqualTo(1);
        assertThat(jdbc.update(consume, preview, user, 0)).isZero();
        long expired = id(sql + " returning id", user, course);
        jdbc.update("update daily_career.schedule_preview set created_at=created_at-interval '11 minutes', expires_at=expires_at-interval '11 minutes' where id=?", expired);
        assertThat(jdbc.update(consume, expired, user, 0)).isZero();
    }

    private static List<Map<String, Object>> columns(String table) {
        return jdbc.queryForList("""
                select a.attname, format_type(a.atttypid,a.atttypmod) as type, a.attnotnull,
                       a.attidentity::text, pg_get_expr(d.adbin,d.adrelid) as default_value
                from pg_attribute a left join pg_attrdef d on d.adrelid=a.attrelid and d.adnum=a.attnum
                where a.attrelid=?::regclass and a.attnum>0 and not a.attisdropped order by a.attnum
                """, table);
    }

    private static List<Map<String, Object>> constraints(String table, String type) {
        return jdbc.queryForList("select pg_get_constraintdef(oid) as definition from pg_constraint where conrelid=?::regclass and contype=?::\"char\" order by conname", table, type);
    }

    private static Map<String, Object> indexDefinition(String index) {
        return jdbc.queryForMap("select indisunique, indisvalid, substring(pg_get_indexdef(indexrelid) from ' USING .*') as definition from pg_index where indexrelid=?::regclass", index);
    }

    private static long user() { return id("insert into daily_career.app_user(display_name) values ('fixture') returning id"); }

    private static long course(long user) {
        long curriculum = id("insert into daily_career.curriculum(code,title) values (?, 'fixture') returning id", UUID.randomUUID().toString());
        long version = id("insert into daily_career.curriculum_version(curriculum_id,version_no) values (?,1) returning id", curriculum);
        return id("insert into daily_career.user_curriculum(user_id,curriculum_version_id,start_date,target_end_date,projected_end_date) values (?,?,current_date,current_date+180,current_date+180) returning id", user, version);
    }

    private static long run(long user, long attempt, int number, String status, boolean current) {
        return id("insert into daily_career.grading_run(user_id,test_attempt_id,run_no,grader_type,rule_version,input_digest,status,is_current) values (?,?,?,'RULE','fixture','fixture',?,?) returning id", user, attempt, number, status, current);
    }

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
