package dev.dailycareer.notification.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class HttpWebPushProvider implements WebPushProvider {
    private final PushEndpointPolicy endpointPolicy;
    private final WebPushCryptography cryptography;
    private final HttpClient http;

    @Autowired
    public HttpWebPushProvider(PushEndpointPolicy endpointPolicy, WebPushCryptography cryptography) {
        this(endpointPolicy, cryptography, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpWebPushProvider(PushEndpointPolicy endpointPolicy, WebPushCryptography cryptography, HttpClient http) {
        this.endpointPolicy = endpointPolicy;
        this.cryptography = cryptography;
        this.http = http;
    }

    @Override public Result send(URI storedEndpoint, String p256dh, String auth, byte[] payload) {
        try {
            URI endpoint = endpointPolicy.validate(storedEndpoint.toASCIIString());
            var prepared = cryptography.prepare(endpoint, p256dh, auth, payload);
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                    .header("Authorization", prepared.authorization())
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", "86400")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(prepared.body())).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 201 || status == 202) return Result.ACCEPTED;
            if (status == 404 || status == 410) return Result.EXPIRED;
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
