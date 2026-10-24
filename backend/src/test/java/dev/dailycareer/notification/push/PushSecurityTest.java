package dev.dailycareer.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.net.InetAddress;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushSecurityTest {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    @Test void requiresCompleteServerKeysAndEncryptsSubscriptionSecrets() {
        WebPushSettings disabled = new WebPushSettings(key(65, 4), "", key(32, 7), "v1", "mailto:test@example.test", "push.example.test");
        assertThat(disabled.enabled()).isFalse();

        WebPushSettings enabled = settings("push.example.test");
        assertThat(enabled.enabled()).isTrue();
        SubscriptionCipher cipher = new SubscriptionCipher(enabled);
        var encrypted = cipher.encrypt("https://push.example.test/private-token", "endpoint");
        assertThat(new String(encrypted.bytes())).doesNotContain("private-token");
        assertThat(cipher.decrypt(encrypted.bytes(), "endpoint", encrypted.keyId()))
                .isEqualTo("https://push.example.test/private-token");
        assertThatThrownBy(() -> cipher.decrypt(encrypted.bytes(), "auth", encrypted.keyId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void allowsOnlyConfiguredHttpsProviderWithPublicResolution() throws Exception {
        var policy = new PushEndpointPolicy(settings("push.example.test"), host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})});
        assertThat(policy.validate("https://push.example.test/subscription/secret").getHost()).isEqualTo("push.example.test");
        assertRejected(() -> policy.validate("https://other.example.test/subscription"));
        assertRejected(() -> policy.validate("http://push.example.test/subscription"));

        var privatePolicy = new PushEndpointPolicy(settings("push.example.test"), host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})});
        assertRejected(() -> privatePolicy.validate("https://push.example.test/subscription"));
    }

    @Test void publicSubscriptionViewNeverContainsProviderSecrets() throws Exception {
        var view = new PushViews.PushSubscription(601L, java.util.UUID.randomUUID(), "Windows Chrome", true,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(view);
        assertThat(payload).contains("subscriptionId", "deviceName").doesNotContain("endpoint", "p256dh", "auth");
    }

    private static WebPushSettings settings(String hosts) {
        return new WebPushSettings(key(65, 4), key(32, 9), key(32, 7), "v1", "mailto:test@example.test", hosts);
    }
    private static String key(int size, int first) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, (byte) first);
        bytes[0] = (byte) first;
        return BASE64.encodeToString(bytes);
    }
    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.code()).isEqualTo(ApiErrorCode.PUSH_ENDPOINT_REJECTED));
    }
}
