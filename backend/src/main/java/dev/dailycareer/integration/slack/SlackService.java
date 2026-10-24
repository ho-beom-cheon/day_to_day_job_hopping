package dev.dailycareer.integration.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.user.UserAccountService;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static dev.dailycareer.common.api.ApiErrorCode.INVALID_SORT_FIELD;
import static dev.dailycareer.common.api.ApiErrorCode.RESOURCE_NOT_FOUND;
import static dev.dailycareer.common.api.ApiErrorCode.SLACK_NOT_CONFIGURED;
import static dev.dailycareer.common.api.ApiErrorCode.VALIDATION_FAILED;
import static dev.dailycareer.integration.slack.SlackViews.*;

@Service
public class SlackService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;
    private final SlackSettings settings;

    public SlackService(JdbcTemplate jdbc, UserAccountService users, IdempotencyService idempotency,
                        ObjectMapper json, SlackSettings settings) {
        this.jdbc = jdbc;
        this.users = users;
        this.idempotency = idempotency;
        this.json = json;
        this.settings = settings;
    }

    public StoredReply submitTest(long user, UUID key) {
        users.current(user);
        if (!settings.enabled(user)) throw new ApiException(SLACK_NOT_CONFIGURED);
        var execution = idempotency.executeWithReplay(user, "SLACK-003", key, json.createObjectNode(), () -> createTest(user, key));
        StoredReply reply = execution.reply();
        if (!execution.reused()) return reply;
        ObjectNode data = (ObjectNode) reply.data();
        data.put("reused", true);
        return new StoredReply(reply.status(), data, reply.etag(), reply.location());
    }

    @Transactional(readOnly = true)
    public SlackDeliveryPage list(long user, int page, int size, String sort) {
        users.current(user);
        if (page < 0 || size < 1 || size > 100) throw new ApiException(VALIDATION_FAILED);
        String order = order(sort);
        List<SlackDelivery> content = jdbc.query("""
                select delivery.id,notification.event_type,delivery.status,delivery.created_at,
                       delivery.sent_at,delivery.updated_at,delivery.last_error_code
                from daily_career.notification_delivery delivery
                join daily_career.notification notification
                  on notification.id=delivery.notification_id and notification.user_id=delivery.user_id
                where delivery.user_id=? and delivery.channel='SLACK'
                """ + " order by " + order + " limit ? offset ?", (row, number) -> view(row.getLong("id"),
                row.getString("event_type"), row.getString("status"), row.getObject("created_at"),
                row.getObject("sent_at"), row.getObject("updated_at"), row.getString("last_error_code")),
                user, size, (long) page * size);
        Integer totalValue = jdbc.queryForObject("select count(*)::int from daily_career.notification_delivery where user_id=? and channel='SLACK'", Integer.class, user);
        int total = totalValue == null ? 0 : totalValue;
        return new SlackDeliveryPage(content, page, size, total, total == 0 ? 0 : (total + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public SlackDelivery get(long user, long deliveryId) {
        users.current(user);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select delivery.id,notification.event_type,delivery.status,delivery.created_at,
                       delivery.sent_at,delivery.updated_at,delivery.last_error_code
                from daily_career.notification_delivery delivery
                join daily_career.notification notification
                  on notification.id=delivery.notification_id and notification.user_id=delivery.user_id
                where delivery.id=? and delivery.user_id=? and delivery.channel='SLACK'
                """, deliveryId, user);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        Map<String, Object> row = rows.getFirst();
        return view(id(row, "id"), row.get("event_type").toString(), row.get("status").toString(),
                row.get("created_at"), row.get("sent_at"), row.get("updated_at"),
                row.get("last_error_code") == null ? null : row.get("last_error_code").toString());
    }

    static String order(String value) {
        return switch (value == null ? "createdAt,desc" : value) {
            case "createdAt,desc" -> "delivery.created_at desc,delivery.id";
            case "createdAt,asc" -> "delivery.created_at,delivery.id";
            default -> throw new ApiException(INVALID_SORT_FIELD);
        };
    }

    private StoredReply createTest(long user, UUID key) {
        long connectionId = connection(user);
        TestRow row = jdbc.queryForObject("""
                with inserted_notification as (
                    insert into daily_career.notification(user_id,event_type,title,body,deep_link,dedupe_key,
                        scheduled_at,timezone,canceled_at)
                    values (?,'TEST','Slack 연결 테스트','Daily Leap의 고정 테스트 메시지입니다.',
                        '/settings/integrations',?,clock_timestamp(),'Asia/Seoul',clock_timestamp())
                    returning id
                )
                insert into daily_career.notification_delivery(user_id,notification_id,channel,
                    integration_connection_id,next_attempt_at)
                select ?,id,'SLACK',?,clock_timestamp() from inserted_notification
                returning id,created_at
                """, (result, number) -> new TestRow(result.getLong("id"), time(result.getObject("created_at"))),
                user, "slack:test:" + key, user, connectionId);
        if (row == null) throw new IllegalStateException("Slack test delivery was not created");
        String path = "/api/v1/integrations/slack/deliveries/" + row.id();
        return new StoredReply(202, json.valueToTree(new JobReceipt(row.id(), "READY", path, row.createdAt(), false)), null, path);
    }

    private long connection(long user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id,credential_ref from daily_career.integration_connection
                where user_id=? and provider='SLACK' and status='ACTIVE' order by id desc limit 1
                """, user);
        if (rows.isEmpty()) {
            Long id = jdbc.queryForObject("""
                    insert into daily_career.integration_connection(user_id,provider,status,credential_ref,config,api_version)
                    values (?,'SLACK','ACTIVE',?,'{"enabled":false,"eventTypes":[]}'::jsonb,'webhook-v1') returning id
                    """, Long.class, user, settings.credentialRef(user));
            if (id == null) throw new IllegalStateException("Slack connection id was not returned");
            return id;
        }
        Map<String, Object> row = rows.getFirst();
        long id = id(row, "id");
        if (!settings.credentialRef(user).equals(row.get("credential_ref"))) {
            jdbc.update("""
                    update daily_career.integration_connection
                    set credential_ref=?,updated_at=clock_timestamp(),revision=revision+1 where id=? and user_id=?
                    """, settings.credentialRef(user), id, user);
        }
        return id;
    }

    private static SlackDelivery view(long id, String eventType, String state, Object createdAt,
                                      Object sentAt, Object updatedAt, String errorCode) {
        OffsetDateTime completed = switch (state) {
            case "SENT" -> time(sentAt == null ? updatedAt : sentAt);
            case "FAILED", "UNKNOWN", "CANCELED" -> time(updatedAt);
            default -> null;
        };
        return new SlackDelivery(id, eventType, publicStatus(state), time(createdAt), completed, errorCode);
    }

    private static String publicStatus(String state) {
        return switch (state) {
            case "QUEUED" -> "READY";
            case "SENDING" -> "PROCESSING";
            case "SENT" -> "SUCCESS";
            case "UNKNOWN" -> "UNKNOWN";
            default -> "FAILED";
        };
    }

    private static long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static OffsetDateTime time(Object value) {
        return value instanceof Timestamp timestamp
                ? timestamp.toInstant().atZone(SEOUL).toOffsetDateTime()
                : ((OffsetDateTime) value).atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
    private record TestRow(long id, OffsetDateTime createdAt) {}
}
