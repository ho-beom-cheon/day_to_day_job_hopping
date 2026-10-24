package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpNotionProviderBodyTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"paragraph", "callout"})
    void replacesOnlyOldAppBodyWithStructuredTemplate(String oldType) throws Exception {
        HttpClient http = mock(HttpClient.class);
        List<HttpRequest> calls = new ArrayList<>();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            calls.add(request);
            String path = request.uri().getPath();
            if (path.equals("/v1/pages/page-1")) return response("{" +
                    "\"id\":\"page-1\",\"properties\":{\"SourceVersion\":{\"number\":2}}}");
            if (path.equals("/v1/blocks/page-1/children") && request.method().equals("GET")) {
                return response("{\"results\":[{\"id\":\"user-block\",\"type\":\"paragraph\"," +
                        "\"paragraph\":{\"rich_text\":[{\"plain_text\":\"내 개인 메모\"}]}}," +
                        "{\"id\":\"old-app-block\",\"type\":\"" + oldType + "\",\"" + oldType + "\":{\"rich_text\":[" +
                        "{\"plain_text\":\"" + (oldType.equals("callout")
                                ? NotionPageTemplate.MARKER + " · 학습 완료"
                                : "Daily Leap 동기화 내용\\n\\n기존 본문") + "\"}]}}],\"has_more\":false}");
            }
            if (path.equals("/v1/blocks/page-1/children") && request.method().equals("PATCH")) {
                return response("{\"results\":[{\"id\":\"new-app-block\"}]}");
            }
            if (path.equals("/v1/blocks/old-app-block") && request.method().equals("DELETE")) return response("{\"id\":\"old-app-block\"}");
            if (path.equals("/v1/pages/page-1") && request.method().equals("PATCH")) return response("{\"id\":\"page-1\"}");
            throw new AssertionError("Unexpected request: " + request.method() + " " + path);
        });
        var provider = new HttpNotionProvider(settings(), json, http);
        var target = new NotionViews.Target("LEARNING_RESULT", "database-1", "source-1");
        JsonNode payload = json.valueToTree(Map.of("title", "Java 학습", "summary", "완료", "occurredAt", "2026-09-26",
                "details", "학습 자료\nList와 Map의 차이를 학습했다."));

        var result = provider.upsert("token", target, "LEARNING_RESULT:7", 2, payload, "page-1");

        assertThat(result.result()).isEqualTo(NotionProvider.WriteResult.UPDATED);
        assertThat(calls).hasSize(4);
        assertThat(calls.stream().map(HttpRequest::method)).containsExactly("GET", "GET", "PATCH", "DELETE");
        assertThat(calls.get(3).uri().getPath()).isEqualTo("/v1/blocks/old-app-block");
        JsonNode appended = json.readTree(body(calls.get(2)));
        JsonNode root = appended.path("children").get(0);
        assertThat(root.path("type").asText()).isEqualTo("paragraph");
        assertThat(root.path("paragraph").path("rich_text").get(0).path("text").path("content").asText())
                .startsWith(NotionPageTemplate.MARKER);
        assertThat(root.path("paragraph").path("children").toString()).contains("학습 자료", "List와 Map의 차이");
    }

    @Test void repeatedSyncKeepsCurrentBodyAndPersonalNotes() throws Exception {
        HttpClient http = mock(HttpClient.class);
        List<HttpRequest> calls = new ArrayList<>();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            calls.add(request);
            if (!request.method().equals("GET")) throw new AssertionError("Unexpected write");
            if (request.uri().getPath().equals("/v1/pages/page-1")) return response(
                    "{\"id\":\"page-1\",\"properties\":{\"SourceVersion\":{\"number\":2}}}");
            return response("{\"results\":[{\"id\":\"user-block\",\"type\":\"paragraph\","
                    + "\"paragraph\":{\"rich_text\":[{\"plain_text\":\"내 개인 메모\"}]}},"
                    + "{\"id\":\"current-body\",\"type\":\"paragraph\",\"paragraph\":{\"rich_text\":["
                    + "{\"plain_text\":\"Daily Leap 학습 기록 · 문제 풀이\"}]}}],\"has_more\":false}");
        });
        var provider = new HttpNotionProvider(settings(), json, http);
        var target = new NotionViews.Target("PROBLEM", "database-1", "source-1");
        JsonNode payload = json.valueToTree(Map.of("details", "문제\nINNER JOIN은?"));

        var result = provider.upsert("token", target, "PROBLEM:7", 2, payload, "page-1");

        assertThat(result.result()).isEqualTo(NotionProvider.WriteResult.SKIPPED);
        assertThat(calls).hasSize(2);
    }

    @Test void newPageIncludesReadableBodyAtCreation() throws Exception {
        HttpClient http = mock(HttpClient.class);
        List<HttpRequest> calls = new ArrayList<>();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            calls.add(request);
            return request.uri().getPath().endsWith("/query")
                    ? response("{\"results\":[]}") : response("{\"id\":\"page-2\"}");
        });
        var provider = new HttpNotionProvider(settings(), json, http);
        var target = new NotionViews.Target("WEEKLY_REPORT", "database-1", "source-1");
        JsonNode payload = json.valueToTree(Map.of("title", "주간 리포트", "summary", "완료 2/9",
                "occurredAt", "2026-09-21", "details", "2026-09-21 · 학습일\n• Java 학습 · 완료"));

        var result = provider.upsert("token", target, "WEEKLY_REPORT:1", 2, payload, null);

        assertThat(result.result()).isEqualTo(NotionProvider.WriteResult.CREATED);
        assertThat(calls).hasSize(2);
        JsonNode created = json.readTree(body(calls.getLast()));
        JsonNode root = created.path("children").get(0);
        assertThat(root.path("type").asText()).isEqualTo("paragraph");
        assertThat(root.path("paragraph").path("children").toString()).contains("Java 학습 · 완료");
    }

    private NotionSettings settings() {
        byte[] key = new byte[32];
        return new NotionSettings("client", "secret", "http://localhost:8080/callback",
                Base64.getUrlEncoder().withoutPadding().encodeToString(key), "test-v1");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static String body(HttpRequest request) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<String> result = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
            @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
            @Override public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(1, TimeUnit.SECONDS);
    }
}
