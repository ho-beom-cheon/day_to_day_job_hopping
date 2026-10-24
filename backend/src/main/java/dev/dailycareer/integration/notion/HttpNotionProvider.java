package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class HttpNotionProvider implements NotionProvider {
    private final NotionSettings settings;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    HttpNotionProvider(NotionSettings settings, ObjectMapper json) {
        this(settings, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpNotionProvider(NotionSettings settings, ObjectMapper json, HttpClient http) {
        this.settings = settings;
        this.json = json;
        this.http = http;
    }

    @Override public OAuthToken exchange(String code) {
        ObjectNode body = json.createObjectNode().put("grant_type", "authorization_code")
                .put("code", code).put("redirect_uri", settings.redirectUri());
        JsonNode response = send("POST", "/v1/oauth/token", body, settings.basicAuthorization());
        String token = requiredText(response, "access_token");
        return new OAuthToken(token, requiredText(response, "workspace_id"), nullableText(response, "workspace_name"));
    }

    @Override public void revoke(String token) {
        send("POST", "/v1/oauth/revoke", json.createObjectNode().put("token", token), settings.basicAuthorization());
    }

    private JsonNode send(String method, String path, JsonNode body, String authorization) {
        settings.required();
        HttpRequest.Builder builder = HttpRequest.newBuilder(NotionSettings.API.resolve(path))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
                .header("Authorization", authorization).header("Notion-Version", NotionSettings.VERSION);
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return json.readTree(response.body());
            if (response.statusCode() == 401 || response.statusCode() == 403) throw new Failure(FailureKind.REAUTH_REQUIRED);
            if (response.statusCode() == 429 || response.statusCode() >= 500) throw new Failure(FailureKind.RETRYABLE);
            throw new Failure(FailureKind.REJECTED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure(FailureKind.UNKNOWN);
        } catch (IOException failure) {
            throw new Failure(FailureKind.UNKNOWN);
        }
    }

    private static String requiredText(JsonNode node, String key) {
        String value = nullableText(node, key);
        if (value == null || value.isBlank()) throw new Failure(FailureKind.REJECTED);
        return value;
    }
    private static String nullableText(JsonNode node, String key) { return node.path(key).isTextual() ? node.path(key).asText() : null; }
}
