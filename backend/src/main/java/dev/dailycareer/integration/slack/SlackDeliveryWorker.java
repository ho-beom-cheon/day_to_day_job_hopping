package dev.dailycareer.integration.slack;

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
public final class SlackDeliveryWorker {
    private final JdbcTemplate jdbc;
    private final SlackSettings settings;
    private final SlackProvider provider;
    private final TransactionTemplate transaction;

    public SlackDeliveryWorker(JdbcTemplate jdbc, SlackSettings settings, SlackProvider provider,
                               PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${daily-career.slack.poll-delay-ms:3000}")
    void poll() {
        transaction.executeWithoutResult(status -> recoverUnknown());
        for (int index = 0; index < 20; index++) {
            Claimed claimed = transaction.execute(status -> claim());
            if (claimed == null) return;
            deliver(claimed);
        }
    }

    private void recoverUnknown() {
        jdbc.update("""
                update daily_career.notification_delivery
                set status='UNKNOWN',last_error_code='SLACK_PROVIDER_RESULT_UNKNOWN',lease_token=null,lease_until=null,
                    updated_at=clock_timestamp(),revision=revision+1
                where channel='SLACK' and status='SENDING' and lease_until<clock_timestamp()
                """);
    }

    private Claimed claim() {
        List<Claimed> rows = jdbc.query("""
                select delivery.id,delivery.attempt_count,connection.credential_ref,
                       notification.event_type,notification.title,notification.body,notification.deep_link
                from daily_career.notification_delivery delivery
                join daily_career.integration_connection connection
                  on connection.id=delivery.integration_connection_id and connection.user_id=delivery.user_id
                join daily_career.notification notification
                  on notification.id=delivery.notification_id and notification.user_id=delivery.user_id
                where delivery.channel='SLACK' and delivery.status='QUEUED' and delivery.attempt_count<3
                  and (delivery.next_attempt_at is null or delivery.next_attempt_at<=clock_timestamp())
                  and connection.status='ACTIVE' and connection.credential_ref is not null
                  and (notification.event_type='TEST' or coalesce((connection.config->>'enabled')::boolean,false))
                order by delivery.next_attempt_at nulls first,delivery.id limit 1 for update of delivery skip locked
                """, (row, number) -> new Claimed(row.getLong("id"), row.getInt("attempt_count") + 1,
                row.getString("credential_ref"), row.getString("event_type"), row.getString("title"),
                row.getString("body"), row.getString("deep_link"), UUID.randomUUID().toString()));
        if (rows.isEmpty()) return null;
        Claimed claimed = rows.getFirst();
        int updated = jdbc.update("""
                update daily_career.notification_delivery
                set status='SENDING',attempt_count=attempt_count+1,lease_token=?,
                    lease_until=clock_timestamp()+interval '1 minute',updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='QUEUED'
                """, claimed.token(), claimed.deliveryId());
        return updated == 1 ? claimed : null;
    }

    private void deliver(Claimed claimed) {
        SlackProvider.Result result;
        try {
            URI webhook = settings.resolve(claimed.credentialRef());
            result = provider.send(webhook, new SlackProvider.Message(claimed.eventType(), claimed.title(),
                    claimed.body(), claimed.targetPath()));
        } catch (Exception failure) {
            result = SlackProvider.Result.REJECTED;
        }
        SlackProvider.Result completed = result;
        transaction.executeWithoutResult(status -> complete(claimed, completed));
    }

    private void complete(Claimed claimed, SlackProvider.Result result) {
        switch (result) {
            case ACCEPTED -> update(claimed, "SENT", null, null, true);
            case RETRYABLE -> {
                if (claimed.attempt() < 3) update(claimed, "QUEUED", "SLACK_PROVIDER_RETRYABLE",
                        Duration.ofSeconds(30L * claimed.attempt()), false);
                else update(claimed, "FAILED", "SLACK_PROVIDER_RETRY_EXHAUSTED", null, false);
            }
            case UNKNOWN -> update(claimed, "UNKNOWN", "SLACK_PROVIDER_RESULT_UNKNOWN", null, false);
            case REJECTED -> update(claimed, "FAILED", "SLACK_PROVIDER_REJECTED", null, false);
        }
    }

    private void update(Claimed claimed, String state, String error, Duration retryAfter, boolean sent) {
        jdbc.update("""
                update daily_career.notification_delivery
                set status=?,last_error_code=?,
                    next_attempt_at=case when ?::bigint is null then null else clock_timestamp()+(?::bigint*interval '1 second') end,
                    sent_at=case when ? then clock_timestamp() else sent_at end,lease_token=null,lease_until=null,
                    updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='SENDING' and lease_token=?
                """, state, error, retryAfter == null ? null : retryAfter.toSeconds(),
                retryAfter == null ? null : retryAfter.toSeconds(), sent, claimed.deliveryId(), claimed.token());
    }

    private record Claimed(long deliveryId, int attempt, String credentialRef, String eventType,
                           String title, String body, String targetPath, String token) {}
}
