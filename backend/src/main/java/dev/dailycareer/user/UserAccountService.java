package dev.dailycareer.user;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.api.ApiEnvelope.ValidationDetail;
import dev.dailycareer.common.concurrency.Revisions;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {
    private final JdbcTemplate jdbc;
    public UserAccountService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public long login(String issuer, String subject, String email, boolean verified,
                      String name, String picture, Set<String> allowedEmails) {
        if (!verified || subject == null || subject.isBlank() || subject.length() > 255
                || email == null || email.length() < 3 || email.length() > 320
                || !email.contains("@") || !allowedEmails.contains(email.toLowerCase(Locale.ROOT))) {
            throw denied();
        }
        // Serialize initial claims across identities and emails. No email-based account merging.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "oidc:" + issuer + ":" + subject);
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "oidc-email:" + email.toLowerCase(Locale.ROOT));
        var linked = jdbc.queryForList("select user_id from daily_career.user_identity where issuer=? and subject=?", Long.class, issuer, subject);
        long id;
        String image = safePicture(picture);
        if (linked.isEmpty()) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from daily_career.app_user where lower(email)=lower(?))", Boolean.class, email))) throw denied();
            String nickname = name == null ? "" : name.strip();
            if (nickname.isEmpty()) nickname = "학습자";
            if (nickname.codePointCount(0, nickname.length()) > 30) nickname = nickname.substring(0, nickname.offsetByCodePoints(0, 30));
            id = jdbc.queryForObject("insert into daily_career.app_user(display_name,email,profile_image_url) values (?,?,?) returning id", Long.class, nickname, email, image);
            jdbc.update("insert into daily_career.user_identity(user_id,issuer,subject,last_login_at) values (?,?,?,now())", id, issuer, subject);
            jdbc.update("insert into daily_career.user_setting(user_id) values (?)", id);
        } else {
            id = linked.getFirst();
            UserView user;
            try { user = locked(id); }
            catch (ApiException exception) { throw denied(); }
            if (!user.email().equalsIgnoreCase(email)) throw denied();
            if (!Objects.equals(user.profileImageUrl(), image)) {
                if (user.revision() == Integer.MAX_VALUE) throw denied();
                jdbc.update("update daily_career.app_user set profile_image_url=?,revision=revision+1,updated_at=clock_timestamp() where id=?", image, id);
            }
            jdbc.update("update daily_career.user_identity set last_login_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where issuer=? and subject=?", issuer, subject);
        }
        return id;
    }

    public UserView current(long id) { return read(id, false); }
    private UserView locked(long id) { return read(id, true); }
    private UserView read(long id, boolean lock) {
        var users = jdbc.query("""
                select u.*, (select c.id from daily_career.user_curriculum c
                   where c.user_id=u.id and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED')
                   order by (c.status in ('PLANNED','ACTIVE','PAUSED')) desc,c.created_at desc,c.id desc limit 1) as curriculum_id
                from daily_career.app_user u where u.id=?
                """ + (lock ? " for update of u" : ""), this::map, id);
        if (users.isEmpty() || users.getFirst() == null) throw new ApiException(ApiErrorCode.AUTH_REQUIRED);
        return users.getFirst();
    }

    private UserView map(ResultSet row, int ignored) throws SQLException {
        String email = row.getString("email"), nickname = row.getString("display_name");
        long revision = row.getLong("revision");
        if (!row.getString("status").equals("ACTIVE") || email == null || email.length() < 3
                || nickname.isBlank() || nickname.codePointCount(0, nickname.length()) > 30 || revision > Integer.MAX_VALUE) return null;
        return new UserView(row.getLong("id"), email, nickname, row.getString("profile_image_url"), "USER",
                row.getObject("curriculum_id", Long.class), (int) revision,
                row.getObject("created_at", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.ofHours(9)),
                row.getObject("updated_at", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.ofHours(9)));
    }

    @Transactional
    public UserView rename(long id, int expected, String nickname) {
        if (nickname == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        nickname = nickname.strip();
        int size = nickname.codePointCount(0, nickname.length());
        if (size < 1 || size > 30) throw new ApiException(ApiErrorCode.VALIDATION_FAILED,
                List.of(new ValidationDetail("nickname", "앞뒤 공백을 제외한 1~30자를 입력해 주세요.")));
        UserView current = locked(id);
        Revisions.requireMatch(expected, current.revision());
        if (!current.nickname().equals(nickname)) {
            if (current.revision() == Integer.MAX_VALUE) throw new ApiException(ApiErrorCode.PRECONDITION_FAILED);
            jdbc.update("update daily_career.app_user set display_name=?,revision=revision+1,updated_at=clock_timestamp() where id=?", nickname, id);
        }
        return current(id);
    }

    static String safePicture(String value) {
        if (value == null || value.length() > 2048) return null;
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException exception) { return null; }
    }

    private static OAuth2AuthenticationException denied() {
        return new OAuth2AuthenticationException(new OAuth2Error("ACCOUNT_NOT_ALLOWED"));
    }
}
