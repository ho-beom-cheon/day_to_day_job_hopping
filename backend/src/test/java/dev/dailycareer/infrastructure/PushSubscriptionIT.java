package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.notification.push.PushSubscriptionService;
import dev.dailycareer.notification.push.PushViews;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class PushSubscriptionIT {
    private static final String CLIENT_PUBLIC = "BN4WaJsRkBYNK7X4KsOENONHyBPcqFHG0-WTggXcD4_VnqfgKoUkph5mNXpcmlouhs9_YJRZZMMdI1trvSDxqiM";
    private static final String CLIENT_AUTH = "MDEyMzQ1Njc4OWFiY2RlZg";
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.web-push.public-key", () -> "BA1Hxzyi1RUM1b5wjxsn7nGxAszw2u61m164i3MrAIxHF6YK5h4SDYic-dRuU_RCPCfA5aq9ojSwk5Y2EmClBPs");
        registry.add("app.web-push.private-key", () -> key(32, 9));
        registry.add("app.web-push.subscription-key", () -> key(32, 7));
        registry.add("app.web-push.key-id", () -> "test-v1");
        registry.add("app.web-push.subject", () -> "mailto:test@example.test");
        registry.add("app.web-push.allowed-hosts", () -> "1.1.1.1");
    }

    @Autowired PushSubscriptionService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test void registersRotatesListsAndIdempotentlyRevokesWithoutExposingSecrets() throws Exception {
        long user = user("owner");
        UUID device = UUID.randomUUID();
        var firstRequest = request(device, "https://1.1.1.1/push/first");
        UUID idempotencyKey = UUID.randomUUID();
        var first = service.subscribe(user, firstRequest, idempotencyKey);
        assertThat(first.status()).isEqualTo(201);
        assertThat(service.subscribe(user, firstRequest, idempotencyKey)).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.push_subscription where user_id=?", Integer.class, user)).isEqualTo(1);

        byte[] endpointCiphertext = jdbc.queryForObject("select endpoint_ciphertext from daily_career.push_subscription where user_id=?", byte[].class, user);
        byte[] authCiphertext = jdbc.queryForObject("select auth_ciphertext from daily_career.push_subscription where user_id=?", byte[].class, user);
        assertThat(new String(endpointCiphertext, StandardCharsets.UTF_8)).doesNotContain("push/first");
        assertThat(new String(authCiphertext, StandardCharsets.UTF_8)).doesNotContain(CLIENT_AUTH);
        assertThat(json.writeValueAsString(service.list(user))).doesNotContain("1.1.1.1", "p256dh", "auth");

        service.subscribe(user, request(device, "https://1.1.1.1/push/second"), UUID.randomUUID());
        var listed = service.list(user);
        assertThat(listed).hasSize(2);
        assertThat(listed.stream().filter(PushViews.PushSubscription::active)).hasSize(1);
        long activeId = listed.stream().filter(PushViews.PushSubscription::active).findFirst().orElseThrow().subscriptionId();
        assertThat(service.delete(user, activeId).deleted()).isTrue();
        assertThat(service.delete(user, activeId).deleted()).isTrue();
        assertThat(service.list(user)).noneMatch(PushViews.PushSubscription::active);
    }

    private PushViews.PushSubscribe request(UUID device, String endpoint) {
        return new PushViews.PushSubscribe(device, "Windows Chrome", endpoint, null,
                new PushViews.PushKeys(CLIENT_PUBLIC, CLIENT_AUTH));
    }
    private long user(String name) {
        long id = jdbc.queryForObject("insert into daily_career.app_user(display_name,email) values (?,?) returning id", Long.class,
                name, name + "-" + UUID.randomUUID() + "@example.test");
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", id);
        return id;
    }
    private static String key(int size, int value) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, (byte) value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
