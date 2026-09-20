package dev.dailycareer.infrastructure.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieConfigurationTest {
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void secureProductionAndLocalCookiesKeepMatchingDeletionAttributes(boolean secure) {
        CookieSerializer serializer = new SessionCookieConfiguration().cookieSerializer(secure);
        String name = secure ? "__Host-CAREER_SESSION" : "CAREER_SESSION";
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, "session-id"));
        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).startsWith(name + "=").contains("HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Domain=");
        assertThat(cookie.contains("Secure")).isEqualTo(secure);
        var cleared = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieSerializer.CookieValue(request, cleared, ""));
        assertThat(cleared.getHeader("Set-Cookie")).startsWith(name + "=;").contains("Max-Age=0", "Path=/", "HttpOnly").doesNotContain("Domain=");
    }
}
