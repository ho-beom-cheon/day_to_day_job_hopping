package dev.dailycareer.integration.slack;

import java.net.URI;

public interface SlackProvider {
    Result send(URI webhook, Message message);

    record Message(String eventType, String title, String body, String targetPath) {}
    enum Result { ACCEPTED, RETRYABLE, REJECTED, UNKNOWN }
}
