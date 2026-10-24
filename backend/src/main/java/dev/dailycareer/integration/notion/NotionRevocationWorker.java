package dev.dailycareer.integration.notion;

import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class NotionRevocationWorker {
    private final JdbcTemplate jdbc;
    private final NotionSettings settings;
    private final NotionCredentialStore credentials;
    private final NotionProvider provider;
    private final TransactionTemplate transaction;

    NotionRevocationWorker(JdbcTemplate jdbc, NotionSettings settings, NotionCredentialStore credentials,
                           NotionProvider provider, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.credentials = credentials;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${daily-career.notion.revocation-delay-ms:10000}")
    void poll() {
        if (!settings.enabled()) return;
        for (int index = 0; index < 5; index++) {
            Revocation revocation = transaction.execute(status -> claim());
            if (revocation == null) return;
            try {
                provider.revoke(credentials.load(revocation.userId(), revocation.credentialRef()));
                transaction.executeWithoutResult(status -> complete(revocation));
            } catch (RuntimeException failure) {
                transaction.executeWithoutResult(status -> fail(revocation));
            }
        }
    }

    private Revocation claim() {
        List<Revocation> rows = jdbc.query("""
                select id,user_id,credential_ref::text,attempt_count from daily_career.notion_revocation
                where status='PENDING' and next_attempt_at<=clock_timestamp() and attempt_count<5
                order by id limit 1 for update skip locked
                """, (row, number) -> new Revocation(row.getLong(1), row.getLong(2), row.getString(3), row.getInt(4) + 1));
        if (rows.isEmpty()) return null;
        Revocation value = rows.getFirst();
        jdbc.update("""
                update daily_career.notion_revocation set attempt_count=attempt_count+1,
                    next_attempt_at=clock_timestamp()+interval '1 minute',updated_at=clock_timestamp() where id=?
                """, value.id());
        return value;
    }

    private void complete(Revocation value) {
        jdbc.update("delete from daily_career.notion_revocation where id=?", value.id());
        credentials.delete(value.userId(), value.credentialRef());
    }

    private void fail(Revocation value) {
        if (value.attempt() >= 5) {
            jdbc.update("""
                    update daily_career.notion_revocation set status='FAILED',last_error_code='NOTION_REVOCATION_FAILED',
                        updated_at=clock_timestamp(),completed_at=clock_timestamp() where id=?
                    """, value.id());
        } else {
            long seconds = Duration.ofMinutes(value.attempt()).toSeconds();
            jdbc.update("""
                    update daily_career.notion_revocation set last_error_code='NOTION_REVOCATION_RETRYABLE',
                        next_attempt_at=clock_timestamp()+(?::bigint*interval '1 second'),updated_at=clock_timestamp()
                    where id=?
                    """, seconds, value.id());
        }
    }

    private record Revocation(long id, long userId, String credentialRef, int attempt) {}
}
