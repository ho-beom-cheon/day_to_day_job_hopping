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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class HttpNotionProvider implements NotionProvider {
    private static final String LEGACY_BODY_MARKER = "Daily Leap 동기화 내용\n\n";
    private final NotionSettings settings;
    private final ObjectMapper json;
    private final HttpClient http;
    private final NotionPageTemplate template;

    @Autowired
    HttpNotionProvider(NotionSettings settings, ObjectMapper json) {
        this(settings, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpNotionProvider(NotionSettings settings, ObjectMapper json, HttpClient http) {
        this.settings = settings;
        this.json = json;
        this.http = http;
        this.template = new NotionPageTemplate(json);
    }

    @Override public OAuthToken exchange(String code) {
        ObjectNode body = json.createObjectNode().put("grant_type", "authorization_code")
                .put("code", code).put("redirect_uri", settings.redirectUri());
        JsonNode response = send("POST", "/v1/oauth/token", body, settings.basicAuthorization(), false);
        String token = requiredText(response, "access_token");
        return new OAuthToken(token, requiredText(response, "workspace_id"), nullableText(response, "workspace_name"));
    }

    @Override public void revoke(String token) {
        send("POST", "/v1/oauth/revoke", json.createObjectNode().put("token", token), settings.basicAuthorization(), false);
    }

    @Override public NotionViews.AvailableTargets availableTargets(String token, String query, String cursor) {
        ObjectNode body = json.createObjectNode().put("page_size", 50);
        body.putObject("filter").put("property", "object").put("value", "data_source");
        if (query != null && !query.isBlank()) body.put("query", query);
        if (cursor != null && !cursor.isBlank()) body.put("start_cursor", cursor);
        JsonNode response = send("POST", "/v1/search", body, bearer(token), true);
        var targets = new java.util.ArrayList<NotionViews.AvailableTarget>();
        for (JsonNode source : response.path("results")) {
            if (!"data_source".equals(source.path("object").asText()) || source.path("in_trash").asBoolean()) continue;
            String database = source.path("parent").path("database_id").asText();
            if (database.isBlank()) continue;
            StringBuilder title = new StringBuilder();
            for (JsonNode part : source.path("title")) title.append(part.path("plain_text").asText(part.path("text").path("content").asText()));
            targets.add(new NotionViews.AvailableTarget(title.isEmpty() ? "제목 없는 데이터베이스" : title.toString(), database, requiredText(source, "id")));
        }
        return new NotionViews.AvailableTargets(targets, nullableText(response, "next_cursor"));
    }

    @Override public void validateTarget(String token, NotionViews.Target target) {
        JsonNode source = send("GET", "/v1/data_sources/" + target.dataSourceId(), null, bearer(token), true);
        JsonNode parent = source.path("parent");
        if (!target.databaseId().equalsIgnoreCase(parent.path("database_id").asText())) {
            throw new Failure(FailureKind.INVALID_TARGET);
        }
        JsonNode properties = source.path("properties");
        requireProperty(properties, "Name", "title");
        requireProperty(properties, "SourceKey", "rich_text");
        requireProperty(properties, "SourceVersion", "number");
        requireProperty(properties, "EntityType", "select");
        requireProperty(properties, "Summary", "rich_text");
        requireProperty(properties, "OccurredAt", "date");
    }

    @Override public WriteOutcome upsert(String token, NotionViews.Target target, String sourceKey,
                                         long sourceRevision, JsonNode payload, String knownExternalObjectId) {
        String pageId = knownExternalObjectId;
        JsonNode existing = null;
        String details = text(payload, "details", null);
        if (pageId == null || pageId.isBlank()) {
            ObjectNode filter = json.createObjectNode();
            filter.putObject("filter").put("property", "SourceKey").putObject("rich_text").put("equals", sourceKey);
            filter.put("page_size", 1);
            JsonNode found = send("POST", "/v1/data_sources/" + target.dataSourceId() + "/query", filter, bearer(token), true);
            if (found.path("results").isArray() && !found.path("results").isEmpty()) {
                existing = found.path("results").get(0);
                pageId = requiredText(existing, "id");
            }
        } else {
            existing = send("GET", "/v1/pages/" + pageId, null, bearer(token), true);
        }
        if (existing != null) {
            long remoteRevision = existing.path("properties").path("SourceVersion").path("number").asLong(-1);
            if (remoteRevision > sourceRevision) return new WriteOutcome(WriteResult.SKIPPED, pageId);
            List<ManagedBlock> managed = details == null ? List.of() : managedBlocks(token, pageId);
            String currentId = managed.stream().filter(ManagedBlock::current)
                    .map(ManagedBlock::id).reduce((first, last) -> last).orElse(null);
            if (remoteRevision == sourceRevision && (details == null || currentId != null)) {
                for (ManagedBlock block : managed) {
                    if (!block.id().equals(currentId)) trashBlock(token, block.id());
                }
                if (managed.size() > 1) return new WriteOutcome(WriteResult.UPDATED, pageId);
                return new WriteOutcome(WriteResult.SKIPPED, pageId);
            }
            if (details != null) {
                ObjectNode append = json.createObjectNode();
                append.putArray("children").add(template.render(target.entityType(), payload));
                JsonNode appended = send("PATCH", "/v1/blocks/" + pageId + "/children", append, bearer(token), false);
                requiredText(appended.path("results").path(0), "id");
                for (ManagedBlock block : managed) trashBlock(token, block.id());
            }
            if (remoteRevision < sourceRevision) {
                ObjectNode update = json.createObjectNode();
                update.set("properties", properties(sourceKey, sourceRevision, target.entityType(), payload));
                JsonNode response = send("PATCH", "/v1/pages/" + pageId, update, bearer(token), false);
                return new WriteOutcome(WriteResult.UPDATED, requiredText(response, "id"));
            }
            return new WriteOutcome(WriteResult.UPDATED, pageId);
        }
        ObjectNode create = json.createObjectNode();
        create.putObject("parent").put("type", "data_source_id").put("data_source_id", target.dataSourceId());
        create.set("properties", properties(sourceKey, sourceRevision, target.entityType(), payload));
        if (details != null) create.putArray("children").add(template.render(target.entityType(), payload));
        JsonNode response = send("POST", "/v1/pages", create, bearer(token), false);
        return new WriteOutcome(WriteResult.CREATED, requiredText(response, "id"));
    }

    private record ManagedBlock(String id, boolean current) {}

    private List<ManagedBlock> managedBlocks(String token, String pageId) {
        List<ManagedBlock> managed = new ArrayList<>();
        String cursor = null;
        do {
            String path = "/v1/blocks/" + pageId + "/children?page_size=100"
                    + (cursor == null ? "" : "&start_cursor=" + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8));
            JsonNode response = send("GET", path, null, bearer(token), false);
            for (JsonNode block : response.path("results")) {
                String type = block.path("type").asText();
                if (!("paragraph".equals(type) || "callout".equals(type)) || block.path("in_trash").asBoolean()) continue;
                StringBuilder content = new StringBuilder();
                for (JsonNode part : block.path(type).path("rich_text")) {
                    content.append(part.path("plain_text").asText(part.path("text").path("content").asText()));
                    if (content.length() >= 80) break;
                }
                if ("paragraph".equals(type) && content.toString().startsWith(LEGACY_BODY_MARKER)) {
                    managed.add(new ManagedBlock(requiredText(block, "id"), false));
                } else if (content.toString().startsWith(NotionPageTemplate.MARKER + " · ")) {
                    managed.add(new ManagedBlock(requiredText(block, "id"), "paragraph".equals(type)));
                }
            }
            cursor = response.path("has_more").asBoolean() ? nullableText(response, "next_cursor") : null;
        } while (cursor != null);
        return managed;
    }

    private void trashBlock(String token, String blockId) {
        send("DELETE", "/v1/blocks/" + blockId, null, bearer(token), false);
    }

    private ObjectNode properties(String sourceKey, long revision, String entityType, JsonNode payload) {
        String title = text(payload, "title", entityType + " " + sourceKey);
        String summary = text(payload, "summary", title);
        String occurredAt = text(payload, "occurredAt", java.time.LocalDate.now().toString());
        ObjectNode properties = json.createObjectNode();
        properties.set("Name", json.valueToTree(Map.of("title", new Object[]{Map.of("text", Map.of("content", limit(title, 200))) })));
        properties.set("SourceKey", json.valueToTree(Map.of("rich_text", new Object[]{Map.of("text", Map.of("content", sourceKey))})));
        properties.set("SourceVersion", json.valueToTree(Map.of("number", revision)));
        properties.set("EntityType", json.valueToTree(Map.of("select", Map.of("name", entityType))));
        properties.set("Summary", json.valueToTree(Map.of("rich_text", new Object[]{Map.of("text", Map.of("content", limit(summary, 1900)))})));
        properties.set("OccurredAt", json.valueToTree(Map.of("date", Map.of("start", occurredAt))));
        return properties;
    }

    private JsonNode send(String method, String path, JsonNode body, String authorization, boolean targetCall) {
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
            if (targetCall && (response.statusCode() == 400 || response.statusCode() == 404)) throw new Failure(FailureKind.INVALID_TARGET);
            if (response.statusCode() == 429 || response.statusCode() >= 500) throw new Failure(FailureKind.RETRYABLE);
            throw new Failure(FailureKind.REJECTED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Failure(FailureKind.UNKNOWN);
        } catch (IOException failure) {
            throw new Failure(FailureKind.UNKNOWN);
        }
    }

    private static void requireProperty(JsonNode properties, String name, String type) {
        if (!type.equals(properties.path(name).path("type").asText())) throw new Failure(FailureKind.INVALID_TARGET);
    }
    private static String bearer(String token) { return "Bearer " + token; }
    private static String requiredText(JsonNode node, String key) {
        String value = nullableText(node, key);
        if (value == null || value.isBlank()) throw new Failure(FailureKind.REJECTED);
        return value;
    }
    private static String nullableText(JsonNode node, String key) { return node.path(key).isTextual() ? node.path(key).asText() : null; }
    private static String text(JsonNode node, String key, String fallback) {
        return node.path(key).isTextual() && !node.path(key).asText().isBlank() ? node.path(key).asText() : fallback;
    }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
