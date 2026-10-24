package dev.dailycareer.auth;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleLoginBoundaryTest {
    private final GoogleLogin.Boundary boundary = new GoogleLogin.Boundary(
            true, URI.create("https://app.example.test"));

    @Test
    void localLoginStartMovesToCanonicalOriginBeforeCreatingOAuthState() throws Exception {
        var request = start("http", "localhost", 8080);
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();

        boundary.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(continued).isFalse();
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location"))
                .isEqualTo("https://app.example.test/oauth2/authorization/google");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void canonicalLoginStartContinuesToGoogle() throws Exception {
        var request = start("https", "app.example.test", 443);
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();

        boundary.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

        assertThat(continued).isTrue();
        assertThat(response.getHeader("Location")).isNull();
    }

    private MockHttpServletRequest start(String scheme, String host, int port) {
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }
}
