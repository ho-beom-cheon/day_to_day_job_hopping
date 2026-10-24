package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.learning.LearningCatalog;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ApprovedCatalogSeedIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6")
            .withUsername("daily_career");
    static SingleConnectionDataSource dataSource;
    static JdbcTemplate jdbc;

    @BeforeAll static void migrateApprovedCatalog() {
        dataSource = new SingleConnectionDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        Flyway.configure().dataSource(dataSource).defaultSchema("public")
                .locations("classpath:db/migration", "classpath:db/dev-seed").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll static void closeConnection() { if (dataSource != null) dataSource.destroy(); }

    @Test void seedIsPublishedAndPassesTheApplicationCatalogValidator() {
        long version = jdbc.queryForObject("select id from daily_career.curriculum_version where status='PUBLISHED'", Long.class);
        var plan = new LearningCatalog(jdbc, new ObjectMapper()).plan(version, true);
        assertThat(plan.months()).hasSize(6);
        assertThat(plan.weeks()).hasSize(6);
        assertThat(plan.days()).hasSize(6);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.problem_version where status='PUBLISHED'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.test_version where status='PUBLISHED'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("select test_type from daily_career.test order by id", String.class))
                .containsExactly("DAILY", "WEEKLY", "MONTHLY", "FINAL");
    }

    @Test void migrationIsRepeatableWithoutDuplicatingApprovedData() {
        var rerun = Flyway.configure().dataSource(dataSource).defaultSchema("public")
                .locations("classpath:db/migration", "classpath:db/dev-seed").load().migrate();
        assertThat(rerun.migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.curriculum where code='BACKEND-FOUNDATIONS-6M'", Integer.class)).isOne();
    }
}
