package dev.dailycareer.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Closed foundation. OIDC/CSRF/logout routes await canonical OpenAPI (GAP-001).
        // CSRF remains enabled and stored in the JDBC-backed HTTP session.
        return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> response.setStatus(403)))
                .build();
    }
}
