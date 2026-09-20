package dev.dailycareer.auth;

import dev.dailycareer.user.UserAccountService;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration(proxyBeanMethods = false)
public class GoogleLoginConfiguration {
    @Bean
    GoogleLogin googleLogin(JdbcTemplate jdbc, UserAccountService users,
            @Value("${GOOGLE_CLIENT_ID:}") String clientId, @Value("${GOOGLE_CLIENT_SECRET:}") String secret,
            @Value("${GOOGLE_ALLOWED_EMAILS:}") String emails, @Value("${APP_ORIGIN:http://localhost:8080}") String origin) {
        if (clientId.isBlank() && secret.isBlank() && emails.isBlank()) return null;
        Set<String> allowed = Arrays.stream(emails.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        if (clientId.isBlank() || secret.isBlank() || allowed.isEmpty() || allowed.stream().anyMatch(s -> !s.contains("@") || s.length() > 320)) {
            throw new IllegalArgumentException("Google client ID, secret and allowed emails must all be configured");
        }
        URI base = URI.create(origin);
        boolean loopback = Set.of("localhost", "127.0.0.1").contains(base.getHost() == null ? "" : base.getHost());
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || (!base.getPath().isEmpty() && !base.getPath().equals("/"))
                || !("https".equals(base.getScheme()) || (loopback && "http".equals(base.getScheme())))) {
            throw new IllegalArgumentException("APP_ORIGIN must be an HTTPS origin or local HTTP origin");
        }
        var google = ClientRegistration.withRegistrationId("google").clientName("Google")
                .clientId(clientId).clientSecret(secret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(origin.replaceAll("/$", "") + "/login/oauth2/code/google")
                .scope("openid", "email", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com").userNameAttributeName("sub").build();
        return new GoogleLogin(new InMemoryClientRegistrationRepository(google), new ExpiringAuthorizationRequests(jdbc), users, allowed);
    }
}
