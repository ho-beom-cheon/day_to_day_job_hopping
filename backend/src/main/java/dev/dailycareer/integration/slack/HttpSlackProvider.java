package dev.dailycareer.integration.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class HttpSlackProvider implements SlackProvider {
    private final SlackEndpointPolicy endpointPolicy;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public HttpSlackProvider(SlackEndpointPolicy endpointPolicy, ObjectMapper json) {
        this(endpointPolicy, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpSlackProvider(SlackEndpointPolicy endpointPolicy, ObjectMapper json, HttpClient http) {
        this.endpointPolicy = endpointPolicy;
        this.json = json;
        this.http = http;
    }

    @Override public Result send(URI storedWebhook, Message message) {
        try {
            URI webhook = endpointPolicy.validate(storedWebhook);
            String path = message.targetPath() != null && message.targetPath().startsWith("/")
                    && !message.targetPath().startsWith("//") ? message.targetPath() : "/";
            String text = "[Daily Leap] " + message.title() + "\n" + message.body() + "\n앱에서 확인: " + path;
            HttpRequest request = HttpRequest.newBuilder(webhook).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(Map.of("text", text)))).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) return Result.ACCEPTED;
            if (status == 429 || status >= 500) return Result.RETRYABLE;
            return Result.REJECTED;
        } catch (java.net.http.HttpTimeoutException | java.net.ConnectException unknown) {
            return Result.UNKNOWN;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.UNKNOWN;
        } catch (java.io.IOException unknown) {
            return Result.UNKNOWN;
        } catch (RuntimeException rejected) {
            return Result.REJECTED;
        }
    }
}
