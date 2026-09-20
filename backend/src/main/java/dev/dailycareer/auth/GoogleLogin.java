package dev.dailycareer.auth;

import dev.dailycareer.user.AppPrincipal;
import dev.dailycareer.user.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public final class GoogleLogin {
    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    private final ClientRegistrationRepository clients;
    private final ExpiringAuthorizationRequests requests;
    private final UserAccountService users;
    private final Set<String> allowedEmails;
    public GoogleLogin(ClientRegistrationRepository clients, ExpiringAuthorizationRequests requests,
                       UserAccountService users, Set<String> allowedEmails) {
        this.clients = clients; this.requests = requests; this.users = users; this.allowedEmails = allowedEmails;
    }

    public void configure(HttpSecurity http) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        http.addFilterBefore(new Boundary(true), OAuth2AuthorizationRequestRedirectFilter.class);
        http.oauth2Login(login -> login.clientRegistrationRepository(clients)
                .authorizedClientService(new OAuth2AuthorizedClientService() {
                    @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, String name) { return null; }
                    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) { /* Login-only: discard tokens. */ }
                    @Override public void removeAuthorizedClient(String id, String name) { }
                })
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver).authorizationRequestRepository(requests))
                .userInfoEndpoint(endpoint -> endpoint.oidcUserService(request -> {
                    // Signature, issuer, audience, timestamps and nonce are validated by Spring before this callback.
                    var token = request.getIdToken();
                    try {
                        long id = users.login(token.getIssuer().toString(), token.getSubject(), token.getEmail(),
                                Boolean.TRUE.equals(token.getEmailVerified()), token.getFullName(), token.getPicture(), allowedEmails);
                        return new DefaultOidcUser(AUTHORITIES, token,
                                new OidcUserInfo(Map.of("sub", token.getSubject(), "app_user_id", id)), "sub");
                    } catch (OAuth2AuthenticationException exception) { throw exception; }
                    catch (RuntimeException exception) { throw new OAuth2AuthenticationException(new OAuth2Error("LOGIN_FAILED")); }
                }))
                .successHandler((request, response, authentication) -> {
                    OidcUser oidc = (OidcUser) authentication.getPrincipal();
                    long id = ((Number) oidc.getClaim("app_user_id")).longValue();
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(id), null, AUTHORITIES));
                    SecurityContextHolder.setContext(context);
                    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
                    redirect(response, "/");
                })
                .failureHandler((request, response, exception) -> {
                    new SecurityContextLogoutHandler().logout(request, response, null);
                    String code = exception instanceof OAuth2AuthenticationException oauth
                            && "ACCOUNT_NOT_ALLOWED".equals(oauth.getError().getErrorCode()) ? "ACCOUNT_NOT_ALLOWED" : "LOGIN_FAILED";
                    redirect(response, "/login?error=" + code);
                }));
    }

    public static final class Boundary extends OncePerRequestFilter {
        private final boolean enabled;
        public Boundary(boolean enabled) { this.enabled = enabled; }
        @Override protected boolean shouldNotFilter(HttpServletRequest request) {
            return !Set.of("/oauth2/authorization/google", "/login/oauth2/code/google").contains(request.getServletPath());
        }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Referrer-Policy", "no-referrer");
            boolean start = request.getServletPath().equals("/oauth2/authorization/google");
            boolean valid = enabled && request.getMethod().equals("GET") && (start ? request.getParameterMap().isEmpty() : validCallback(request));
            if (!valid) { failure(request, response); return; }
            try { chain.doFilter(request, response); }
            catch (RuntimeException exception) {
                // Never log exceptions carrying authorization codes, state, tokens or provider responses.
                if (!response.isCommitted()) failure(request, response);
                else throw new ServletException("Authentication response already committed");
            }
        }
        private static boolean validCallback(HttpServletRequest request) {
            if (!single(request, "state", 16, 1024)) return false;
            boolean code = request.getParameterMap().containsKey("code"), error = request.getParameterMap().containsKey("error");
            return code != error && (code ? single(request, "code", 1, 4096) : single(request, "error", 1, 100));
        }
        private static boolean single(HttpServletRequest request, String name, int min, int max) {
            String[] values = request.getParameterValues(name);
            return values != null && values.length == 1 && values[0].length() >= min && values[0].length() <= max;
        }
        private static void failure(HttpServletRequest request, HttpServletResponse response) throws IOException {
            new SecurityContextLogoutHandler().logout(request, response, null);
            redirect(response, "/login?error=LOGIN_FAILED");
        }
    }
    private static void redirect(HttpServletResponse response, String destination) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setStatus(302);
        response.setHeader("Location", destination);
    }
}
