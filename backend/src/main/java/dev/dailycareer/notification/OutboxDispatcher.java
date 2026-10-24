package dev.dailycareer.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public OutboxDispatcher(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${daily-career.outbox.poll-delay-ms:2000}")
    void poll() {
        for (int index = 0; index < 20; index++) {
            Claimed event = transaction.execute(status -> claim());
            if (event == null) return;
            dispatchSafely(event);
        }
    }

    void dispatchSafely(Claimed event) {
        try {
            transaction.executeWithoutResult(status -> publish(event));
        } catch (RuntimeException failure) {
            transaction.executeWithoutResult(status -> release(event));
        }
    }

    private Claimed claim() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id,user_id,event_key,event_type,payload::text
                from daily_career.outbox_event
                where available_at<=clock_timestamp() and attempt_count<5
                  and (status='PENDING' or (status='PROCESSING' and lease_until<clock_timestamp()))
                order by available_at,id limit 1 for update skip locked
                """);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        long id = ((Number) row.get("id")).longValue();
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                update daily_career.outbox_event
                set status='PROCESSING',attempt_count=attempt_count+1,lease_token=?,lease_until=clock_timestamp()+interval '1 minute',updated_at=clock_timestamp(),revision=revision+1
                where id=?
                """, token, id);
        try {
            return new Claimed(id, row.get("user_id") == null ? null : ((Number) row.get("user_id")).longValue(),
                    row.get("event_key").toString(), row.get("event_type").toString(), json.readTree(row.get("payload").toString()), token);
        } catch (Exception invalidPayload) {
            throw new IllegalStateException("Invalid outbox payload", invalidPayload);
        }
    }

    private void publish(Claimed event) {
        if (event.key().startsWith("SCHEDULED:") && !scheduledConsentStillValid(event)) {
            markPublished(event);
            return;
        }
        Message message = messageFor(event.type());
        if (event.userId() != null && message != null) {
            Long notificationId = jdbc.queryForObject("""
                    insert into daily_career.notification(user_id,event_type,title,body,deep_link,dedupe_key,scheduled_at,event_local_date,timezone)
                    values (?,?,?,?,?,?,clock_timestamp(),?,'Asia/Seoul')
                    on conflict(user_id,dedupe_key) do update set dedupe_key=excluded.dedupe_key
                    returning id
                    """, Long.class, event.userId(), message.type(), message.title(), message.body(), message.targetPath(), event.key(),
                    event.key().startsWith("SCHEDULED:") ? java.time.LocalDate.parse(event.payload().path("date").asText()) : null);
            if (notificationId == null) throw new IllegalStateException("Notification id was not returned");
            routeDeliveries(event.userId(), notificationId);
        }
        markPublished(event);
    }

    private boolean scheduledConsentStillValid(Claimed event) {
        if (event.userId() == null) return false;
        java.time.Instant dueAt = java.time.Instant.parse(event.payload().path("dueAt").asText());
        return !jdbc.queryForList("""
                select 1 from daily_career.user_setting
                where user_id=? and scheduled_reminders_enabled and scheduled_reminders_enabled_at<=?
                for update
                """, Integer.class, event.userId(), java.sql.Timestamp.from(dueAt)).isEmpty();
    }

    private void markPublished(Claimed event) {
        int updated = jdbc.update("""
                update daily_career.outbox_event
                set status='PUBLISHED',published_at=coalesce(published_at,clock_timestamp()),lease_token=null,lease_until=null,updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='PROCESSING' and lease_token=?
                """, event.id(), event.token());
        if (updated != 1) throw new IllegalStateException("Outbox lease was lost");
    }

    private void routeDeliveries(long user, long notificationId) {
        jdbc.update("""
                insert into daily_career.notification_delivery(user_id,notification_id,channel,push_subscription_id,next_attempt_at)
                select subscription.user_id,?,'WEB_PUSH',subscription.id,clock_timestamp()
                from daily_career.push_subscription subscription
                join daily_career.user_setting setting on setting.user_id=subscription.user_id
                where subscription.user_id=? and subscription.status='ACTIVE'
                  and coalesce((setting.notification_settings->>'pushEnabled')::boolean,false)
                on conflict do nothing
                """, notificationId, user);
        jdbc.update("""
                insert into daily_career.notification_delivery(user_id,notification_id,channel,integration_connection_id,next_attempt_at)
                select connection.user_id,?,'SLACK',connection.id,clock_timestamp()
                from daily_career.integration_connection connection
                join daily_career.notification notification on notification.id=? and notification.user_id=connection.user_id
                where connection.user_id=? and connection.provider='SLACK' and connection.status='ACTIVE'
                  and coalesce((connection.config->>'enabled')::boolean,false)
                  and jsonb_exists(connection.config->'eventTypes',notification.event_type)
                on conflict do nothing
                """, notificationId, notificationId, user);
    }

    private void release(Claimed event) {
        jdbc.update("""
                update daily_career.outbox_event
                set status=case when attempt_count>=5 then 'DEAD' else 'PENDING' end,
                    available_at=clock_timestamp()+interval '30 seconds',lease_token=null,lease_until=null,
                    updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='PROCESSING' and lease_token=?
                """, event.id(), event.token());
    }

    static Message messageFor(String eventType) {
        return switch (eventType) {
            default -> null;
        };
    }

    record Claimed(long id, Long userId, String key, String type, JsonNode payload, String token) {}
    record Message(String type, String title, String body, String targetPath) {}
}
