package dev.dailycareer.integration.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.integration.browser.HttpBrowserOAuthProvider.encode;

@Service
public class BrowserConnections {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final BrowserOAuthSettings settings;
    private final BrowserSecretCipher cipher;
    private final BrowserOAuthProvider provider;
    private final UserAccountService users;
    private final TransactionTemplate tx;
    private static final SecureRandom RANDOM = new SecureRandom();

    public BrowserConnections(JdbcTemplate jdbc, ObjectMapper json, BrowserOAuthSettings settings,
            BrowserSecretCipher cipher, BrowserOAuthProvider provider, UserAccountService users, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.json = json; this.settings = settings; this.cipher = cipher;
        this.provider = provider; this.users = users; this.tx = new TransactionTemplate(manager);
    }
    public record Connection(boolean configured, boolean connected, long revision, String channelName,
                             String workspaceName, String model, String managementUrl) {}
    public record Authorization(String authorizationUrl, OffsetDateTime expiresAt) {}
    public record Disconnected(boolean disconnected, String providerRevocationStatus, String managementUrl) {}

    public Connection status(long user, String name) {
        check(name); users.current(user);
        var rows = rows(user, name);
        var row = rows.isEmpty() ? null : rows.getFirst();
        JsonNode metadata = row == null ? json.createObjectNode() : read(row.get("metadata").toString());
        return new Connection(settings.ready(name), row != null && row.get("credential_ref") != null,
                row == null ? 0 : number(row, "revision"), nullable(metadata, "channelName"), nullable(metadata, "workspaceName"),
                name.equals("openrouter") ? settings.openRouterModel() : null, nullable(metadata, "managementUrl"));
    }

    public Authorization authorize(long user, String name, String session) {
        check(name); users.current(user);
        if (!settings.ready(name)) throw new ApiException(SERVICE_UNAVAILABLE);
        return tx.execute(status -> {
            var current = lock(user, name);
            String state = random(), verifier = random(), stateHash = hash(state);
            OffsetDateTime expires = OffsetDateTime.now().plusMinutes(10);
            jdbc.update("delete from daily_career.browser_oauth_state where expires_at<clock_timestamp()");
            jdbc.update("update daily_career.browser_oauth_state set consumed_at=clock_timestamp() where user_id=? and provider=? and consumed_at is null", user, name);
            jdbc.update("""
                    insert into daily_career.browser_oauth_state(state_hash,user_id,provider,session_hash,
                        connection_revision,verifier_ciphertext,expires_at) values (?,?,?,?,?,?,?)
                    """, stateHash, user, name, hash(session), current.get("revision"),
                    cipher.encrypt(user + ":" + name + ":" + stateHash, verifier), expires);
            String url;
            if (name.equals("slack")) url = "https://slack.com/oauth/v2/authorize?scope=incoming-webhook&client_id="
                    + encode(settings.slackClientId()) + "&redirect_uri=" + encode(settings.callback(name)) + "&state=" + encode(state);
            else url = "https://openrouter.ai/auth?callback_url=" + encode(settings.callback(name) + "?state=" + state)
                    + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256&key_label=Daily%20Leap";
            return new Authorization(url, expires);
        });
    }

    public boolean callback(long user, String name, String session, String state, String code, String error) {
        check(name);
        if (state == null || state.length() > 200 || session == null || !settings.ready(name)) return false;
        // Claim once before any network request. Wrong user/session cannot consume another person's state.
        var claims = jdbc.queryForList("""
                update daily_career.browser_oauth_state set consumed_at=clock_timestamp()
                where state_hash=? and provider=? and user_id=? and session_hash=?
                    and consumed_at is null and expires_at>clock_timestamp() returning *
                """, hash(state), name, user, hash(session));
        if (claims.isEmpty() || error != null || code == null || code.isBlank() || code.length() > 4096) return false;
        var claim = claims.getFirst();
        try {
            String verifier = cipher.decrypt(user + ":" + name + ":" + hash(state), claim.get("verifier_ciphertext").toString());
            var grant = provider.exchange(name, code, verifier); // deliberately outside a DB transaction
            return Boolean.TRUE.equals(tx.execute(status -> {
                var current = lock(user, name);
                if (number(current, "revision") != number(claim, "connection_revision")) return false;
                UUID reference = UUID.randomUUID();
                String encrypted = cipher.encrypt(user + ":" + name + ":" + reference, grant.secret().toString());
                jdbc.update("""
                        update daily_career.browser_connection set credential_ref=?,credential_ciphertext=?,metadata=?::jsonb,
                            revision=revision+1,updated_at=clock_timestamp() where user_id=? and provider=?
                        """, reference, encrypted, grant.metadata().toString(), user, name);
                if (name.equals("slack")) {
                    disconnectSlack(user);
                    var config = json.createObjectNode().put("enabled", false)
                            .put("channelName", grant.metadata().path("channelName").asText());
                    config.putArray("eventTypes").add("STUDY_REMINDER").add("WEEKLY_REPORT").add("INTEGRATION_FAILURE");
                    jdbc.update("""
                            insert into daily_career.integration_connection(user_id,provider,status,credential_ref,config,api_version,external_workspace_id)
                            values (?,'SLACK','ACTIVE',?,?::jsonb,'oauth-webhook-v1',?)
                            """, user, reference.toString(), config.toString(), grant.metadata().path("workspaceId").asText());
                }
                return true;
            }));
        } catch (Exception failed) {
            if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
            // Never expose provider bodies, codes, keys or webhook URLs in an error/log.
            return false;
        }
    }

