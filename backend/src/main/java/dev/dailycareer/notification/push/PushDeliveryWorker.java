package dev.dailycareer.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class PushDeliveryWorker {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final WebPushSettings settings;
    private final SubscriptionCipher cipher;
    private final WebPushProvider provider;
    private final TransactionTemplate transaction;

    public PushDeliveryWorker(JdbcTemplate jdbc, ObjectMapper json, WebPushSettings settings,
                              SubscriptionCipher cipher, WebPushProvider provider,
                              PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.settings = settings;
        this.cipher = cipher;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${daily-career.push.poll-delay-ms:3000}")
    void poll() {
        if (!settings.enabled()) return;
        for (int index = 0; index < 20; index++) {
            Claimed claimed = transaction.execute(status -> claim());
            if (claimed == null) return;
            deliver(claimed);
        }
    }

    private Claimed claim() {
        List<Claimed> rows = jdbc.query("""
                select delivery.id,delivery.push_subscription_id,delivery.attempt_count,
                       subscription.endpoint_ciphertext,subscription.auth_ciphertext,subscription.key_id,subscription.p256dh,
                       notification.title,notification.body,notification.deep_link,notification.dedupe_key
                from daily_career.notification_delivery delivery
                join daily_career.push_subscription subscription
                  on subscription.id=delivery.push_subscription_id and subscription.user_id=delivery.user_id
                join daily_career.notification notification
                  on notification.id=delivery.notification_id and notification.user_id=delivery.user_id
                where delivery.channel='WEB_PUSH' and delivery.attempt_count<3
                  and (delivery.status='QUEUED' and (delivery.next_attempt_at is null or delivery.next_attempt_at<=clock_timestamp())
                       or delivery.status='SENDING' and delivery.lease_until<clock_timestamp())
                  and subscription.status='ACTIVE'
                  and (subscription.expiration_at is null or subscription.expiration_at>clock_timestamp())
                order by delivery.next_attempt_at nulls first,delivery.id limit 1 for update of delivery skip locked
                """, (row, number) -> new Claimed(row.getLong("id"), row.getLong("push_subscription_id"),
                row.getInt("attempt_count") + 1, row.getBytes("endpoint_ciphertext"), row.getBytes("auth_ciphertext"),
                row.getString("key_id"), row.getString("p256dh"), row.getString("title"), row.getString("body"),
                row.getString("deep_link"), row.getString("dedupe_key"), UUID.randomUUID().toString()));
        if (rows.isEmpty()) return null;
        Claimed claimed = rows.getFirst();
        int updated = jdbc.update("""
                update daily_career.notification_delivery
                set status='SENDING',attempt_count=attempt_count+1,lease_token=?,lease_until=clock_timestamp()+interval '1 minute',
                    updated_at=clock_timestamp(),revision=revision+1 where id=?
                """, claimed.token(), claimed.deliveryId());
        return updated == 1 ? claimed : null;
    }

    private void deliver(Claimed claimed) {
        WebPushProvider.Result result;
        try {
            String endpoint = cipher.decrypt(claimed.endpointCiphertext(), "endpoint", claimed.keyId());
            String auth = cipher.decrypt(claimed.authCiphertext(), "auth", claimed.keyId());
            byte[] payload = json.writeValueAsBytes(new Payload(claimed.title(), claimed.body(), claimed.targetPath(), claimed.tag()));
            result = provider.send(URI.create(endpoint), claimed.p256dh(), auth, payload);
        } catch (Exception failure) {
            result = WebPushProvider.Result.REJECTED;
        }
        WebPushProvider.Result completed = result;
        transaction.executeWithoutResult(status -> complete(claimed, completed));
    }

    private void complete(Claimed claimed, WebPushProvider.Result result) {
        switch (result) {
            case ACCEPTED -> {
                update(claimed, "SENT", null, null, true);
                jdbc.update("""
                        update daily_career.push_subscription
                        set last_success_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?
                        """, claimed.subscriptionId());
            }
            case EXPIRED -> {
                update(claimed, "FAILED", "PUSH_SUBSCRIPTION_EXPIRED", null, false);
                jdbc.update("""
                        update daily_career.push_subscription
                        set status='EXPIRED',updated_at=clock_timestamp(),revision=revision+1 where id=? and status='ACTIVE'
                        """, claimed.subscriptionId());
                jdbc.update("""
                        update daily_career.notification_delivery
                        set status='CANCELED',last_error_code='PUSH_SUBSCRIPTION_EXPIRED',updated_at=clock_timestamp(),revision=revision+1
                        where push_subscription_id=? and status='QUEUED'
                        """, claimed.subscriptionId());
            }
            case RETRYABLE -> {
                if (claimed.attempt() < 3) update(claimed, "QUEUED", "PUSH_PROVIDER_RETRYABLE",
                        Duration.ofSeconds(30L * claimed.attempt()), false);
                else update(claimed, "FAILED", "PUSH_PROVIDER_RETRY_EXHAUSTED", null, false);
            }
            case UNKNOWN -> update(claimed, "UNKNOWN", "PUSH_PROVIDER_RESULT_UNKNOWN", null, false);
            case REJECTED -> update(claimed, "FAILED", "PUSH_PROVIDER_REJECTED", null, false);
        }
    }

    private void update(Claimed claimed, String status, String error, Duration retryAfter, boolean sent) {
        jdbc.update("""
                update daily_career.notification_delivery
                set status=?,last_error_code=?,next_attempt_at=case when ?::bigint is null then null else clock_timestamp()+(?::bigint*interval '1 second') end,
                    sent_at=case when ? then clock_timestamp() else sent_at end,lease_token=null,lease_until=null,
                    updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='SENDING' and lease_token=?
                """, status, error, retryAfter == null ? null : retryAfter.toSeconds(),
                retryAfter == null ? null : retryAfter.toSeconds(), sent, claimed.deliveryId(), claimed.token());
    }

    private record Payload(String title, String body, String targetPath, String tag) {}
    private record Claimed(long deliveryId, long subscriptionId, int attempt, byte[] endpointCiphertext,
                           byte[] authCiphertext, String keyId, String p256dh, String title, String body,
                           String targetPath, String tag, String token) {}
}
