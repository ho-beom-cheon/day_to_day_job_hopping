package dev.dailycareer.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.user.UserAccountService;
import java.sql.Timestamp;
import java.time.Instant;
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
import static dev.dailycareer.common.api.ApiErrorCode.VALIDATION_FAILED;
import static dev.dailycareer.notification.NotificationViews.*;

@Service
public class NotificationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public NotificationService(JdbcTemplate jdbc, UserAccountService users, IdempotencyService idempotency, ObjectMapper json) {
        this.jdbc = jdbc;
        this.users = users;
        this.idempotency = idempotency;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public NotificationPage list(long user, Boolean read, int page, int size, String sort) {
        users.current(user);
        if (page < 0 || size < 1 || size > 100) throw new ApiException(VALIDATION_FAILED);
        String order = order(sort);
        String filter = read == null ? "" : read ? " and read_at is not null" : " and read_at is null";
        Object[] args = new Object[]{user, size, (long) page * size};
        List<Notification> content = jdbc.query("""
                select id,event_type,title,body,coalesce(deep_link,'/') deep_link,read_at,created_at
                from daily_career.notification
                where user_id=? and canceled_at is null and scheduled_at<=clock_timestamp()
                """ + filter + " order by " + order + " limit ? offset ?", (row, number) -> new Notification(
                row.getLong("id"), row.getString("event_type"), row.getString("title"), row.getString("body"),
                row.getString("deep_link"), row.getObject("read_at") != null, time(row.getObject("created_at")),
                row.getObject("read_at") == null ? null : time(row.getObject("read_at"))), args);
        int total = count("select count(*)::int from daily_career.notification where user_id=? and canceled_at is null and scheduled_at<=clock_timestamp()" + filter, user);
        int unread = count("select count(*)::int from daily_career.notification where user_id=? and canceled_at is null and scheduled_at<=clock_timestamp() and read_at is null", user);
        return new NotificationPage(content, page, size, total, total == 0 ? 0 : (total + size - 1) / size, unread);
    }

    @Transactional
    public Notification markRead(long user, long notificationId) {
        users.current(user);
        int updated = jdbc.update("""
                update daily_career.notification set read_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where id=? and user_id=? and read_at is null
                """, notificationId, user);
        List<Map<String, Object>> rows = jdbc.queryForList("select * from daily_career.notification where id=? and user_id=?", notificationId, user);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        return view(rows.getFirst());
    }

    public StoredReply markAllRead(long user, ReadAllRequest input, UUID key) {
        users.current(user);
        validate(input);
        return idempotency.execute(user, "NOTI-003", key, json.valueToTree(input), () -> {
            OffsetDateTime through = input.through().atZoneSameInstant(SEOUL).toOffsetDateTime();
            int updated = jdbc.update("""
                    update daily_career.notification set read_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                    where user_id=? and canceled_at is null and read_at is null and created_at<=?
                    """, user, through);
            return new StoredReply(200, json.valueToTree(new ReadAllResult(updated, through)), null, null);
        });
    }

    static String order(String value) {
        return switch (value == null ? "createdAt,desc" : value) {
            case "createdAt,desc" -> "created_at desc,id";
            case "createdAt,asc" -> "created_at,id";
            default -> throw new ApiException(INVALID_SORT_FIELD);
        };
    }

    static void validate(ReadAllRequest input) {
        if (input == null || input.through() == null || input.through().toInstant().isAfter(Instant.now())) {
            throw new ApiException(VALIDATION_FAILED);
        }
    }

    private Notification view(Map<String, Object> row) {
        Object readAt = row.get("read_at");
        return new Notification(id(row, "id"), row.get("event_type").toString(), row.get("title").toString(),
                row.get("body").toString(), row.get("deep_link") == null ? "/" : row.get("deep_link").toString(),
                readAt != null, time(row.get("created_at")), readAt == null ? null : time(readAt));
    }

    private int count(String sql, long user) {
        Integer value = jdbc.queryForObject(sql, Integer.class, user);
        return value == null ? 0 : value;
    }

    private static long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static OffsetDateTime time(Object value) {
        return value instanceof Timestamp timestamp
                ? timestamp.toInstant().atZone(SEOUL).toOffsetDateTime()
                : ((OffsetDateTime) value).atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
