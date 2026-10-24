package dev.dailycareer.integration.slack;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlackSecurityTest {
    @Test void requiresCompleteSecretConfigurationAndResolvesOnlyItsReference() {
        var disabled = new SlackSettings("env://slack/webhook", "", "#learning", "hooks.slack.com");
        assertThat(disabled.enabled()).isFalse();

        var enabled = settings();
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.resolve("env://slack/webhook").getHost()).isEqualTo("hooks.slack.com");
        assertThatThrownBy(() -> enabled.resolve("another-secret")).isInstanceOf(IllegalStateException.class);
    }

    @Test void acceptsOnlyConfiguredHttpsHostWithPublicDns() throws Exception {
        var publicPolicy = new SlackEndpointPolicy(settings(), host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})});
        assertThat(publicPolicy.validate(URI.create("https://hooks.slack.com/services/T/B/X")).getHost())
                .isEqualTo("hooks.slack.com");
        assertThatThrownBy(() -> publicPolicy.validate(URI.create("https://example.test/services/T/B/X")))
                .isInstanceOf(IllegalArgumentException.class);

        var privatePolicy = new SlackEndpointPolicy(settings(), host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})});
        assertThatThrownBy(() -> privatePolicy.validate(URI.create("https://hooks.slack.com/services/T/B/X")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsOAuthWebhookWithoutServerWideWebhookConfiguration() throws Exception {
        var personalOnly = new SlackSettings("", "", "", "hooks.slack.com");
        assertThat(personalOnly.enabled()).isFalse();
        var policy = new SlackEndpointPolicy(personalOnly, host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})});

        assertThat(policy.validate(URI.create("https://hooks.slack.com/services/T/B/X")).getHost())
                .isEqualTo("hooks.slack.com");
        assertThatThrownBy(() -> policy.validate(URI.create("https://example.test/services/T/B/X")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(URI.create("http://hooks.slack.com/services/T/B/X")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void validatesOnlyCanonicalHistorySorts() {
        assertThat(SlackService.order(null)).isEqualTo("delivery.created_at desc,delivery.id");
        assertThat(SlackService.order("createdAt,asc")).isEqualTo("delivery.created_at,delivery.id");
        assertThatThrownBy(() -> SlackService.order("status,asc"))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo(ApiErrorCode.INVALID_SORT_FIELD));
    }

    private static SlackSettings settings() {
        return new SlackSettings("env://slack/webhook", "https://hooks.slack.com/services/T/B/X",
                "#learning", "hooks.slack.com");
    }
}
