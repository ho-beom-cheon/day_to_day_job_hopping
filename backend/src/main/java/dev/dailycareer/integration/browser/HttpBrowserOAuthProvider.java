package dev.dailycareer.integration.browser;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class HttpBrowserOAuthProvider implements BrowserOAuthProvider {
    private final BrowserOAuthSettings settings;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public HttpBrowserOAuthProvider(BrowserOAuthSettings settings, ObjectMapper json) { this.settings = settings; this.json = json; }
    public Grant exchange(String provider, String code, String verifier) throws Exception {
        HttpRequest request;
        if (provider.equals("slack")) {
            String body = "client_id=" + encode(settings.slackClientId()) + "&client_secret=" + encode(settings.slackClientSecret())
                    + "&code=" + encode(code) + "&redirect_uri=" + encode(settings.slackRedirectUri());
            request = HttpRequest.newBuilder(URI.create("https://slack.com/api/oauth.v2.access"))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(20)).build();
        } else {
            String body = json.writeValueAsString(Map.of("code", code, "code_verifier", verifier, "code_challenge_method", "S256"));
            request = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/auth/keys"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(20)).build();
        }
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("OAuth exchange failed");
        var root = json.readTree(response.body());
        if (provider.equals("slack")) {
            String webhook = root.path("incoming_webhook").path("url").asText();
            // Provider-supplied URLs still pass the same strict HTTPS webhook boundary.
            URI uri = URI.create(webhook);
            if (!root.path("ok").asBoolean() || !"https".equals(uri.getScheme()) || !"hooks.slack.com".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || !uri.getPath().startsWith("/services/"))
                throw new IllegalStateException("Slack webhook not granted");
            return new Grant(json.createObjectNode().put("webhook", webhook), json.createObjectNode()
                    .put("channelName", root.path("incoming_webhook").path("channel").asText())
                    .put("workspaceName", root.path("team").path("name").asText()).put("workspaceId", root.path("team").path("id").asText()));
        }
        String key = root.path("key").asText();
        if (key.isBlank()) throw new IllegalStateException("OpenRouter key missing");
        return new Grant(json.createObjectNode().put("key", key), json.createObjectNode()
                .put("managementUrl", "https://openrouter.ai/keys/" + BrowserConnections.hash(key)));
    }
    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
