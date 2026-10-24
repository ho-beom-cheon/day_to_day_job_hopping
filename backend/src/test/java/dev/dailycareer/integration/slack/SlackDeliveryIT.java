package dev.dailycareer.integration.slack;

import dev.dailycareer.DailyCareerApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(SlackDeliveryIT.FakeProviderConfiguration.class)
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "daily-career.slack.poll-delay-ms=3600000"})
class SlackDeliveryIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.slack.credential-ref", () -> "env://slack/webhook");
        registry.add("app.slack.webhook-url", () -> "https://hooks.slack.com/services/T/B/X");
        registry.add("app.slack.channel-name", () -> "#learning");
        registry.add("app.slack.allowed-hosts", () -> "hooks.slack.com");
    }

    @Autowired SlackService service;
    @Autowired SlackDeliveryWorker worker;
    @Autowired FakeSlackProvider provider;
    @Autowired JdbcTemplate jdbc;

    @Test void submitsOneDurableTestMessageAndMarksReplayBeforeProviderAcceptance() {
        long user = user("slack-owner");
        UUID key = UUID.randomUUID();
        var first = service.submitTest(user, key);
        long deliveryId = first.data().path("jobId").asLong();

        assertThat(first.status()).isEqualTo(202);
        assertThat(first.location()).isEqualTo("/api/v1/integrations/slack/deliveries/" + deliveryId);
        assertThat(first.data().path("reused").asBoolean()).isFalse();
        assertThat(service.submitTest(user, key).data().path("reused").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.notification_delivery where user_id=? and channel='SLACK'", Integer.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select notification.canceled_at is not null from daily_career.notification_delivery delivery
                join daily_career.notification notification on notification.id=delivery.notification_id
                where delivery.id=?
                """, Boolean.class, deliveryId)).isTrue();

        provider.result = SlackProvider.Result.ACCEPTED;
        worker.poll();
        assertThat(service.get(user, deliveryId).status()).isEqualTo("SUCCESS");
        assertThat(service.list(user, 0, 20, null).content()).hasSize(1);
    }

    @Test void keepsUnknownAndRejectedFinalWhileRetryableReturnsToReady() {
        long user = user("slack-outcomes");
        long unknown = submit(user, SlackProvider.Result.UNKNOWN);
        assertThat(service.get(user, unknown).status()).isEqualTo("UNKNOWN");
        assertThat(service.get(user, unknown).errorCode()).isEqualTo("SLACK_PROVIDER_RESULT_UNKNOWN");

        long rejected = submit(user, SlackProvider.Result.REJECTED);
        assertThat(service.get(user, rejected).status()).isEqualTo("FAILED");
        assertThat(service.get(user, rejected).errorCode()).isEqualTo("SLACK_PROVIDER_REJECTED");

        long retryable = submit(user, SlackProvider.Result.RETRYABLE);
        assertThat(service.get(user, retryable).status()).isEqualTo("READY");
        assertThat(service.get(user, retryable).errorCode()).isEqualTo("SLACK_PROVIDER_RETRYABLE");
    }

    private long submit(long user, SlackProvider.Result result) {
        provider.result = result;
        long id = service.submitTest(user, UUID.randomUUID()).data().path("jobId").asLong();
        worker.poll();
        return id;
    }

    private long user(String name) {
        long id = jdbc.queryForObject("insert into daily_career.app_user(display_name,email) values (?,?) returning id",
                Long.class, name, name + "-" + UUID.randomUUID() + "@example.test");
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", id);
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {
        @Bean @Primary FakeSlackProvider fakeSlackProvider() { return new FakeSlackProvider(); }
    }

    static final class FakeSlackProvider implements SlackProvider {
        volatile Result result = Result.ACCEPTED;
        @Override public Result send(java.net.URI webhook, Message message) { return result; }
    }
}
