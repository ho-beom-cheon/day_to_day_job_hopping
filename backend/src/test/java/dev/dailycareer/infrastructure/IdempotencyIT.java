package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.RequestFingerprint;
import dev.dailycareer.common.idempotency.StoredReply;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Exercise the production service and transaction manager against real PostgreSQL. */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class IdempotencyIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired IdempotencyService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void replaysCanonicalRequestWithExactlyOneBusinessChangeAndOutboxEvent() throws Exception {
        long user = user();
        UUID key = UUID.randomUUID();
        var count = new AtomicInteger();
        var input = json("{\"targetId\":\"1\",\"body\":{\"b\":2,\"a\":1}}");
        var equivalent = json("{\"body\":{\"a\":1,\"b\":2},\"targetId\":\"1\"}");
        var first = service.execute(user, "TEST-005", key, input, () -> {
            count.incrementAndGet();
            change(user, key.toString());
            return reply("accepted");
        });
        var replay = service.execute(user, "TEST-005", key, equivalent, () -> { throw new AssertionError("Replayed work"); });
        assertThat(replay).isEqualTo(first);
        assertThat(count).hasValue(1);
        assertThat(revision(user)).isEqualTo(1);
        assertThat(events(key.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from daily_career.idempotency_record where user_id=?", String.class, user)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select extract(epoch from expires_at-created_at)::integer from daily_career.idempotency_record where user_id=?", Integer.class, user)).isEqualTo(86400);
        String stored = jdbc.queryForObject("select response_payload::text from daily_career.idempotency_record where user_id=?", String.class, user);
        assertThat(stored).doesNotContain("traceId", "serverTime", "Set-Cookie", "Authorization");
    }

    @Test void rejectsChangedPayloadOrTargetAndSeparatesUserAndApiScopes() throws Exception {
        long firstUser = user(), secondUser = user();
        UUID key = UUID.randomUUID();
        JsonNode input = json("{\"targetId\":\"1\",\"body\":{},\"ifMatch\":\"7\"}");
        service.execute(firstUser, "TEST-005", key, input, () -> reply("first"));
        for (var changed : new String[]{"{\"targetId\":\"2\",\"body\":{},\"ifMatch\":\"7\"}",
                "{\"targetId\":\"1\",\"body\":null,\"ifMatch\":\"7\"}",
                "{\"targetId\":\"1\",\"body\":{},\"ifMatch\":\"8\"}"}) {
            JsonNode request = json(changed);
            assertCode(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, () -> service.execute(firstUser, "TEST-005", key, request,
                    () -> { throw new AssertionError("Changed request executed"); }));
        }
        assertThat(service.execute(secondUser, "TEST-005", key, input, () -> reply("second")).data().asText()).isEqualTo("second");
        assertThat(service.execute(firstUser, "CURR-002", key, input, () -> reply("other-api")).data().asText()).isEqualTo("other-api");
        assertThat(RequestFingerprint.digest(json("{\"body\":{}}"))).isNotEqualTo(RequestFingerprint.digest(json("{\"body\":{\"field\":null}}")));
        assertThat(RequestFingerprint.digest(json("{\"body\":[1,2]}"))).isNotEqualTo(RequestFingerprint.digest(json("{\"body\":[2,1]}")));
    }

    @Test void concurrentDuplicateReturnsBusyThenReplaysCommittedResult() throws Exception {
        long user = user();
        UUID key = UUID.randomUUID();
        JsonNode input = json("{\"targetId\":\"1\"}");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.execute(user, "TEST-005", key, input, () -> {
                count.incrementAndGet();
                entered.countDown();
                await(release);
                change(user, key.toString());
                return reply("accepted");
            }));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertCode(ApiErrorCode.REQUEST_IN_PROGRESS, () -> service.execute(user, "TEST-005", key, input,
                        () -> { throw new AssertionError("Concurrent work ran"); }));
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(202);
        }
        service.execute(user, "TEST-005", key, input, () -> { throw new AssertionError("Replayed work"); });
        assertThat(count).hasValue(1);
        assertThat(revision(user)).isEqualTo(1);
        assertThat(events(key.toString())).isEqualTo(1);
    }

    @Test void rollsBackWorkOutboxAndKeyOnFailureAndOnOuterTransactionRollback() throws Exception {
        long user = user();
        UUID key = UUID.randomUUID();
        JsonNode input = json("{}");
        assertThatThrownBy(() -> service.execute(user, "TEST-005", key, input, () -> {
            change(user, key.toString());
            throw new IllegalStateException("fixture failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(revision(user)).isZero();
        assertThat(events(key.toString())).isZero();
        assertThat(records(user)).isZero();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.execute(user, "TEST-005", key, input, () -> { change(user, key.toString()); return reply("accepted"); });
            tx.setRollbackOnly();
        });
        assertThat(revision(user)).isZero();
        assertThat(events(key.toString())).isZero();
        assertThat(records(user)).isZero();
        service.execute(user, "TEST-005", key, input, () -> { change(user, key.toString()); return reply("accepted"); });
        assertThat(revision(user)).isEqualTo(1);
        assertThat(events(key.toString())).isEqualTo(1);
    }

    @Test void expiredKeyCanBeReclaimedAndActiveIncompleteRecordCannotBeReexecuted() throws Exception {
        long user = user();
        UUID key = UUID.randomUUID();
        service.execute(user, "TEST-005", key, json("{\"body\":1}"), () -> reply("old"));
        long id = jdbc.queryForObject("select id from daily_career.idempotency_record where user_id=?", Long.class, user);
        jdbc.update("update daily_career.idempotency_record set expires_at=current_timestamp-interval '1 second' where id=?", id);
        assertThat(service.execute(user, "TEST-005", key, json("{\"body\":2}"), () -> reply("new")).data().asText()).isEqualTo("new");
        assertThat(records(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select id from daily_career.idempotency_record where user_id=?", Long.class, user)).isEqualTo(id);
        jdbc.update("update daily_career.idempotency_record set status='PROCESSING',response_status=null,response_payload=null where id=?", id);
        JsonNode input = json("{\"body\":2}");
        assertCode(ApiErrorCode.REQUEST_IN_PROGRESS, () -> service.execute(user, "TEST-005", key, input,
                () -> { throw new AssertionError("Incomplete work reexecuted"); }));
    }

    @Test void atomicRevisionPredicateRejectsStaleWriterAndRollsBackItsKey() throws Exception {
        long user = user();
        JsonNode input = json("{\"ifMatch\":\"0\"}");
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> writer = () -> {
                try {
                    service.execute(user, "CURR-002", UUID.randomUUID(), input, () -> {
                        entered.countDown(); await(release);
                        int updated = jdbc.update("update daily_career.app_user set revision=revision+1 where id=? and revision=0", user);
                        if (updated == 0) throw new ApiException(ApiErrorCode.PRECONDITION_FAILED);
                        return reply("accepted");
                    });
                    return "SUCCESS";
                } catch (ApiException exception) { return exception.code().name(); }
            };
            var first = executor.submit(writer);
            var second = executor.submit(writer);
            try { assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { release.countDown(); }
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "PRECONDITION_FAILED");
        }
        assertThat(revision(user)).isEqualTo(1);
        assertThat(records(user)).isEqualTo(1);
    }

    @Test void cleanupRemovesOnlyExpiredRecordsWithinTheBatchLimit() throws Exception {
        long user = user();
        JsonNode input = json("{}");
        for (int i = 0; i < 3; i++) service.execute(user, "TEST-005", UUID.randomUUID(), input, () -> reply("accepted"));
        jdbc.update("update daily_career.idempotency_record set expires_at=current_timestamp-interval '1 second' where id in (select id from daily_career.idempotency_record where user_id=? order by id limit 2)", user);
        assertThat(service.purgeExpired(1)).isEqualTo(1);
        assertThat(records(user)).isEqualTo(2);
        assertThat(service.purgeExpired(1000)).isEqualTo(1);
        assertThat(records(user)).isEqualTo(1);
    }

    private long user() { return jdbc.queryForObject("insert into daily_career.app_user(display_name) values ('fixture') returning id", Long.class); }
    private long revision(long user) { return jdbc.queryForObject("select revision from daily_career.app_user where id=?", Long.class, user); }
    private int records(long user) { return jdbc.queryForObject("select count(*) from daily_career.idempotency_record where user_id=?", Integer.class, user); }
    private int events(String key) { return jdbc.queryForObject("select count(*) from daily_career.outbox_event where event_key=?", Integer.class, key); }
    private JsonNode json(String json) throws Exception { return mapper.readTree(json); }
    private StoredReply reply(String value) { return new StoredReply(202, mapper.getNodeFactory().textNode(value), "\"1\"", "/api/v1/ai/jobs/1"); }
    private void change(long user, String key) {
        jdbc.update("update daily_career.app_user set revision=revision+1 where id=?", user);
        jdbc.update("insert into daily_career.outbox_event(user_id,event_key,event_type,aggregate_type,aggregate_id,aggregate_revision,payload,available_at) values (?,?,'FIXTURE','USER',?,1,'{}',now())", user, key, user);
    }
    private void assertCode(ApiErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.code()).isEqualTo(code));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture coordination timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
