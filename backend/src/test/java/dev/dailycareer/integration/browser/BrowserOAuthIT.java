package dev.dailycareer.integration.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.integration.IntegrationService;
import dev.dailycareer.integration.IntegrationViews;
import dev.dailycareer.integration.slack.SlackService;
import dev.dailycareer.integration.slack.SlackSettings;
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
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(BrowserOAuthIT.Fakes.class)
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "daily-career.slack.poll-delay-ms=3600000", "daily-career.ai.poll-delay-ms=3600000"})
class BrowserOAuthIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword);
        r.add("INTEGRATION_CREDENTIAL_KEY", () -> Base64.getEncoder().encodeToString(new byte[32]));
        r.add("SLACK_CLIENT_ID", () -> "test-client"); r.add("SLACK_CLIENT_SECRET", () -> "test-secret");
        r.add("SLACK_REDIRECT_URI", () -> "https://app.example.test/api/v1/integrations/browser/slack/callback");
        r.add("OPENROUTER_REDIRECT_URI", () -> "http://localhost:8080/api/v1/integrations/browser/openrouter/callback");
        r.add("OPENROUTER_MODEL", () -> "test/model");
    }
    @Autowired BrowserConnections service;
    @Autowired dev.dailycareer.ai.AiService ai;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Fake provider;
    @Autowired SlackSettings slack;
    @Autowired SlackService messages;
    @Autowired IntegrationService integrations;

    @Test void slackIsPersonalEncryptedAndStopsQueuedMessagesOnDisconnect() {
        long user = user(), other = user();
        String state = param(service.authorize(user,"slack","session").authorizationUrl(),"state");
        int calls = provider.calls;
        assertThat(service.callback(other,"slack","session",state,"code",null)).isFalse();
        assertThat(service.callback(user,"slack","wrong-session",state,"code",null)).isFalse();
        assertThat(provider.calls).isEqualTo(calls);
        assertThat(service.callback(user,"slack","session",state,"code",null)).isTrue();
        assertThat(service.callback(user,"slack","session",state,"code",null)).isFalse();
        assertThat(slack.enabled(user)).isTrue(); assertThat(slack.enabled(other)).isFalse();
        assertThat(slack.resolve(slack.credentialRef(user)).toString()).endsWith("/TEST");
        assertThat(integrations.slack(user).channelName()).isEqualTo("#learning");
        integrations.updateSlack(user,new IntegrationViews.SlackPatch(true,null),0);
        assertThat(integrations.slack(user).enabled()).isTrue();
        assertThat(jdbc.queryForObject("select credential_ciphertext from daily_career.browser_connection where user_id=?",String.class,user)).doesNotContain("hooks.slack.com");
        long delivery = messages.submitTest(user,UUID.randomUUID()).data().path("jobId").asLong();
        assertThatThrownBy(() -> service.disconnect(user,"slack",0)).isInstanceOf(ApiException.class);
        String ref = slack.credentialRef(user);
        assertThat(service.disconnect(user,"slack",1).providerRevocationStatus()).isEqualTo("MANUAL_REQUIRED");
        assertThat(service.status(user,"slack").connected()).isFalse();
        assertThatThrownBy(() -> slack.resolve(ref)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select status from daily_career.notification_delivery where id=?",String.class,delivery)).isEqualTo("CANCELED");
    }
    @Test void openRouterBindsPkceAndNeverStoresPlainKeyOrVerifier() {
        long user = user();
        String url = service.authorize(user,"openrouter","session").authorizationUrl();
        String state = param(param(url,"callback_url"),"state");
        assertThat(service.callback(user,"openrouter","session",state,"code",null)).isTrue();
        assertThat(BrowserConnections.challenge(provider.verifier)).isEqualTo(param(url,"code_challenge"));
        String stored = jdbc.queryForObject("select verifier_ciphertext from daily_career.browser_oauth_state where state_hash=?",String.class,BrowserConnections.hash(state));
        assertThat(stored).doesNotContain(provider.verifier);
        String ref = service.reference(user,"openrouter");
        assertThat(service.credential(user,"openrouter",ref).path("key").asText()).isEqualTo("test-api-key");
        assertThat(service.status(user,"openrouter").toString()).doesNotContain("test-api-key");
        service.disconnect(user,"openrouter",1);
        assertThatThrownBy(() -> service.credential(user,"openrouter",ref)).isInstanceOf(IllegalStateException.class);
    }
    @Test void expiryCancellationAndDisconnectDuringExchangeCannotReconnect() {
        long user = user();
        String state = param(service.authorize(user,"slack","session").authorizationUrl(),"state");
        jdbc.update("update daily_career.browser_oauth_state set expires_at=clock_timestamp()-interval '1 second' where state_hash=?", BrowserConnections.hash(state));
        assertThat(service.callback(user,"slack","session",state,"code",null)).isFalse();
        state = param(service.authorize(user,"slack","session").authorizationUrl(),"state");
        assertThat(service.callback(user,"slack","session",state,null,"access_denied")).isFalse();
        assertThat(service.callback(user,"slack","session",state,"code",null)).isFalse();
        state = param(service.authorize(user,"slack","session").authorizationUrl(),"state");
        provider.duringExchange = () -> service.disconnect(user,"slack",0);
        try { assertThat(service.callback(user,"slack","session",state,"code",null)).isFalse(); }
        finally { provider.duringExchange = () -> {}; }
        assertThat(service.status(user,"slack").connected()).isFalse();
    }
    @Test void queuesAiUsingOwnedSnapshotAndCancelsItWithoutChangingProviderOnReconnect() throws Exception {
        long user = user();
        String url = service.authorize(user,"openrouter","session").authorizationUrl();
        String state = param(param(url,"callback_url"),"state");
        assertThat(service.callback(user,"openrouter","session",state,"code",null)).isTrue();
        long course = curriculum(user);
        var input = json.createObjectNode().put("curriculumId",Long.toString(course)).put("date",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());
        var reply = ai.create(user,"AI-004","DAILY_FEEDBACK",input,UUID.randomUUID());
        long job = reply.data().path("jobId").asLong();
        var row = jdbc.queryForMap("select * from daily_career.ai_job where id=?",job);
        assertThat(row.get("provider")).isEqualTo("OPENROUTER");
        assertThat(row.get("browser_credential_ref").toString()).isEqualTo(service.reference(user,"openrouter"));
        assertThat(json.readTree(row.get("input_snapshot").toString()).path("learningRecords").size()).isEqualTo(1);
        assertThat(ai.usage(user).get("enabled")).isEqualTo(true);
        service.disconnect(user,"openrouter",1);
        assertThat(ai.usage(user).get("enabled")).isEqualTo(false);
        assertThat(jdbc.queryForObject("select status from daily_career.ai_job where id=?",String.class,job)).isEqualTo("CANCELED");
    }
    @Test void doesNotQueueDailyFeedbackWithoutStartedLearning() {
        long user = user();
        String url = service.authorize(user,"openrouter","session").authorizationUrl();
        String state = param(param(url,"callback_url"),"state");
        assertThat(service.callback(user,"openrouter","session",state,"code",null)).isTrue();
        long course = curriculum(user);
        jdbc.update("update daily_career.learning_day_item set status='NOT_STARTED',completed_at=null where user_id=? and user_curriculum_id=?",user,course);
        var input = json.createObjectNode().put("curriculumId",Long.toString(course))
                .put("date",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());

        assertThatThrownBy(() -> ai.create(user,"AI-004","DAILY_FEEDBACK",input,UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, failure ->
                        assertThat(failure.code()).isEqualTo(ApiErrorCode.AI_INPUT_NOT_ELIGIBLE));
        assertThat(jdbc.queryForObject("select count(*) from daily_career.ai_job where user_id=?",Integer.class,user)).isZero();
    }
    private long curriculum(long user) {
        long curriculum = jdbc.queryForObject("insert into daily_career.curriculum(code,title) values (?, 'AI test') returning id",Long.class,"AI-"+UUID.randomUUID());
        long version = jdbc.queryForObject("insert into daily_career.curriculum_version(curriculum_id,version_no,status,published_at) values (?,1,'PUBLISHED',clock_timestamp()) returning id",Long.class,curriculum);
        long course = jdbc.queryForObject("insert into daily_career.user_curriculum(user_id,curriculum_version_id,start_date,target_end_date,projected_end_date,status) values (?,?,current_date,current_date+30,current_date+30,'ACTIVE') returning id",Long.class,user,version);
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        long day = jdbc.queryForObject("insert into daily_career.learning_day(user_id,user_curriculum_id,local_date) values (?,?,?) returning id",Long.class,user,course,today);
        jdbc.update("insert into daily_career.learning_day_item(user_id,user_curriculum_id,curriculum_version_id,learning_day_id,order_no,activity_type,status,completed_at) values (?,?,?,?,1,'LEARN','COMPLETED',clock_timestamp())",user,course,version,day);
        return course;
    }
    private long user() {
        long id = jdbc.queryForObject("insert into daily_career.app_user(display_name,email) values ('oauth-test',?) returning id",Long.class,UUID.randomUUID()+"@example.test");
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)",id); return id;
    }
    private static String param(String url, String name) {
        return Arrays.stream(URI.create(url).getRawQuery().split("&")).map(p -> p.split("=",2)).filter(p -> p[0].equals(name))
                .map(p -> URLDecoder.decode(p[1],StandardCharsets.UTF_8)).findFirst().orElseThrow();
    }
    @TestConfiguration(proxyBeanMethods=false) static class Fakes {
        @Bean @Primary Fake provider() { return new Fake(); }
    }
    static class Fake implements BrowserOAuthProvider {
        int calls; String verifier; Runnable duringExchange = () -> {};
        public Grant exchange(String provider, String code, String verifier) {
            calls++; this.verifier=verifier; duringExchange.run(); var json = new ObjectMapper();
            return provider.equals("slack") ? new Grant(json.createObjectNode().put("webhook","https://hooks.slack.com/services/T/B/TEST"),
                    json.createObjectNode().put("channelName","#learning").put("workspaceName","Test").put("workspaceId","T"))
                    : new Grant(json.createObjectNode().put("key","test-api-key"),json.createObjectNode().put("managementUrl","https://openrouter.ai/keys/test"));
        }
    }
}
