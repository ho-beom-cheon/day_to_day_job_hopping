package dev.dailycareer.integration.browser;

import java.net.URI;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record BrowserOAuthSettings(
        @Value("${INTEGRATION_CREDENTIAL_KEY:}") String credentialKey,
        @Value("${SLACK_CLIENT_ID:}") String slackClientId,
        @Value("${SLACK_CLIENT_SECRET:}") String slackClientSecret,
        @Value("${SLACK_REDIRECT_URI:}") String slackRedirectUri,
        @Value("${OPENROUTER_REDIRECT_URI:}") String openRouterRedirectUri,
        @Value("${OPENROUTER_MODEL:}") String openRouterModel) {
    public boolean ready(String provider) {
        if (!keyReady()) return false;
        return switch (provider) {
            case "slack" -> !slackClientId.isBlank() && !slackClientSecret.isBlank() && safeCallback(slackRedirectUri, false);
            case "openrouter" -> !openRouterModel.isBlank() && safeCallback(openRouterRedirectUri, true);
            default -> false;
        };
    }
    public String callback(String provider) { return provider.equals("slack") ? slackRedirectUri : openRouterRedirectUri; }
    public boolean keyReady() { try { return key().length == 32; } catch (IllegalArgumentException invalid) { return false; } }
    byte[] key() { return Base64.getDecoder().decode(credentialKey); }
    static boolean safeCallback(String value, boolean allowLocal) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && ("https".equals(uri.getScheme()) || allowLocal && "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())));
        } catch (IllegalArgumentException invalid) { return false; }
    }
    @Override public String toString() { return "BrowserOAuthSettings[redacted]"; }
}
