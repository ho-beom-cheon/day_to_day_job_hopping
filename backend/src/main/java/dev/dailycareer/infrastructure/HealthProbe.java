package dev.dailycareer.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Container-local probe, no additional public API and no curl package required. */
public final class HealthProbe {
    private HealthProbe() {}

    public static void main(String[] args) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:9090/actuator/health"))
                .timeout(Duration.ofSeconds(4)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) System.exit(1);
    }
}
