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
                                "/api/v1/integrations/browser/*/authorization-requests", "/api/v1/integrations/notion/setup", "/api/v1/integrations/notion/available-targets").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/common-codes", "/api/v1/dashboard", "/api/v1/settings").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/curriculum-templates", "/api/v1/curriculums/current",
                                "/api/v1/curriculums/*", "/api/v1/curriculums/*/months", "/api/v1/curriculums/*/months/*",
                                "/api/v1/curriculums/*/weeks", "/api/v1/curriculums/*/weeks/*", "/api/v1/learning-days",
                                "/api/v1/learning-days/*", "/api/v1/learning-days/*/sessions", "/api/v1/learning-sessions/*",
                                "/api/v1/learning-sessions/*/contents", "/api/v1/learning-contents/*", "/api/v1/problems/*",
                                "/api/v1/problem-attempts", "/api/v1/problem-attempts/*", "/api/v1/wrong-answers", "/api/v1/wrong-answers/*",
                                "/api/v1/study-evaluations", "/api/v1/study-evaluations/*",
                                "/api/v1/tests", "/api/v1/tests/*", "/api/v1/test-attempts", "/api/v1/test-attempts/*",
                                "/api/v1/test-attempts/*/result",
                                "/api/v1/learning-progress", "/api/v1/learning-progress/*", "/api/v1/learning-calendar", "/api/v1/evaluations/monthly",
                                "/api/v1/evaluations/*", "/api/v1/notifications", "/api/v1/notifications/settings", "/api/v1/push/config", "/api/v1/push/subscriptions",
                                "/api/v1/integrations/notion", "/api/v1/integrations/notion/targets", "/api/v1/integrations/notion/sync-history",
                                "/api/v1/integrations/notion/sync-jobs/*", "/api/v1/integrations/slack", "/api/v1/integrations/slack/deliveries", "/api/v1/integrations/slack/deliveries/*",
                                "/api/v1/ai/jobs", "/api/v1/ai/jobs/*", "/api/v1/ai/usage", "/api/v1/ai/learning-topics",
                                "/api/v1/ai/practice-sets", "/api/v1/ai/practice-sets/*", "/api/v1/ai/practice-sets/by-content/*",
                                "/api/v1/ai/practice-runs", "/api/v1/ai/practice-runs/*",
                                "/api/v1/ai/wrong-notes", "/api/v1/ai/wrong-notes/*/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/study-program", "/api/v1/study-program/preview").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/extra-learning", "/api/v1/extra-learning/topics", "/api/v1/extra-learning/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/extra-learning", "/api/v1/extra-learning/*/time").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/curriculums", "/api/v1/curriculums/*/restart", "/api/v1/curriculums/*/switch", "/api/v1/learning-sessions/*/start",
                                "/api/v1/curriculums/*/reschedule-preview", "/api/v1/curriculums/*/reschedule",
                                "/api/v1/rest-days", "/api/v1/learning-sessions/*/complete", "/api/v1/learning-contents/*/complete",
                                "/api/v1/problem-attempts", "/api/v1/problems/*/reports", "/api/v1/wrong-answers/*/resolve",
                                "/api/v1/study-evaluations", "/api/v1/study-evaluations/*/parts/*/generate", "/api/v1/study-evaluations/*/parts/*/start", "/api/v1/study-evaluations/*/parts/*/submit", "/api/v1/study-evaluations/*/parts/*/grade",
                                "/api/v1/wrong-answers/*/reopen", "/api/v1/tests/*/attempts", "/api/v1/test-attempts/*/submit",
                                "/api/v1/evaluations/monthly", "/api/v1/ai/explanations", "/api/v1/ai/practice-problems", "/api/v1/ai/learning-topics",
                                "/api/v1/ai/wrong-answer-reviews", "/api/v1/ai/daily-feedback", "/api/v1/ai/monthly-reviews",
                                "/api/v1/ai/practice-sets/*/answers", "/api/v1/ai/practice-sets/*/runs",
                                "/api/v1/ai/practice-sets/*/reports",
                                "/api/v1/ai/practice-runs/*/answers",
                                "/api/v1/notifications/*/read", "/api/v1/notifications/read-all", "/api/v1/push/subscriptions").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations/notion/authorization-requests", "/api/v1/integrations/notion/sync",
                                "/api/v1/integrations/notion/sync-jobs/*/retry", "/api/v1/integrations/slack/test-messages").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/wrong-answers/*", "/api/v1/ai/wrong-notes/*/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/settings", "/api/v1/notifications/settings", "/api/v1/integrations/slack").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/study-evaluations/*/parts/*/answers", "/api/v1/test-attempts/*/answers/*", "/api/v1/integrations/notion/targets").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rest-days/*", "/api/v1/push/subscriptions/*", "/api/v1/integrations/notion").authenticated()
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
