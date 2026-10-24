package dev.dailycareer.integration.notion;

import dev.dailycareer.DailyCareerApplication;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
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
@Import(NotionRuntimeIT.FakeProviderConfiguration.class)
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "daily-career.notion.revocation-delay-ms=3600000"})
class NotionRuntimeIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6")
            .withUsername("daily_career");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("APP_NOTION_CLIENT_ID", () -> "client-id");
        registry.add("APP_NOTION_CLIENT_SECRET", () -> "client-secret");
        registry.add("APP_NOTION_REDIRECT_URI", () -> "http://localhost:8080/api/v1/integrations/notion/callback");
        registry.add("APP_NOTION_CREDENTIAL_KEY", () -> key());
        registry.add("APP_NOTION_KEY_ID", () -> "test-v1");
    }

    @Autowired NotionService service;
    @Autowired NotionRevocationWorker revocationWorker;
    @Autowired FakeNotionProvider provider;
    @Autowired JdbcTemplate jdbc;

    @Test void connectsEncryptsAndDisconnectsWithoutExposingToken() {
        long user = user();
        UUID authorizationKey = UUID.randomUUID();
        var authorization = service.authorization(user, "session-a", authorizationKey);
        assertThat(service.authorization(user, "session-a", authorizationKey).data()).isEqualTo(authorization.data());
        String state = query(authorization.data().path("authorizationUrl").asText(), "state");
        assertThat(service.completeAuthorization(user, "another-session", state, "code")).isFalse();

        var secondAuthorization = service.authorization(user, "session-a", UUID.randomUUID());
        String secondState = query(secondAuthorization.data().path("authorizationUrl").asText(), "state");
        assertThat(service.completeAuthorization(user, "session-a", secondState, "code")).isTrue();
        assertThat(service.connection(user).connected()).isTrue();
        assertThat(jdbc.queryForObject("select access_token_ciphertext from daily_career.notion_credential where user_id=?",
                String.class, user)).doesNotContain("secret-access-token");

        assertThat(service.disconnect(user).providerRevocationStatus()).isEqualTo("PENDING");
        assertThat(service.connection(user).connected()).isFalse();
        revocationWorker.poll();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.notion_credential where user_id=?",
                Integer.class, user)).isZero();
        assertThat(provider.revoked).isTrue();
    }

    private long user() {
        long user = jdbc.queryForObject("insert into daily_career.app_user(display_name,email) values ('notion-owner',?) returning id",
                Long.class, UUID.randomUUID() + "@example.test");
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", user);
        return user;
    }

    private static String query(String url, String name) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2)).filter(pair -> pair[0].equals(name))
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)).findFirst().orElseThrow();
    }
    private static String key() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 7);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {
        @Bean @Primary FakeNotionProvider fakeNotionProvider() { return new FakeNotionProvider(); }
    }

    static final class FakeNotionProvider implements NotionProvider {
        volatile boolean revoked;
        @Override public OAuthToken exchange(String code) { return new OAuthToken("secret-access-token", UUID.randomUUID().toString(), "Test workspace"); }
        @Override public void revoke(String token) { assertThat(token).isEqualTo("secret-access-token"); revoked = true; }
    }
}
