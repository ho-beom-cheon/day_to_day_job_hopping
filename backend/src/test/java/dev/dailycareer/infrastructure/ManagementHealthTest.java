package dev.dailycareer.infrastructure;

import dev.dailycareer.infrastructure.security.SecurityConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP/security boundary; DB status is simulated here, actual DB remains an IT gate. */
@SpringBootTest(classes = ManagementHealthTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ManagementHealthTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, SessionAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
    @Import(SecurityConfiguration.class)
    static class TestApplication {
        @Bean AtomicReference<Status> databaseStatus() { return new AtomicReference<>(Status.UP); }
        @Bean HealthIndicator dbHealthIndicator(AtomicReference<Status> databaseStatus) {
            return () -> Health.status(databaseStatus.get()).withDetail("sensitive", "not exposed").build();
        }
    }
    @Autowired TestRestTemplate http;
    @Autowired AtomicReference<Status> databaseStatus;
    @LocalServerPort int applicationPort;
    @LocalManagementPort int managementPort;

    @Test void managementHealthReturnsOnlyAggregateStatus() {
        var result = http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health", Map.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(Map.of("status", "UP"));
    }

    @Test void databaseFailureProducesUnhealthyStatus() {
        databaseStatus.set(Status.DOWN);
        try {
            var result = http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health", Map.class);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(result.getBody()).isEqualTo(Map.of("status", "DOWN"));
        } finally { databaseStatus.set(Status.UP); }
    }

    @Test void mainPortAndOtherManagementEndpointsRemainClosed() {
        assertThat(http.getForEntity("http://127.0.0.1:" + applicationPort + "/actuator/health", String.class)
                .getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(http.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/env", String.class)
                .getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(http.postForEntity("http://127.0.0.1:" + managementPort + "/actuator/health", Map.of(), String.class)
                .getStatusCode().is2xxSuccessful()).isFalse();
    }
}
