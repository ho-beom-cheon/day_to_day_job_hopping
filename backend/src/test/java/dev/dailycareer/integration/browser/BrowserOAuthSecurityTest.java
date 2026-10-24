package dev.dailycareer.integration.browser;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BrowserOAuthSecurityTest {
    @Test void requiresHttpsForSlackAndAllowsOnlyLoopbackHttpForOpenRouter() {
        assertThat(BrowserOAuthSettings.safeCallback("http://localhost:8080/callback", false)).isFalse();
        assertThat(BrowserOAuthSettings.safeCallback("http://localhost:8080/callback", true)).isTrue();
        for (String url : new String[]{"http://example.com/callback", "https://a@b/callback", "https://a/callback?state=x", "https://a/callback#x"})
            assertThat(BrowserOAuthSettings.safeCallback(url, true)).isFalse();
    }
    @Test void bindsEncryptedCredentialsToOwnerProviderAndReference() {
        var settings = new BrowserOAuthSettings(Base64.getEncoder().encodeToString(new byte[32]), "", "", "", "", "");
        var cipher = new BrowserSecretCipher(settings);
        String encrypted = cipher.encrypt("1:slack:ref", "webhook-secret");
        assertThat(encrypted).doesNotContain("webhook-secret");
        assertThat(cipher.decrypt("1:slack:ref", encrypted)).isEqualTo("webhook-secret");
        assertThatThrownBy(() -> cipher.decrypt("2:slack:ref", encrypted)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("1:openrouter:ref", encrypted)).isInstanceOf(IllegalStateException.class);
    }
    @Test void usesStandardS256PkceVector() {
        assertThat(BrowserConnections.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }
}
