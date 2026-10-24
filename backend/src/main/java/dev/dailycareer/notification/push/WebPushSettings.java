package dev.dailycareer.notification.push;

import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class WebPushSettings {
    private static final Set<String> DEFAULT_HOSTS = Set.of(
            "fcm.googleapis.com", "updates.push.services.mozilla.com", "push.services.mozilla.com",
            "web.push.apple.com", ".notify.windows.com");

    private final String publicKey;
    private final String privateKey;
    private final String subscriptionKey;
    private final String keyId;
    private final String subject;
    private final Set<String> allowedHosts;

    public WebPushSettings(
            @Value("${app.web-push.public-key:}") String publicKey,
            @Value("${app.web-push.private-key:}") String privateKey,
            @Value("${app.web-push.subscription-key:}") String subscriptionKey,
            @Value("${app.web-push.key-id:}") String keyId,
            @Value("${app.web-push.subject:}") String subject,
            @Value("${app.web-push.allowed-hosts:}") String allowedHosts) {
        this.publicKey = clean(publicKey);
        this.privateKey = clean(privateKey);
        this.subscriptionKey = clean(subscriptionKey);
        this.keyId = clean(keyId);
        this.subject = clean(subject);
        var configured = new LinkedHashSet<String>();
        if (allowedHosts != null) for (String host : allowedHosts.split(",")) {
            String normalized = host.strip().toLowerCase();
            if (!normalized.isEmpty()) configured.add(normalized);
        }
        this.allowedHosts = configured.isEmpty() ? DEFAULT_HOSTS : Set.copyOf(configured);
    }

    public boolean enabled() {
        byte[] publicBytes = decoded(publicKey, 65);
        byte[] privateBytes = decoded(privateKey, 32);
        return publicBytes != null && publicBytes[0] == 4 && privateBytes != null && nonZero(privateBytes)
                && decoded(subscriptionKey, 32) != null && !keyId.isBlank()
                && (subject.startsWith("mailto:") || subject.startsWith("https://"));
    }

    public String publicKey() { return publicKey; }
    byte[] publicKeyBytes() { return required(publicKey, 65); }
    byte[] privateKeyBytes() { return required(privateKey, 32); }
    byte[] subscriptionKeyBytes() { return required(subscriptionKey, 32); }
    String keyId() { return keyId; }
    String subject() { return subject; }
    Set<String> allowedHosts() { return allowedHosts; }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static byte[] required(String value, int length) {
        byte[] decoded = decoded(value, length);
        if (decoded == null) throw new IllegalStateException("Web Push configuration is incomplete");
        return decoded;
    }
    private static byte[] decoded(String value, int length) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            return bytes.length == length ? bytes : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
    private static boolean nonZero(byte[] value) { for (byte part : value) if (part != 0) return true; return false; }
}
