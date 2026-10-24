package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.integration.IntegrationViews.NotionConnection;
import dev.dailycareer.user.UserAccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.integration.notion.NotionViews.*;

@Service
public class NotionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final ObjectMapper json;
    private final IdempotencyService idempotency;
    private final NotionSettings settings;
    private final NotionCredentialStore credentials;
    private final NotionProvider provider;
    private final TransactionTemplate transaction;

    public NotionService(JdbcTemplate jdbc, UserAccountService users, ObjectMapper json,
                         IdempotencyService idempotency, NotionSettings settings,
                         NotionCredentialStore credentials, NotionProvider provider,
                         PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.users = users;
        this.json = json;
        this.idempotency = idempotency;
        this.settings = settings;
        this.credentials = credentials;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public boolean configured() { return settings.enabled(); }

    @Transactional(readOnly = true)
    public NotionConnection connection(long user) {
        users.current(user);
        List<Map<String, Object>> rows = connectionRows(user);
        if (rows.isEmpty()) return new NotionConnection(false, "DISCONNECTED", null, null,
                NotionSettings.VERSION, 0);
        Map<String, Object> row = rows.getFirst();
        String state = row.get("status").toString();
        boolean connected = "ACTIVE".equals(state) && row.get("credential_ref") != null;
        String status = connected ? "CONNECTED" : ("ACTIVE".equals(state) || "ERROR".equals(state))
                ? "REAUTH_REQUIRED" : "DISCONNECTED";
        JsonNode config = parse(row.get("config"));
        return new NotionConnection(connected, status, text(config, "workspaceName"),
                string(row.get("external_workspace_id")), NotionSettings.VERSION, number(row, "revision"));
    }

    public StoredReply authorization(long user, String sessionId, UUID key) {
        users.current(user);
        if (!settings.enabled()) throw new ApiException(SERVICE_UNAVAILABLE);
        ObjectNode fingerprint = json.createObjectNode().put("session", hash(sessionId));
        return idempotency.execute(user, "NOTION-006", key, fingerprint,
                () -> createAuthorization(user, sessionId));
    }

    private StoredReply createAuthorization(long user, String sessionId) {
        int revision = currentConnectionRevision(user);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        OffsetDateTime expiresAt = OffsetDateTime.now(SEOUL).plusMinutes(10);
        jdbc.update("""
                insert into daily_career.notion_oauth_state
                    (state_hash,user_id,session_hash,connection_revision,expires_at)
                values (?,?,?,?,?)
                """, hash(state), user, hash(sessionId), revision, expiresAt);
        return new StoredReply(201, json.valueToTree(new Authorization(settings.authorizationUrl(state), expiresAt)), null, null);
    }

    public boolean completeAuthorization(long user, String sessionId, String state, String code) {
        users.current(user);
        OAuthClaim claim;
        try {
            claim = transaction.execute(status -> claim(user, sessionId, state));
        } catch (RuntimeException failure) {
            return false;
        }
        if (claim == null) return false;
        NotionProvider.OAuthToken token;
        try {
            token = provider.exchange(code);
        } catch (RuntimeException failure) {
            safeConsumeState(claim.stateHash());
            return false;
        }
        try {
            transaction.executeWithoutResult(status -> finishAuthorization(user, claim, token));
            return true;
        } catch (RuntimeException failure) {
            try { provider.revoke(token.accessToken()); }
            catch (RuntimeException ignored) {
                // The new token was never persisted. Best-effort revocation must not expose callback details.
            }
            safeConsumeState(claim.stateHash());
            return false;
        }
    }

    public void rejectAuthorization(long user, String sessionId, String state) {
        if (user <= 0 || sessionId == null || state == null) return;
        try {
            OAuthClaim claim = transaction.execute(status -> claim(user, sessionId, state));
            if (claim != null) transaction.executeWithoutResult(status -> consumeState(claim.stateHash()));
        } catch (RuntimeException ignored) {
            // Callback returns only a fixed failure redirect; state validation details are never exposed.
        }
    }

    @Transactional
    public DisconnectResult disconnect(long user) {
        users.current(user);
        List<Map<String, Object>> rows = connectionRows(user);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().get("status"))) {
            return new DisconnectResult(true, "NOT_REQUIRED");
        }
        Map<String, Object> row = rows.getFirst();
        long connectionId = id(row, "id");
        String reference = string(row.get("credential_ref"));
        jdbc.update("""
                update daily_career.integration_connection
                set status='DISCONNECTED',credential_ref=null,disconnected_at=clock_timestamp(),
                    updated_at=clock_timestamp(),revision=revision+1 where id=? and user_id=? and status='ACTIVE'
                """, connectionId, user);
        if (reference == null) return new DisconnectResult(true, "NOT_REQUIRED");
        jdbc.update("""
                insert into daily_career.notion_revocation(user_id,credential_ref)
                values (?,?::uuid) on conflict (credential_ref) do nothing
                """, user, reference);
        return new DisconnectResult(true, "PENDING");
    }

    private OAuthClaim claim(long user, String sessionId, String state) {
        String stateHash = hash(state);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select * from daily_career.notion_oauth_state where state_hash=? for update
                """, stateHash);
        if (rows.isEmpty()) throw new IllegalStateException("OAuth state is invalid");
        Map<String, Object> row = rows.getFirst();
        if (id(row, "user_id") != user || !MessageDigest.isEqual(row.get("session_hash").toString().getBytes(StandardCharsets.US_ASCII),
                hash(sessionId).getBytes(StandardCharsets.US_ASCII)) || !"READY".equals(row.get("status"))
                || time(row.get("expires_at")).isBefore(OffsetDateTime.now(SEOUL))
                || number(row, "connection_revision") != currentConnectionRevision(user)) {
            throw new IllegalStateException("OAuth state is invalid");
        }
        jdbc.update("update daily_career.notion_oauth_state set status='PROCESSING' where state_hash=? and status='READY'", stateHash);
        return new OAuthClaim(stateHash, number(row, "connection_revision"));
    }

    private void finishAuthorization(long user, OAuthClaim claim, NotionProvider.OAuthToken token) {
        String reference = credentials.save(user, token.accessToken());
        List<Map<String, Object>> rows = connectionRows(user);
        if (rows.isEmpty()) {
            jdbc.update("""
                    insert into daily_career.integration_connection
                        (user_id,provider,status,credential_ref,external_workspace_id,config,api_version,revision)
                    values (?,'NOTION','ACTIVE',?,?,?::jsonb,?,1)
                    """, user, reference, token.workspaceId(), write(Map.of("workspaceName",
                    token.workspaceName() == null ? "Notion workspace" : token.workspaceName())), NotionSettings.VERSION);
        } else {
            Map<String, Object> row = rows.getFirst();
            if (number(row, "revision") != claim.connectionRevision()) throw new IllegalStateException("Connection changed during OAuth");
            String oldReference = string(row.get("credential_ref"));
            int changed = jdbc.update("""
                    update daily_career.integration_connection set status='ACTIVE',credential_ref=?,external_workspace_id=?,
                        config=?::jsonb,api_version=?,disconnected_at=null,updated_at=clock_timestamp(),revision=revision+1
                    where id=? and user_id=? and revision=?
                    """, reference, token.workspaceId(), write(Map.of("workspaceName",
                    token.workspaceName() == null ? "Notion workspace" : token.workspaceName())), NotionSettings.VERSION,
                    id(row, "id"), user, claim.connectionRevision());
            if (changed != 1) throw new IllegalStateException("Connection changed during OAuth");
            if (oldReference != null && !oldReference.equals(reference)) jdbc.update("""
                    insert into daily_career.notion_revocation(user_id,credential_ref)
                    values (?,?::uuid) on conflict (credential_ref) do nothing
                    """, user, oldReference);
        }
        consumeState(claim.stateHash());
    }

    private void consumeState(String stateHash) {
        jdbc.update("""
                update daily_career.notion_oauth_state set status='CONSUMED',consumed_at=clock_timestamp()
                where state_hash=? and status in ('READY','PROCESSING')
                """, stateHash);
    }

    private void safeConsumeState(String stateHash) {
        try { transaction.executeWithoutResult(status -> consumeState(stateHash)); }
        catch (RuntimeException ignored) {
            // Callback always returns a fixed application redirect and never exposes storage details.
        }
    }

    private List<Map<String, Object>> connectionRows(long user) {
        return jdbc.queryForList("""
                select * from daily_career.integration_connection
                where user_id=? and provider='NOTION' order by id desc limit 1
                """, user);
    }

    private int currentConnectionRevision(long user) {
        Integer value = jdbc.queryForObject("""
                select coalesce((select revision::int from daily_career.integration_connection
                    where user_id=? and provider='NOTION' order by id desc limit 1),0)
                """, Integer.class, user);
        return value == null ? 0 : value;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Cannot write Notion data", failure); }
    }
    private JsonNode parse(Object value) {
        try { return json.readTree(value.toString()); }
        catch (Exception failure) { throw new IllegalStateException("Cannot read Notion data", failure); }
    }
    private static String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required bound value");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String text(JsonNode value, String key) { return value.path(key).isTextual() ? value.path(key).asText() : null; }
    private static OffsetDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atZone(SEOUL).toOffsetDateTime();
        return ((OffsetDateTime) value).atZoneSameInstant(SEOUL).toOffsetDateTime();
    }

    private record OAuthClaim(String stateHash, int connectionRevision) {}
}