    public Disconnected disconnect(long user, String name, long expected) {
        check(name); users.current(user);
        return tx.execute(status -> {
            var current = lock(user, name);
            if (number(current, "revision") != expected) throw new ApiException(PRECONDITION_FAILED);
            String management = name.equals("slack") ? "https://slack.com/apps/manage"
                    : nullable(read(current.get("metadata").toString()), "managementUrl");
            jdbc.update("""
                    update daily_career.browser_connection set credential_ref=null,credential_ciphertext=null,
                        revision=revision+1,updated_at=clock_timestamp() where user_id=? and provider=?
                    """, user, name);
            jdbc.update("update daily_career.browser_oauth_state set consumed_at=clock_timestamp() where user_id=? and provider=? and consumed_at is null", user, name);
            if (name.equals("slack")) disconnectSlack(user);
            else jdbc.update("""
                    update daily_career.ai_job set status='CANCELED',error_code='AI_CONNECTION_REMOVED',finished_at=clock_timestamp(),
                        revision=revision+1,updated_at=clock_timestamp() where user_id=? and provider='OPENROUTER' and status='QUEUED'
                    """, user);
            return new Disconnected(true, "MANUAL_REQUIRED", management);
        });
    }
    private void disconnectSlack(long user) {
        jdbc.update("""
                update daily_career.notification_delivery set status='CANCELED',last_error_code='SLACK_DISCONNECTED',
                    updated_at=clock_timestamp(),revision=revision+1 where user_id=? and channel='SLACK' and status='QUEUED'
                """, user);
        jdbc.update("""
                update daily_career.integration_connection set status='DISCONNECTED',credential_ref=null,disconnected_at=clock_timestamp(),
                    revision=revision+1,updated_at=clock_timestamp() where user_id=? and provider='SLACK' and status='ACTIVE'
                """, user);
    }
    public String reference(long user, String name) {
        if (!settings.ready(name)) return null;
        var rows = rows(user, name);
        return rows.isEmpty() || rows.getFirst().get("credential_ref") == null ? null : rows.getFirst().get("credential_ref").toString();
    }
    public JsonNode credential(long user, String name, String reference) {
        var rows = jdbc.queryForList("select * from daily_career.browser_connection where user_id=? and provider=? and credential_ref::text=?", user, name, reference);
        if (rows.isEmpty() || reference == null || !settings.ready(name)) throw new IllegalStateException("Connection unavailable");
        return read(cipher.decrypt(user + ":" + name + ":" + reference, rows.getFirst().get("credential_ciphertext").toString()));
    }
    public String slackWebhook(String reference) {
        var rows = jdbc.queryForList("select user_id from daily_career.browser_connection where provider='slack' and credential_ref::text=?", reference);
        if (rows.isEmpty()) throw new IllegalStateException("Slack connection unavailable");
        return credential(number(rows.getFirst(), "user_id"), "slack", reference).path("webhook").asText();
    }
    public String model() { return settings.openRouterModel(); }
    private List<Map<String,Object>> rows(long user, String name) {
        return jdbc.queryForList("select * from daily_career.browser_connection where user_id=? and provider=?", user, name);
    }
    private Map<String,Object> lock(long user, String name) {
        jdbc.update("insert into daily_career.browser_connection(user_id,provider) values (?,?) on conflict do nothing", user, name);
        return jdbc.queryForMap("select * from daily_career.browser_connection where user_id=? and provider=? for update", user, name);
    }
    private JsonNode read(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException("Invalid connection data"); } }
    private static String nullable(JsonNode value, String key) { return value.path(key).isTextual() ? value.path(key).asText() : null; }
    private static long number(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private static void check(String name) { if (!Set.of("slack", "openrouter").contains(name)) throw new ApiException(RESOURCE_NOT_FOUND); }
    private static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String value) { return HexFormat.of().formatHex(digest(value)); }
    static String challenge(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(value)); }
    private static byte[] digest(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
}
