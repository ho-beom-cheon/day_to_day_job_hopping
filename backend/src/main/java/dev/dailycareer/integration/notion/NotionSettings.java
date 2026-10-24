package dev.dailycareer.integration.notion;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class NotionSettings {
    static final String VERSION = "2026-03-11";
    static final URI API = URI.create("https://api.notion.com");
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String credentialKey;
    private final String keyId;

    public NotionSettings(@Value("${APP_NOTION_CLIENT_ID:}") String clientId,
                          @Value("${APP_NOTION_CLIENT_SECRET:}") String clientSecret,
                          @Value("${APP_NOTION_REDIRECT_URI:}") String redirectUri,
                          @Value("${APP_NOTION_CREDENTIAL_KEY:}") String credentialKey,
                          @Value("${APP_NOTION_KEY_ID:notion-local-v1}") String keyId) {
        this.clientId = trim(clientId);
        this.clientSecret = trim(clientSecret);
        this.redirectUri = trim(redirectUri);
        this.credentialKey = trim(credentialKey);
        this.keyId = trim(keyId);
    }

    public boolean enabled() {
        if (clientId.isBlank() || clientSecret.isBlank() || redirectUri.isBlank() || keyId.isBlank()) return false;
        try {
            URI redirect = URI.create(redirectUri);
            boolean safeRedirect = "https".equalsIgnoreCase(redirect.getScheme())
                    || ("http".equalsIgnoreCase(redirect.getScheme())
                    && ("localhost".equalsIgnoreCase(redirect.getHost()) || "127.0.0.1".equals(redirect.getHost())));
            return safeRedirect && key().length == 32;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public String authorizationUrl(String state) {
        required();
        return "https://api.notion.com/v1/oauth/authorize?client_id=" + encode(clientId)
                + "&response_type=code&owner=user&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
    }

    public String basicAuthorization() {
        required();
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    public String redirectUri() { required(); return redirectUri; }
    public byte[] key() {
        try { return Base64.getUrlDecoder().decode(pad(credentialKey)); }
        catch (IllegalArgumentException first) { return Base64.getDecoder().decode(credentialKey); }
    }
    public String keyId() { return keyId; }
    public void required() { if (!enabled()) throw new IllegalStateException("Notion is not configured"); }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String pad(String value) { return value + "=".repeat((4 - value.length() % 4) % 4); }
}
