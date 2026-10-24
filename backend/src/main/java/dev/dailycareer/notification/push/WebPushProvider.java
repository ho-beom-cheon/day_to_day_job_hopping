package dev.dailycareer.notification.push;

import java.net.URI;

public interface WebPushProvider {
    Result send(URI endpoint, String p256dh, String auth, byte[] payload);
    enum Result { ACCEPTED, EXPIRED, RETRYABLE, REJECTED, UNKNOWN }
}
