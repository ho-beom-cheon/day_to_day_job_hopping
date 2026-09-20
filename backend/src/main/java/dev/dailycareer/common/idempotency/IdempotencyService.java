package dev.dailycareer.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Short local DB transactions only. Authenticate/authorize before invoking, including on replays. */
@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public static UUID requiredKey(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders("Idempotency-Key"));
        if (values.isEmpty()) throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        if (values.size() != 1) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        return parseKey(values.getFirst());
    }

    public static UUID parseKey(String value) {
        if (value == null) throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        if (!value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        return UUID.fromString(value);
    }

    public StoredReply execute(long userId, String operationId, UUID key, JsonNode input, Supplier<StoredReply> work) {
        if (userId <= 0 || operationId == null || !operationId.matches("[A-Z][A-Z0-9_]*-[0-9]{3}")) {
            throw new IllegalArgumentException("Expected an authenticated app user and a canonical API ID");
        }
        Objects.requireNonNull(key);
        Objects.requireNonNull(work);
        String digest = RequestFingerprint.digest(input);
        return Objects.requireNonNull(transaction.execute(status -> executeLocked(userId, operationId, key, digest, work)));
    }

    private StoredReply executeLocked(long userId, String operationId, UUID key, String digest, Supplier<StoredReply> work) {
        long lockId = ByteBuffer.wrap(RequestFingerprint.sha256(userId + ":" + operationId + ":" + key)).getLong();
        // Nonblocking transaction lock also serializes the not-yet-inserted row case across app instances.
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, lockId))) {
            throw new ApiException(ApiErrorCode.REQUEST_IN_PROGRESS);
        }
        var records = jdbc.query("""
                select id, request_digest, status, response_status, response_payload::text,
                       expires_at > current_timestamp as active
                from daily_career.idempotency_record
                where user_id=? and operation_scope=? and idempotency_key=? for update
                """, (row, number) -> new Existing(row.getLong("id"), row.getString("request_digest"), row.getString("status"),
                row.getObject("response_status", Integer.class), row.getString("response_payload"), row.getBoolean("active")),
                userId, operationId, key.toString());
        long id;
        if (!records.isEmpty()) {
            Existing existing = records.getFirst();
            if (existing.active()) {
                if (!existing.digest().equals(digest)) throw new ApiException(ApiErrorCode.IDEMPOTENCY_KEY_REUSED);
                if (!existing.state().equals("SUCCEEDED")) throw new ApiException(ApiErrorCode.REQUEST_IN_PROGRESS);
                return read(existing);
            }
            id = existing.id();
            jdbc.update("""
                    update daily_career.idempotency_record set request_digest=?, status='PROCESSING', response_status=null,
                        response_payload=null, expires_at=current_timestamp+interval '24 hours',
                        updated_at=current_timestamp, revision=revision+1 where id=?
                    """, digest, id);
        } else {
            id = jdbc.queryForObject("""
                    insert into daily_career.idempotency_record(user_id,operation_scope,idempotency_key,request_digest,expires_at)
                    values (?,?,?,?,current_timestamp+interval '24 hours') returning id
                    """, Long.class, userId, operationId, key.toString(), digest);
        }
        StoredReply reply = Objects.requireNonNull(work.get(), "Work must produce a durable success reply");
        jdbc.update("""
                update daily_career.idempotency_record set status='SUCCEEDED', response_status=?, response_payload=?::jsonb,
                    updated_at=current_timestamp, revision=revision+1 where id=?
                """, reply.status(), write(reply), id);
        return reply;
    }

    /** Maintenance hook; scheduling belongs to the worker lifecycle. Never remove active or locked work. */
    public int purgeExpired(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("Batch size must be 1..1000");
        return Objects.requireNonNull(transaction.execute(status -> jdbc.update("""
                delete from daily_career.idempotency_record where id in (
                    select id from daily_career.idempotency_record where expires_at <= current_timestamp
                    order by expires_at, id limit ? for update skip locked
                )
                """, batchSize)));
    }

    private String write(StoredReply reply) {
        try { return mapper.writeValueAsString(reply); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot store idempotency result", exception); }
    }

    private StoredReply read(Existing existing) {
        try {
            StoredReply reply = mapper.readValue(existing.payload(), StoredReply.class);
            if (!Objects.equals(existing.status(), reply.status())) throw new IllegalStateException("Stored status does not match payload");
            return reply;
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot restore idempotency result", exception); }
    }

    private record Existing(long id, String digest, String state, Integer status, String payload, boolean active) {}
}
