package dev.dailycareer.infrastructure.security;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiErrorWriter;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.auth.GoogleLogin;
import dev.dailycareer.auth.DevAuthenticationBypassFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import java.util.Collections;

@Configuration
public class SecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain managementHealthSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher(EndpointRequest.to(HealthEndpoint.class))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET).permitAll().anyRequest().denyAll())
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter writer, ObjectProvider<GoogleLogin> google,
                                            ObjectProvider<DevAuthenticationBypassFilter> devBypass) throws Exception {
        GoogleLogin login = google.getIfAvailable();
        if (login != null) login.configure(http);
        else http.addFilterBefore(new GoogleLogin.Boundary(false), CsrfFilter.class);
        DevAuthenticationBypassFilter bypass = devBypass.getIfAvailable();
        if (bypass != null) http.addFilterBefore(bypass, CsrfFilter.class);
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/oauth2/authorization/google", "/login/oauth2/code/google",
                                "/api/v1/integrations/notion/callback", "/api/v1/integrations/browser/*/callback").permitAll()
                        .requestMatchers("/api/v1/integrations/browser/slack", "/api/v1/integrations/browser/openrouter",
                                "/api/v1/integrations/browser/*/authorization-requests", "/api/v1/integrations/notion/setup").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/settings").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications", "/api/v1/notifications/settings", "/api/v1/push/config",
                                "/api/v1/push/subscriptions", "/api/v1/integrations/notion", "/api/v1/integrations/slack",
                                "/api/v1/integrations/slack/deliveries", "/api/v1/integrations/slack/deliveries/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/notifications/*/read", "/api/v1/notifications/read-all",
                                "/api/v1/push/subscriptions", "/api/v1/integrations/notion/authorization-requests",
                                "/api/v1/integrations/slack/test-messages").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/settings", "/api/v1/notifications/settings", "/api/v1/integrations/slack").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/push/subscriptions/*", "/api/v1/integrations/notion").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/today", "/api/v1/tasks/*", "/api/v1/career/facts", "/api/v1/career/claims",
                                "/api/v1/ai/usage").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/today", "/api/v1/tasks/*/start", "/api/v1/tasks/*/turns/*/submit",
                                "/api/v1/tasks/*/self-grade", "/api/v1/tasks/*/finish", "/api/v1/tasks/*/swap", "/api/v1/tasks/*/feedback",
                                "/api/v1/tasks/*/reports", "/api/v1/career/facts", "/api/v1/career/facts/*/confirm", "/api/v1/career/claims",
                                "/api/v1/career/claims/*/review", "/api/v1/career/claims/*/interview").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tasks/*/turns/*/answer", "/api/v1/tasks/*/card").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/career/facts/*", "/api/v1/career/claims/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/coding", "/api/v1/coding/settings", "/api/v1/coding/links", "/api/v1/coding/logs").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/coding/links", "/api/v1/coding/logs", "/api/v1/coding/logs/*/retry").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/coding/settings", "/api/v1/coding/links/*/hidden", "/api/v1/coding/links/*/solved-before").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/coding/logs/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/coding/links/*/hidden", "/api/v1/coding/links/*/solved-before").authenticated()
                        .anyRequest().denyAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestHandler() {
                    private final XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();
                    @Override public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
                        delegate.handle(request, response, token);
                    }
                    @Override public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
                        var headers = Collections.list(request.getHeaders(token.getHeaderName()));
                        if (headers.size() != 1 || headers.getFirst().length() < 16 || headers.getFirst().length() > 512) return null;
                        return delegate.resolveCsrfTokenValue(request, token);
                    }
                }))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (ApiResponses.isApi(request)) writer.write(request, response, ApiErrorCode.AUTH_REQUIRED);
                            else response.setStatus(401);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            if (request.getRequestURI().equals("/api/v1/auth/logout") &&
                                    (authentication == null || authentication instanceof AnonymousAuthenticationToken)) {
                                writer.write(request, response, ApiErrorCode.AUTH_REQUIRED);
                                return;
                            }
                            if (ApiResponses.isApi(request)) writer.write(request, response,
                                    exception instanceof CsrfException ? ApiErrorCode.CSRF_INVALID : ApiErrorCode.RESOURCE_NOT_FOUND);
                            else response.setStatus(403);
                        }))
                .build();
    }
}
