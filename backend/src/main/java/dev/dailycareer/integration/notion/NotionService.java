package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.json.Ids;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.integration.IntegrationViews.NotionConnection;
import dev.dailycareer.user.UserAccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
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
    private final NotionSnapshotFactory snapshots;
    private final TransactionTemplate transaction;

    public NotionService(JdbcTemplate jdbc, UserAccountService users, ObjectMapper json,
                         IdempotencyService idempotency, NotionSettings settings,
                         NotionCredentialStore credentials, NotionProvider provider,
                         NotionSnapshotFactory snapshots, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.users = users;
        this.json = json;
        this.idempotency = idempotency;
        this.settings = settings;
        this.credentials = credentials;
        this.provider = provider;
        this.snapshots = snapshots;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public boolean configured() { return settings.enabled(); }

    @Transactional(readOnly = true)
    public NotionConnection connection(long user) {
        users.current(user);
        List<Map<String, Object>> rows = connectionRows(user);
        if (rows.isEmpty()) return new NotionConnection(false, "DISCONNECTED", null, null,
                NotionSettings.VERSION, null, false, 0);
        Map<String, Object> row = rows.getFirst();
        String state = row.get("status").toString();
        boolean connected = "ACTIVE".equals(state) && row.get("credential_ref") != null;
        String status = connected ? "CONNECTED" : ("ACTIVE".equals(state) || "ERROR".equals(state))
                ? "REAUTH_REQUIRED" : "DISCONNECTED";
        long connectionId = id(row, "id");
        boolean configured = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from daily_career.notion_target_set where connection_id=? and user_id=?)",
                Boolean.class, connectionId, user));
        OffsetDateTime lastSync = jdbc.queryForObject("""
                select max(completed_at) from daily_career.notion_sync_batch
                where connection_id=? and user_id=? and status in ('SUCCESS','PARTIAL_SUCCESS')
                """, (result, number) -> time(result.getObject(1)), connectionId, user);
        JsonNode config = parse(row.get("config"));
        return new NotionConnection(connected, status, text(config, "workspaceName"),
                string(row.get("external_workspace_id")), NotionSettings.VERSION, lastSync, configured,
                number(row, "revision"));
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
        jdbc.update("""
                update daily_career.notion_sync_item item set status='FAILED',retryable=false,
                    error_code='NOTION_DISCONNECTED',error_message='Notion 연결이 해제되었습니다.',
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=item.revision+1
                from daily_career.notion_sync_batch batch
                where item.batch_id=batch.id and batch.connection_id=? and batch.user_id=?
                  and item.status in ('READY','PROCESSING')
                """, connectionId, user);
        jdbc.update("""
                update daily_career.notion_sync_batch batch set status='FAILED',
                    failed_count=total_count-created_count-updated_count-skipped_count,
                    completed_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where connection_id=? and user_id=? and status in ('READY','PROCESSING')
                """, connectionId, user);
        if (reference == null) return new DisconnectResult(true, "NOT_REQUIRED");
        jdbc.update("""
                insert into daily_career.notion_revocation(user_id,credential_ref)
                values (?,?::uuid) on conflict (credential_ref) do nothing
                """, user, reference);
        return new DisconnectResult(true, "PENDING");
    }

    @Transactional(readOnly = true)
    public Targets targets(long user) {
        ConnectionRow connection = activeConnection(user);
        List<Map<String, Object>> sets = jdbc.queryForList("""
                select id,revision,updated_at from daily_career.notion_target_set
                where connection_id=? and user_id=?
                """, connection.id(), user);
        if (sets.isEmpty()) return null;
        Map<String, Object> set = sets.getFirst();
        List<Target> values = jdbc.query("""
                select entity_type,database_id::text,data_source_id::text from daily_career.notion_target
                where target_set_id=? and user_id=? order by entity_type
                """, (row, number) -> new Target(row.getString(1), row.getString(2), row.getString(3)), id(set, "id"), user);
        return new Targets(values, time(set.get("updated_at")), number(set, "revision"));
    }

    public AvailableTargets availableTargets(long user, String query, String cursor) {
        if (query != null && query.length() > 100 || cursor != null && cursor.length() > 200) throw new ApiException(VALIDATION_FAILED);
        ConnectionRow connection = activeConnection(user);
        try {
            AvailableTargets result = provider.availableTargets(credential(user, connection), query, cursor);
            if (!activeConnection(user).equals(connection)) throw new ApiException(NOTION_REAUTH_REQUIRED);
            return result;
        } catch (NotionProvider.Failure failure) { throw targetFailure(failure); }
    }

    public Targets updateTargets(long user, TargetsPut input, int expected) {
        users.current(user);
        List<Target> targets = validateTargets(input);
        ConnectionRow connection = activeConnection(user);
        String token = credential(user, connection);
        for (Target target : targets) {
            try { provider.validateTarget(token, target); }
            catch (NotionProvider.Failure failure) { throw targetFailure(failure); }
        }
        return transaction.execute(status -> saveTargets(user, connection, targets, expected));
    }

    public StoredReply createSync(long user, SyncRequest input, UUID key) {
        users.current(user);
        validateSync(input);
        var execution = idempotency.executeWithReplay(user, "NOTION-004", key, json.valueToTree(input),
                () -> enqueue(user, input, null, null));
        if (!execution.reused()) return execution.reply();
        ObjectNode data = (ObjectNode) execution.reply().data();
        data.put("reused", true);
        return new StoredReply(202, data, null, execution.reply().location());
    }

    @Transactional(readOnly = true)
    public SyncPage history(long user, String status, int page, int size, String sort) {
        users.current(user);
        if (page < 0 || size < 1 || size > 100) throw new ApiException(VALIDATION_FAILED);
        if (status != null && !List.of("READY", "PROCESSING", "SUCCESS", "PARTIAL_SUCCESS", "FAILED").contains(status)) {
            throw new ApiException(VALIDATION_FAILED);
        }
        String order = order(sort);
        String filter = status == null ? "" : " and status=?";
        Object[] args = status == null ? new Object[]{user, size, (long) page * size}
                : new Object[]{user, status, size, (long) page * size};
        List<Map<String, Object>> rows = jdbc.queryForList("select * from daily_career.notion_sync_batch where user_id=?"
                + filter + " order by " + order + " limit ? offset ?", args);
        Integer totalValue = status == null
                ? jdbc.queryForObject("select count(*)::int from daily_career.notion_sync_batch where user_id=?", Integer.class, user)
                : jdbc.queryForObject("select count(*)::int from daily_career.notion_sync_batch where user_id=? and status=?", Integer.class, user, status);
        int total = totalValue == null ? 0 : totalValue;
        return new SyncPage(rows.stream().map(row -> job(user, row)).toList(), page, size, total,
                total == 0 ? 0 : (total + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public SyncJob get(long user, long syncId) {
        users.current(user);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from daily_career.notion_sync_batch where id=? and user_id=?", syncId, user);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        return job(user, rows.getFirst());
    }

    public StoredReply retry(long user, long syncId, UUID key) {
        users.current(user);
        ObjectNode fingerprint = json.createObjectNode().put("syncId", Long.toString(syncId));
        var execution = idempotency.executeWithReplay(user, "NOTION-011", key, fingerprint,
                () -> enqueueRetry(user, syncId));
        if (!execution.reused()) return execution.reply();
        ObjectNode data = (ObjectNode) execution.reply().data();
        data.put("reused", true);
        return new StoredReply(202, data, null, execution.reply().location());
    }

    private StoredReply enqueueRetry(long user, long syncId) {
        List<Map<String, Object>> batches = jdbc.queryForList(
                "select * from daily_career.notion_sync_batch where id=? and user_id=? for update", syncId, user);
        if (batches.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        Map<String, Object> original = batches.getFirst();
        if (!List.of("FAILED", "PARTIAL_SUCCESS").contains(original.get("status").toString())) {
            throw new ApiException(NOTION_RETRY_NOT_ALLOWED);
        }
        ConnectionRow connection = activeConnection(user);
        long targetSetId = id(original, "target_set_id");
        Integer currentTargets = jdbc.queryForObject(
                "select revision::int from daily_career.notion_target_set where id=? and user_id=?", Integer.class, targetSetId, user);
        if (connection.revision() != number(original, "connection_revision") || currentTargets == null
                || currentTargets != number(original, "targets_revision")) throw new ApiException(NOTION_TARGET_CHANGED);
        List<Map<String, Object>> items = jdbc.queryForList("""
                select * from daily_career.notion_sync_item
                where batch_id=? and user_id=? and status in ('FAILED','UNKNOWN') and retryable
                order by id
                """, syncId, user);
        if (items.isEmpty()) throw new ApiException(NOTION_RETRY_NOT_ALLOWED);
        return copyRetry(user, original, items);
    }

    private StoredReply copyRetry(long user, Map<String, Object> original, List<Map<String, Object>> items) {
        BatchRow batch = jdbc.queryForObject("""
                insert into daily_career.notion_sync_batch
                    (user_id,curriculum_id,connection_id,connection_revision,target_set_id,targets_revision,
                     entity_types,date_from,date_to,total_count,original_batch_id)
                values (?,?,?,?,?,?,?::jsonb,?,?,?,?) returning id,created_at
                """, (row, number) -> new BatchRow(row.getLong(1), time(row.getObject(2))), user,
                id(original, "curriculum_id"), id(original, "connection_id"), number(original, "connection_revision"),
                id(original, "target_set_id"), number(original, "targets_revision"), original.get("entity_types").toString(),
                original.get("date_from"), original.get("date_to"), items.size(), id(original, "id"));
        if (batch == null) throw new IllegalStateException("Notion retry batch was not created");
        for (Map<String, Object> item : items) jdbc.update("""
                insert into daily_career.notion_sync_item
                    (batch_id,user_id,entity_type,source_id,source_revision,source_key,database_id,data_source_id,
                     payload_snapshot,external_object_id)
                values (?,?,?,?,?,?,?::uuid,?::uuid,?::jsonb,?)
                """, batch.id(), user, item.get("entity_type"), item.get("source_id"), item.get("source_revision"),
                item.get("source_key"), item.get("database_id"), item.get("data_source_id"),
                item.get("payload_snapshot").toString(), item.get("external_object_id"));
        return receipt(batch);
    }

    private StoredReply enqueue(long user, SyncRequest input, Map<String, Object> retryBatch,
                                List<Map<String, Object>> retryItems) {
        long curriculum = Ids.parse(input.curriculumId());
        Boolean owns = jdbc.queryForObject("select exists(select 1 from daily_career.user_curriculum where id=? and user_id=?)",
                Boolean.class, curriculum, user);
        if (!Boolean.TRUE.equals(owns)) throw new ApiException(RESOURCE_NOT_FOUND);
        ConnectionRow connection = activeConnection(user);
        TargetSet set = targetSet(user, connection.id());
        List<NotionSnapshotFactory.Snapshot> values;
        try { values = snapshots.create(user, curriculum, input.entityTypes(), input.from(), input.to()); }
        catch (IllegalArgumentException failure) { throw new ApiException(VALIDATION_FAILED); }
        Map<String, Target> targetMap = new HashMap<>();
        List<Target> storedTargets = jdbc.query("""
                select entity_type,database_id::text,data_source_id::text from daily_career.notion_target
                where target_set_id=? and user_id=?
                """, (row, number) -> new Target(row.getString(1), row.getString(2), row.getString(3)), set.id(), user);
        storedTargets.forEach(target -> targetMap.put(target.entityType(), target));
        if (!targetMap.keySet().containsAll(input.entityTypes())) throw new ApiException(NOTION_TARGET_CHANGED);
        BatchRow batch = jdbc.queryForObject("""
                insert into daily_career.notion_sync_batch
                    (user_id,curriculum_id,connection_id,connection_revision,target_set_id,targets_revision,
                     entity_types,date_from,date_to,total_count)
                values (?,?,?,?,?,?,?::jsonb,?,?,?) returning id,created_at
                """, (row, number) -> new BatchRow(row.getLong(1), time(row.getObject(2))), user, curriculum,
                connection.id(), connection.revision(), set.id(), set.revision(), write(input.entityTypes()),
                input.from(), input.to(), values.size());
        if (batch == null) throw new IllegalStateException("Notion sync batch was not created");
        for (NotionSnapshotFactory.Snapshot value : values) {
            Target target = targetMap.get(value.entityType());
            jdbc.update("""
                    insert into daily_career.notion_sync_item
                        (batch_id,user_id,entity_type,source_id,source_revision,source_key,database_id,data_source_id,payload_snapshot)
                    values (?,?,?,?,?,?,?::uuid,?::uuid,?::jsonb)
                    """, batch.id(), user, value.entityType(), value.sourceId(), value.sourceRevision(),
                    value.entityType() + ":" + value.sourceId(), target.databaseId(), target.dataSourceId(), value.payload().toString());
        }
        return receipt(batch);
    }

    private StoredReply receipt(BatchRow batch) {
        String path = "/api/v1/integrations/notion/sync-jobs/" + batch.id();
        return new StoredReply(202, json.valueToTree(new JobReceipt(Long.toString(batch.id()), "READY", path,
                batch.createdAt(), false)), null, path);
    }

    private Targets saveTargets(long user, ConnectionRow expectedConnection, List<Target> targets, int expected) {
        ConnectionRow connection = activeConnection(user);
        if (connection.id() != expectedConnection.id() || connection.revision() != expectedConnection.revision()) {
            throw new ApiException(NOTION_TARGET_CHANGED);
        }
        List<Map<String, Object>> existing = jdbc.queryForList("""
                select id,revision from daily_career.notion_target_set where connection_id=? and user_id=? for update
                """, connection.id(), user);
        long setId;
        if (existing.isEmpty()) {
            if (expected != 0) throw new ApiException(PRECONDITION_FAILED);
            Long created = jdbc.queryForObject("""
                    insert into daily_career.notion_target_set(user_id,connection_id,revision)
                    values (?,?,1) returning id
                    """, Long.class, user, connection.id());
            if (created == null) throw new IllegalStateException("Notion target set was not created");
            setId = created;
        } else {
            Map<String, Object> row = existing.getFirst();
            if (number(row, "revision") != expected) throw new ApiException(PRECONDITION_FAILED);
            setId = id(row, "id");
            jdbc.update("delete from daily_career.notion_target where target_set_id=? and user_id=?", setId, user);
            int changed = jdbc.update("""
                    update daily_career.notion_target_set set revision=revision+1,updated_at=clock_timestamp()
                    where id=? and user_id=? and revision=?
                    """, setId, user, expected);
            if (changed != 1) throw new ApiException(PRECONDITION_FAILED);
        }
        for (Target target : targets) jdbc.update("""
                insert into daily_career.notion_target(target_set_id,user_id,entity_type,database_id,data_source_id)
                values (?,?,?,?::uuid,?::uuid)
                """, setId, user, target.entityType(), target.databaseId(), target.dataSourceId());
        return targets(user);
    }

    private List<Target> validateTargets(TargetsPut input) {
        if (input == null || input.targets() == null || input.targets().size() != 6) throw new ApiException(VALIDATION_FAILED);
        HashSet<String> types = new HashSet<>();
        for (Target target : input.targets()) {
            if (target == null || !ENTITY_TYPES.contains(target.entityType()) || !types.add(target.entityType())) {
                throw new ApiException(VALIDATION_FAILED);
            }
            try { UUID.fromString(target.databaseId()); UUID.fromString(target.dataSourceId()); }
            catch (RuntimeException failure) { throw new ApiException(VALIDATION_FAILED); }
        }
        if (!types.equals(new HashSet<>(ENTITY_TYPES))) throw new ApiException(VALIDATION_FAILED);
        return List.copyOf(input.targets());
    }

    private void validateSync(SyncRequest input) {
        if (input == null || input.curriculumId() == null || input.entityTypes() == null || input.from() == null || input.to() == null) {
            throw new ApiException(VALIDATION_FAILED);
        }
        Ids.parse(input.curriculumId());
        if (input.entityTypes().isEmpty() || input.entityTypes().size() > 6
                || new HashSet<>(input.entityTypes()).size() != input.entityTypes().size()
                || !ENTITY_TYPES.containsAll(input.entityTypes()) || input.from().isAfter(input.to())
                || input.to().isAfter(LocalDate.now(SEOUL)) || ChronoUnit.DAYS.between(input.from(), input.to()) > 365) {
            throw new ApiException(VALIDATION_FAILED);
        }
    }

    private SyncJob job(long user, Map<String, Object> row) {
        long batchId = id(row, "id");
        List<String> entities = new ArrayList<>();
        parse(row.get("entity_types")).forEach(value -> entities.add(value.asText()));
        List<SyncFailure> failures = jdbc.query("""
                select entity_type,source_id,error_code,error_message,retryable
                from daily_career.notion_sync_item where batch_id=? and user_id=? and status in ('FAILED','UNKNOWN')
                order by id limit 1000
                """, (result, number) -> new SyncFailure(result.getString(1), Long.toString(result.getLong(2)),
                result.getString(3), result.getString(4), result.getBoolean(5)), batchId, user);
        return new SyncJob(Long.toString(batchId), Long.toString(id(row, "curriculum_id")), row.get("status").toString(),
                number(row, "connection_revision"), number(row, "targets_revision"), entities,
                localDate(row.get("date_from")), localDate(row.get("date_to")), number(row, "created_count"),
                number(row, "updated_count"), number(row, "skipped_count"), number(row, "failed_count"),
                number(row, "total_count"), time(row.get("started_at")), time(row.get("completed_at")),
                time(row.get("created_at")), failures);
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

    private ConnectionRow activeConnection(long user) {
        users.current(user);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id,credential_ref,revision from daily_career.integration_connection
                where user_id=? and provider='NOTION' and status='ACTIVE' order by id desc limit 1
                """, user);
        if (rows.isEmpty()) throw new ApiException(NOTION_NOT_CONNECTED);
        Map<String, Object> row = rows.getFirst();
        if (row.get("credential_ref") == null) throw new ApiException(NOTION_REAUTH_REQUIRED);
        return new ConnectionRow(id(row, "id"), row.get("credential_ref").toString(), number(row, "revision"));
    }

    private String credential(long user, ConnectionRow connection) {
        try { return credentials.load(user, connection.credentialRef()); }
        catch (RuntimeException failure) { throw new ApiException(NOTION_REAUTH_REQUIRED); }
    }

    private TargetSet targetSet(long user, long connectionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id,revision from daily_career.notion_target_set where connection_id=? and user_id=?
                """, connectionId, user);
        if (rows.isEmpty()) throw new ApiException(NOTION_TARGET_CHANGED);
        return new TargetSet(id(rows.getFirst(), "id"), number(rows.getFirst(), "revision"));
    }

    private ApiException targetFailure(NotionProvider.Failure failure) {
        return switch (failure.kind()) {
            case INVALID_TARGET, REJECTED -> new ApiException(NOTION_TARGET_INVALID);
            case REAUTH_REQUIRED -> new ApiException(NOTION_REAUTH_REQUIRED);
            case RETRYABLE, UNKNOWN -> new ApiException(SERVICE_UNAVAILABLE);
        };
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

    static String order(String value) {
        return switch (value == null ? "createdAt,desc" : value) {
            case "createdAt,desc" -> "created_at desc,id desc";
            case "createdAt,asc" -> "created_at,id";
            default -> throw new ApiException(INVALID_SORT_FIELD);
        };
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
    private static LocalDate localDate(Object value) { return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value; }
    private static OffsetDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atZone(SEOUL).toOffsetDateTime();
        return ((OffsetDateTime) value).atZoneSameInstant(SEOUL).toOffsetDateTime();
    }

    private record OAuthClaim(String stateHash, int connectionRevision) {}
    private record ConnectionRow(long id, String credentialRef, int revision) {}
    private record TargetSet(long id, int revision) {}
    private record BatchRow(long id, OffsetDateTime createdAt) {}
}
