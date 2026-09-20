package dev.dailycareer.infrastructure.security;

import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiErrorWriter;
import dev.dailycareer.common.api.ApiResponses;
import dev.dailycareer.auth.GoogleLogin;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter writer, ObjectProvider<GoogleLogin> google) throws Exception {
        GoogleLogin login = google.getIfAvailable();
        if (login != null) login.configure(http);
        else http.addFilterBefore(new GoogleLogin.Boundary(false), CsrfFilter.class);
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/oauth2/authorization/google", "/login/oauth2/code/google").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/curriculum-templates", "/api/v1/curriculums/current",
                                "/api/v1/curriculums/*", "/api/v1/curriculums/*/months", "/api/v1/curriculums/*/months/*",
                                "/api/v1/curriculums/*/weeks", "/api/v1/curriculums/*/weeks/*", "/api/v1/learning-days",
                                "/api/v1/learning-days/*", "/api/v1/learning-days/*/sessions", "/api/v1/learning-sessions/*",
                                "/api/v1/learning-sessions/*/contents", "/api/v1/learning-contents/*", "/api/v1/problems/*",
                                "/api/v1/problem-attempts/*", "/api/v1/wrong-answers", "/api/v1/wrong-answers/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/curriculums", "/api/v1/learning-sessions/*/start",
                                "/api/v1/curriculums/*/reschedule-preview", "/api/v1/curriculums/*/reschedule",
                                "/api/v1/rest-days", "/api/v1/learning-sessions/*/complete", "/api/v1/learning-contents/*/complete",
                                "/api/v1/problem-attempts", "/api/v1/problems/*/reports", "/api/v1/wrong-answers/*/resolve",
                                "/api/v1/wrong-answers/*/reopen").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/wrong-answers/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rest-days/*").authenticated()
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
