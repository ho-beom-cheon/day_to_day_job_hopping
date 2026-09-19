package dev.dailycareer.infrastructure;

import dev.dailycareer.DailyCareerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit -Ppostgres-it gate: requires Docker and never silently skips a missing engine. */
@Testcontainers
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionMigrationIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine3.23@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired SessionRepository<? extends Session> sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @LocalServerPort int applicationPort;

    @Test void healthIsInternalAndReflectsDatabaseConnectivity() {
        var internal = http.getForEntity("http://127.0.0.1:9090/actuator/health", String.class);
        assertThat(internal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(internal.getBody()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(http.getForEntity("http://127.0.0.1:" + applicationPort + "/actuator/health", String.class)
                .getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(http.getForEntity("http://127.0.0.1:9090/actuator/env", String.class)
                .getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test void migratesCleanPostgresAndPersistsSessionAttributes() {
        roundTrip(sessions);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class)).isEqualTo(1);
    }

    private <S extends Session> void roundTrip(SessionRepository<S> repository) {
        S session = repository.createSession();
        session.setAttribute("test-id", "9223372036854775807");
        repository.save(session);
        S loaded = repository.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("test-id")).isEqualTo("9223372036854775807");
        repository.deleteById(session.getId());
        assertThat(repository.findById(session.getId())).isNull();
    }
}
