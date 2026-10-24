package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class NotionSyncWorker {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final NotionSettings settings;
    private final NotionCredentialStore credentials;
    private final NotionProvider provider;
    private final TransactionTemplate transaction;

    NotionSyncWorker(JdbcTemplate jdbc, ObjectMapper json, NotionSettings settings,
                     NotionCredentialStore credentials, NotionProvider provider,
                     PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.settings = settings;
        this.credentials = credentials;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${daily-career.notion.poll-delay-ms:3000}")
    void poll() {
        if (!settings.enabled()) return;
        transaction.executeWithoutResult(status -> recoverAbandoned());
        for (int index = 0; index < 5; index++) {
            Long batch = transaction.execute(status -> claimBatch());
            if (batch == null) return;
            runBatch(batch);
        }
    }

    private void recoverAbandoned() {
        jdbc.update("""
                update daily_career.notion_sync_item set status='UNKNOWN',retryable=true,
                    error_code='NOTION_RESULT_UNKNOWN',error_message='외부 처리 결과를 확인하지 못했습니다.',
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where status='PROCESSING' and updated_at<clock_timestamp()-interval '2 minutes'
                """);
        List<Long> abandoned = jdbc.queryForList("""
                select id from daily_career.notion_sync_batch batch where status='PROCESSING'
                  and not exists(select 1 from daily_career.notion_sync_item item
                      where item.batch_id=batch.id and item.status in ('READY','PROCESSING'))
                """, Long.class);
        abandoned.forEach(this::finishBatch);
    }

    private Long claimBatch() {
        List<Long> rows = jdbc.queryForList("""
                select batch.id from daily_career.notion_sync_batch batch
                join daily_career.integration_connection connection
                  on connection.id=batch.connection_id and connection.user_id=batch.user_id
                join daily_career.notion_target_set target_set
                  on target_set.id=batch.target_set_id and target_set.user_id=batch.user_id
                where (batch.status='READY' or (batch.status='PROCESSING' and exists(
                        select 1 from daily_career.notion_sync_item ready_item
                        where ready_item.batch_id=batch.id and ready_item.status='READY')))
                  and connection.status='ACTIVE' and connection.credential_ref is not null
                  and connection.revision=batch.connection_revision and target_set.revision=batch.targets_revision
                order by batch.id limit 1 for update of batch skip locked
                """, Long.class);
        if (rows.isEmpty()) return null;
        long id = rows.getFirst();
        return jdbc.update("""
                update daily_career.notion_sync_batch set status='PROCESSING',
                    started_at=coalesce(started_at,clock_timestamp()),updated_at=clock_timestamp(),revision=revision+1
                where id=? and status in ('READY','PROCESSING')
                """, id) == 1 ? id : null;
    }

    private void runBatch(long batchId) {
        while (true) {
            Claimed item = transaction.execute(status -> claimItem(batchId));
            if (item == null) break;
            NotionProvider.WriteOutcome outcome;
            try {
                String token = credentials.load(item.userId(), item.credentialRef());
                outcome = provider.upsert(token, item.target(), item.sourceKey(), item.sourceRevision(),
                        item.payload(), item.externalObjectId());
                NotionProvider.WriteOutcome completed = outcome;
                transaction.executeWithoutResult(status -> complete(item, completed));
            } catch (NotionProvider.Failure failure) {
                transaction.executeWithoutResult(status -> fail(item, failure));
                if (failure.kind() == NotionProvider.FailureKind.REAUTH_REQUIRED) {
                    transaction.executeWithoutResult(status -> abortForReauth(batchId, item));
                    break;
                }
            } catch (RuntimeException failure) {
                transaction.executeWithoutResult(status -> fail(item,
                        new NotionProvider.Failure(NotionProvider.FailureKind.UNKNOWN)));
            }
        }
        transaction.executeWithoutResult(status -> finishBatch(batchId));
    }

    private Claimed claimItem(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select item.*,batch.connection_revision,batch.targets_revision,batch.connection_id,batch.target_set_id,
                       connection.credential_ref
                from daily_career.notion_sync_item item
                join daily_career.notion_sync_batch batch on batch.id=item.batch_id and batch.user_id=item.user_id
                join daily_career.integration_connection connection
                  on connection.id=batch.connection_id and connection.user_id=batch.user_id
                join daily_career.notion_target_set target_set
                  on target_set.id=batch.target_set_id and target_set.user_id=batch.user_id
                where item.batch_id=? and item.status='READY' and batch.status='PROCESSING'
                  and connection.status='ACTIVE' and connection.credential_ref is not null
                  and connection.revision=batch.connection_revision and target_set.revision=batch.targets_revision
                order by item.id limit 1 for update of item skip locked
                """, batchId);
        if (rows.isEmpty()) {
            fenceFailure(batchId);
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        long itemId = id(row, "id");
        if (jdbc.update("""
                update daily_career.notion_sync_item set status='PROCESSING',attempt_count=attempt_count+1,
                    started_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='READY'
                """, itemId) != 1) return null;
        String existing = string(row.get("external_object_id"));
        if (existing == null) existing = jdbc.query("""
                select external_object_id from daily_career.notion_sync_item
                where user_id=? and entity_type=? and source_id=? and external_object_id is not null
                  and status in ('CREATED','UPDATED','SKIPPED') and id<>? order by id desc limit 1
                """, result -> result.next() ? result.getString(1) : null,
                id(row, "user_id"), row.get("entity_type"), row.get("source_id"), itemId);
        return new Claimed(itemId, id(row, "user_id"), number(row, "connection_revision"),
                number(row, "targets_revision"), id(row, "connection_id"), id(row, "target_set_id"),
                row.get("credential_ref").toString(), row.get("source_key").toString(),
                id(row, "source_revision"), new NotionViews.Target(row.get("entity_type").toString(),
                row.get("database_id").toString(), row.get("data_source_id").toString()),
                parse(row.get("payload_snapshot")), existing);
    }

    private void fenceFailure(long batchId) {
        Boolean valid = jdbc.queryForObject("""
                select exists(
                    select 1 from daily_career.notion_sync_batch batch
                    join daily_career.integration_connection connection
                      on connection.id=batch.connection_id and connection.user_id=batch.user_id
                    join daily_career.notion_target_set target_set
                      on target_set.id=batch.target_set_id and target_set.user_id=batch.user_id
                    where batch.id=? and batch.status='PROCESSING' and connection.status='ACTIVE'
                      and connection.credential_ref is not null and connection.revision=batch.connection_revision
                      and target_set.revision=batch.targets_revision)
                """, Boolean.class, batchId);
        if (Boolean.TRUE.equals(valid)) return;
        jdbc.update("""
                update daily_career.notion_sync_item set status='FAILED',retryable=false,
                    error_code='NOTION_TARGET_CHANGED',error_message='연결 또는 대상 revision이 변경되었습니다.',
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where batch_id=? and status='READY'
                """, batchId);
    }

    private void complete(Claimed item, NotionProvider.WriteOutcome outcome) {
        if (!fenceValid(item)) {
            jdbc.update("""
                    update daily_career.notion_sync_item set status='FAILED',retryable=false,
                        error_code='NOTION_TARGET_CHANGED',error_message='외부 호출 후 연결 또는 대상이 변경되었습니다.',
                        completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                    where id=? and status='PROCESSING'
                    """, item.itemId());
            return;
        }
        jdbc.update("""
                update daily_career.notion_sync_item set status=?,retryable=false,external_object_id=?,
                    error_code=null,error_message=null,completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='PROCESSING'
                """, outcome.result().name(), outcome.externalObjectId(), item.itemId());
    }

    private void fail(Claimed item, NotionProvider.Failure failure) {
        String status = failure.kind() == NotionProvider.FailureKind.UNKNOWN ? "UNKNOWN" : "FAILED";
        boolean retryable = failure.kind() == NotionProvider.FailureKind.UNKNOWN
                || failure.kind() == NotionProvider.FailureKind.RETRYABLE;
        String code = switch (failure.kind()) {
            case REAUTH_REQUIRED -> "NOTION_REAUTH_REQUIRED";
            case INVALID_TARGET -> "NOTION_TARGET_INVALID";
            case RETRYABLE -> "NOTION_PROVIDER_UNAVAILABLE";
            case UNKNOWN -> "NOTION_RESULT_UNKNOWN";
            case REJECTED -> "NOTION_PROVIDER_REJECTED";
        };
        String message = switch (failure.kind()) {
            case UNKNOWN -> "외부 처리 결과를 확인하지 못했습니다.";
            case RETRYABLE -> "Notion이 일시적으로 요청을 처리하지 못했습니다.";
            case REAUTH_REQUIRED -> "Notion 재연결이 필요합니다.";
            case INVALID_TARGET -> "Notion 대상의 접근 권한 또는 속성이 변경되었습니다.";
            case REJECTED -> "Notion이 동기화 항목을 거부했습니다.";
        };
        jdbc.update("""
                update daily_career.notion_sync_item set status=?,retryable=?,error_code=?,error_message=?,
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='PROCESSING'
                """, status, retryable, code, message, item.itemId());
    }

    private void abortForReauth(long batchId, Claimed item) {
        jdbc.update("""
                update daily_career.integration_connection set status='ERROR',updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and revision=? and status='ACTIVE'
                """, item.connectionId(), item.userId(), item.connectionRevision());
        jdbc.update("""
                update daily_career.notion_sync_item set status='FAILED',retryable=false,
                    error_code='NOTION_REAUTH_REQUIRED',error_message='Notion 재연결이 필요합니다.',
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where batch_id=? and status='READY'
                """, batchId);
    }

    private boolean fenceValid(Claimed item) {
        Boolean value = jdbc.queryForObject("""
                select exists(select 1 from daily_career.integration_connection connection
                    join daily_career.notion_target_set target_set on target_set.id=? and target_set.user_id=connection.user_id
                    where connection.id=? and connection.user_id=? and connection.status='ACTIVE'
                      and connection.credential_ref=? and connection.revision=? and target_set.revision=?)
                """, Boolean.class, item.targetSetId(), item.connectionId(), item.userId(), item.credentialRef(),
                item.connectionRevision(), item.targetsRevision());
        return Boolean.TRUE.equals(value);
    }

    private void finishBatch(long batchId) {
        jdbc.update("""
                update daily_career.notion_sync_batch batch set
                    created_count=counts.created_count,updated_count=counts.updated_count,
                    skipped_count=counts.skipped_count,failed_count=counts.failed_count,
                    status=case when counts.failed_count=0 then 'SUCCESS'
                                when counts.created_count+counts.updated_count+counts.skipped_count=0 then 'FAILED'
                                else 'PARTIAL_SUCCESS' end,
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                from (select batch_id,
                    count(*) filter (where status='CREATED')::int created_count,
                    count(*) filter (where status='UPDATED')::int updated_count,
                    count(*) filter (where status='SKIPPED')::int skipped_count,
                    count(*) filter (where status in ('FAILED','UNKNOWN'))::int failed_count,
                    count(*) filter (where status in ('READY','PROCESSING'))::int pending_count
                    from daily_career.notion_sync_item where batch_id=? group by batch_id) counts
                where batch.id=counts.batch_id and batch.status='PROCESSING' and counts.pending_count=0
                """, batchId);
        jdbc.update("""
                update daily_career.notion_sync_batch set status='SUCCESS',completed_at=clock_timestamp(),
                    updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='PROCESSING' and total_count=0
                """, batchId);
    }

    private JsonNode parse(Object value) {
        try { return json.readTree(value.toString()); }
        catch (Exception failure) { throw new IllegalStateException("Invalid Notion item payload", failure); }
    }
    private static long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static int number(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private record Claimed(long itemId, long userId, int connectionRevision, int targetsRevision,
                           long connectionId, long targetSetId, String credentialRef, String sourceKey,
                           long sourceRevision, NotionViews.Target target, JsonNode payload,
                           String externalObjectId) {}
}
