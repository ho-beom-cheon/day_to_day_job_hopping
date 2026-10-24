package dev.dailycareer.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.user.UserAccountService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.dailycareer.notification.push.PushViews.*;

@Service
public class PushSubscriptionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final ObjectMapper json;
    private final IdempotencyService idempotency;
    private final WebPushSettings settings;
    private final PushEndpointPolicy endpointPolicy;
    private final SubscriptionCipher cipher;

    public PushSubscriptionService(JdbcTemplate jdbc, UserAccountService users, ObjectMapper json,
                                   IdempotencyService idempotency, WebPushSettings settings,
                                   PushEndpointPolicy endpointPolicy, SubscriptionCipher cipher) {
        this.jdbc = jdbc;
        this.users = users;
        this.json = json;
        this.idempotency = idempotency;
        this.settings = settings;
        this.endpointPolicy = endpointPolicy;
        this.cipher = cipher;
    }

    public StoredReply subscribe(long user, PushSubscribe input, UUID idempotencyKey) {
        users.current(user);
        if (!settings.enabled()) throw new ApiException(ApiErrorCode.PUSH_DISABLED);
        Validated validated = validate(input);
        return idempotency.execute(user, "NOTI-007", idempotencyKey, json.valueToTree(input),
                () -> store(user, validated));
    }

    private StoredReply store(long user, Validated input) {
        List<Map<String, Object>> existing = jdbc.queryForList(
                "select id,user_id from daily_career.push_subscription where endpoint_hash=? for update", input.endpointHash());
        Long id = null;
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            if (((Number) row.get("user_id")).longValue() != user) throw new ApiException(ApiErrorCode.PUSH_ENDPOINT_REJECTED);
            id = ((Number) row.get("id")).longValue();
        }
        jdbc.update("""
                update daily_career.push_subscription
                set status='REVOKED',updated_at=clock_timestamp(),revision=revision+1
                where user_id=? and device_id=? and status='ACTIVE' and (?::bigint is null or id<>?::bigint)
                """, user, input.deviceId(), id, id);
        var endpoint = cipher.encrypt(input.endpoint().toASCIIString(), "endpoint");
        var auth = cipher.encrypt(input.auth(), "auth");
        Timestamp expiration = input.expirationTime() == null ? null : Timestamp.from(Instant.ofEpochMilli(input.expirationTime()));
        if (id == null) {
            id = jdbc.queryForObject("""
                    insert into daily_career.push_subscription
                    (user_id,device_id,endpoint_hash,endpoint_ciphertext,auth_ciphertext,key_id,p256dh,device_label,status,expiration_at)
                    values (?,?,?,?,?,?,?,?,'ACTIVE',?) returning id
                    """, Long.class, user, input.deviceId(), input.endpointHash(), endpoint.bytes(), auth.bytes(),
                    endpoint.keyId(), input.p256dh(), input.deviceName(), expiration);
        } else {
            jdbc.update("""
                    update daily_career.push_subscription
                    set device_id=?,device_label=?,endpoint_ciphertext=?,auth_ciphertext=?,key_id=?,p256dh=?,
                        expiration_at=?,status='ACTIVE',updated_at=clock_timestamp(),revision=revision+1
                    where id=? and user_id=?
                    """, input.deviceId(), input.deviceName(), endpoint.bytes(), auth.bytes(), endpoint.keyId(),
                    input.p256dh(), expiration, id, user);
        }
        PushSubscription result = byId(user, id);
        return new StoredReply(201, json.valueToTree(result), null, "/api/v1/push/subscriptions/" + id);
    }

    @Transactional
    public List<PushSubscription> list(long user) {
        users.current(user);
        jdbc.update("""
                update daily_career.push_subscription
                set status='EXPIRED',updated_at=clock_timestamp(),revision=revision+1
                where user_id=? and status='ACTIVE' and expiration_at is not null and expiration_at<=clock_timestamp()
                """, user);
        return jdbc.query("""
                select id,device_id,device_label,status,created_at,updated_at
                from daily_career.push_subscription where user_id=? and device_id is not null
                order by updated_at desc,id desc limit 20
                """, (row, number) -> view(row.getLong("id"), row.getObject("device_id", UUID.class),
                row.getString("device_label"), row.getString("status"), row.getObject("created_at"),
                row.getObject("updated_at")), user);
    }

    @Transactional
    public DeletedResult delete(long user, long subscriptionId) {
        users.current(user);
        jdbc.update("""
                update daily_career.push_subscription set status='REVOKED',updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and status='ACTIVE'
                """, subscriptionId, user);
        jdbc.update("""
                update daily_career.notification_delivery
                set status='CANCELED',last_error_code='PUSH_SUBSCRIPTION_REVOKED',updated_at=clock_timestamp(),revision=revision+1
                where push_subscription_id=? and user_id=? and status='QUEUED'
                """, subscriptionId, user);
        return new DeletedResult(true);
    }

    private PushSubscription byId(long user, long id) {
        return jdbc.queryForObject("""
                select id,device_id,device_label,status,created_at,updated_at
                from daily_career.push_subscription where id=? and user_id=?
                """, (row, number) -> view(row.getLong("id"), row.getObject("device_id", UUID.class),
                row.getString("device_label"), row.getString("status"), row.getObject("created_at"),
                row.getObject("updated_at")), id, user);
    }

    private Validated validate(PushSubscribe input) {
        if (input == null || input.deviceId() == null || input.deviceName() == null || input.endpoint() == null
                || input.keys() == null || input.keys().p256dh() == null || input.keys().auth() == null) validation();
        String deviceName = input.deviceName().strip();
        if (deviceName.isEmpty() || deviceName.length() > 80 || input.expirationTime() != null
                && (input.expirationTime() < 0 || input.expirationTime() > 9_007_199_254_740_991L)) validation();
        byte[] publicKey = decode(input.keys().p256dh(), 65);
        decode(input.keys().auth(), 16);
        if (publicKey[0] != 4 || !WebPushCryptography.validClientPublicKey(publicKey)) rejected();
        URI endpoint = endpointPolicy.validate(input.endpoint());
        return new Validated(input.deviceId(), deviceName, endpoint, input.expirationTime(),
                input.keys().p256dh(), input.keys().auth(), hash(endpoint.toASCIIString()));
    }

    private static byte[] decode(String value, int expected) {
        try {
            if (value.isEmpty() || !value.matches("[A-Za-z0-9_-]+")) rejected();
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != expected) rejected();
            return decoded;
        } catch (IllegalArgumentException invalid) {
            rejected();
            throw invalid;
        }
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static PushSubscription view(long id, UUID deviceId, String name, String status, Object created, Object updated) {
        return new PushSubscription(id, deviceId, name, "ACTIVE".equals(status), time(created), time(updated));
    }
    private static OffsetDateTime time(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atZone(SEOUL).toOffsetDateTime();
        return ((OffsetDateTime) value).atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
    private static void validation() { throw new ApiException(ApiErrorCode.VALIDATION_FAILED); }
    private static void rejected() { throw new ApiException(ApiErrorCode.PUSH_ENDPOINT_REJECTED); }

    private record Validated(UUID deviceId, String deviceName, URI endpoint, Long expirationTime,
                             String p256dh, String auth, String endpointHash) {}
}
