package dev.dailycareer.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Session binding plus a PostgreSQL compare-and-delete shared by concurrent callbacks. */
public final class ExpiringAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final HttpSessionOAuth2AuthorizationRequestRepository sessions = new HttpSessionOAuth2AuthorizationRequestRepository();
    private final JdbcTemplate jdbc;
    public ExpiringAuthorizationRequests(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        var authorization = sessions.loadAuthorizationRequest(request);
        if (authorization == null) return null;
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from daily_career.oidc_login_state where state_hash=? and expires_at>clock_timestamp())", Boolean.class, hash(authorization.getState()))) ? authorization : null;
    }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorization, HttpServletRequest request, HttpServletResponse response) {
        if (authorization != null) {
            // Bounded opportunistic cleanup; only expired login metadata, never user sessions.
            jdbc.update("delete from daily_career.oidc_login_state where state_hash in (select state_hash from daily_career.oidc_login_state where expires_at<=clock_timestamp() order by expires_at limit 100)");
            jdbc.update("insert into daily_career.oidc_login_state(state_hash) values (?)", hash(authorization.getState()));
        }
        sessions.saveAuthorizationRequest(authorization, request, response);
    }
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        var authorization = sessions.removeAuthorizationRequest(request, response);
        if (authorization == null) return null;
        int consumed = jdbc.update("delete from daily_career.oidc_login_state where state_hash=? and expires_at>clock_timestamp()", hash(authorization.getState()));
        return consumed == 1 ? authorization : null;
    }
    private static String hash(String state) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
