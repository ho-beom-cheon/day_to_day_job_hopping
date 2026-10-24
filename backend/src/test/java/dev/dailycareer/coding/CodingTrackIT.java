package dev.dailycareer.coding;

import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The coding test track on real PostgreSQL: seed links, recommendations, retry schedule, weekly target and isolation. */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "AI_PROVIDER_TYPE=openai", "OPENAI_API_KEY=test-key", "OPENAI_MODEL=gpt-6-luna"})
class CodingTrackIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CodingTrack coding;
    @Autowired JdbcTemplate jdbc;

    @Test void seedHoldsOnlyCheckedLinksWithoutProblemText() {
        long user = user();
        var links = coding.links(user);
        assertThat(links).hasSize(73).allSatisfy(link -> {
            assertThat(link.url()).startsWith("https://school.programmers.co.kr/learn/courses/30/lessons/").endsWith(link.siteProblemRef());
            assertThat(CodingRules.CATEGORIES).contains(link.category());
            assertThat(link.own()).isFalse();
        });
        assertThat(links.stream().map(CodingTrack.Link::category).distinct()).hasSize(16);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='daily_career' and table_name='coding_problem_link'"
                + " and column_name in ('body','description','prompt','statement')", Integer.class)).isZero();
    }

    @Test void recommendsUpToTheRemainingWeeklyTargetAndCountsTheWeek() {
        long user = user();
        var empty = coding.overview(user);
        assertThat(empty.weeklyTarget()).isEqualTo(2);
        assertThat(empty.recommendations()).hasSize(2).allSatisfy(item -> assertThat(item.reason()).isEqualTo("NEW_CATEGORY"));
        assertThat(empty.recommendations().getFirst().link().category()).isEqualTo("DATA_STRUCTURE");
        assertThat(empty.recommendations().getFirst().link().level()).isEqualTo(1);

        var first = empty.recommendations().getFirst().link();
        coding.createLog(user, request(first.linkId(), "SUCCESS"));
        var after = coding.overview(user);
        assertThat(after.weekDone()).isEqualTo(1);
        assertThat(after.recommendations()).hasSize(1).noneMatch(item -> item.link().linkId().equals(first.linkId()));

        coding.updateSettings(user, new CodingTrack.Settings(0));
        assertThat(coding.overview(user).recommendations()).isEmpty();
        assertThatThrownBy(() -> coding.updateSettings(user, new CodingTrack.Settings(8))).isInstanceOf(ApiException.class);
    }

    @Test void missesAreRetriedFirstAndTheScheduleFollowsTheNewestAttempt() {
        long user = user();
        long link = coding.links(user).stream().filter(item -> item.category().equals("DP")).findFirst().orElseThrow().linkId();
        var missed = coding.createLog(user, new CodingTrack.LogRequest(String.valueOf(link), null, null, null, "Java", "SOLUTION_VIEWED", 40, "점화식", "",
                LocalDate.now(CodingTrack.SEOUL).minusDays(3)));
        assertThat(missed.retryStep()).isEqualTo(1);
        assertThat(missed.nextRetryAt()).isEqualTo(LocalDate.now(CodingTrack.SEOUL));
        assertThat(missed.category()).isEqualTo("DP");

        var overview = coding.overview(user);
        assertThat(overview.dueRetries()).extracting(CodingTrack.Log::logId).containsExactly(missed.logId());
        assertThat(overview.recommendations().getFirst().reason()).isEqualTo("RETRY_DUE");

        var retried = coding.createLog(user, request(link, "SUCCESS"));
        assertThat(retried.retryStep()).isEqualTo(2);
        assertThat(retried.nextRetryAt()).isEqualTo(LocalDate.now(CodingTrack.SEOUL).plusDays(7));
        assertThat(jdbc.queryForObject("select retry_step from daily_career.coding_practice_log where id=?", Integer.class, missed.logId())).isZero();
        assertThat(coding.overview(user).dueRetries()).isEmpty();
        assertThatThrownBy(() -> coding.addRetry(user, missed.logId(), missed.revision())).isInstanceOf(ApiException.class);
    }

    @Test void manualRetryOnlyOnTheNewestCleanAttempt() {
        long user = user();
        var log = coding.createLog(user, new CodingTrack.LogRequest(null, "Two Sum", "https://leetcode.com/problems/two-sum/", "TWO_POINTER", "Java",
                "SUCCESS", 15, "", "", null));
        assertThat(log.site()).isEqualTo("LEETCODE");
        assertThat(log.problemLinkId()).isNull();
        var retry = coding.addRetry(user, log.logId(), log.revision());
        assertThat(retry.retryStep()).isEqualTo(2);
        assertThat(retry.nextRetryAt()).isEqualTo(LocalDate.now(CodingTrack.SEOUL).plusDays(7));
        assertThatThrownBy(() -> coding.addRetry(user, log.logId(), retry.revision()))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    @Test void ownLinksAndHiddenSeedsStayPerUser() {
        long owner = user(), other = user();
        var own = coding.createLink(owner, new CodingTrack.LinkRequest("배열 회전", "https://www.acmicpc.net/problem/1", 2, "IMPLEMENTATION"));
        assertThat(own.own()).isTrue();
        assertThat(coding.links(other)).noneMatch(link -> link.linkId().equals(own.linkId()));
        long seed = coding.links(owner).getFirst().linkId();
        assertThat(coding.mark(owner, seed, "HIDDEN", true).hidden()).isTrue();
        assertThat(coding.links(other).stream().filter(link -> link.linkId() == seed).findFirst().orElseThrow().hidden()).isFalse();
        assertThat(coding.overview(owner).recommendations()).noneMatch(item -> item.link().linkId() == seed);

        var log = coding.createLog(owner, request(own.linkId(), "FAILED"));
        assertThatThrownBy(() -> coding.patchLog(other, log.logId(), new CodingTrack.LogPatch(null, null, 5, null, null), log.revision()))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> coding.createLink(owner, new CodingTrack.LinkRequest("x", "javascript:alert(1)", null, "OTHER"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> coding.createLog(owner, new CodingTrack.LogRequest(null, "x", null, "SORT", "", "SUCCESS", null, "", "",
                LocalDate.now(CodingTrack.SEOUL).plusDays(1)))).isInstanceOf(ApiException.class);
    }

    @Test void solvedBeforeLeavesRecommendationsWithoutCountingTowardTheWeekOrRates() {
        long user = user();
        var first = coding.overview(user).recommendations().getFirst().link();
        var marked = coding.mark(user, first.linkId(), "SOLVED_BEFORE", true);
        assertThat(marked.solvedBefore()).isTrue();
        assertThat(marked.hidden()).isFalse();
        assertThat(marked.attempts()).isZero();

        var overview = coding.overview(user);
        assertThat(overview.weekDone()).isZero();
        assertThat(overview.categoryStats()).isEmpty();
        assertThat(overview.recommendations()).hasSize(2).noneMatch(item -> item.link().linkId().equals(first.linkId()));
        assertThat(coding.logs(user)).isEmpty();

        // One mark per link: hiding replaces it, and clearing the other kind leaves it alone.
        assertThat(coding.mark(user, first.linkId(), "HIDDEN", true)).satisfies(link -> {
            assertThat(link.hidden()).isTrue();
            assertThat(link.solvedBefore()).isFalse();
        });
        assertThat(coding.mark(user, first.linkId(), "SOLVED_BEFORE", false).hidden()).isTrue();
        assertThat(coding.mark(user, first.linkId(), "HIDDEN", false).hidden()).isFalse();
        assertThat(coding.overview(user).recommendations().getFirst().link().linkId()).isEqualTo(first.linkId());
    }

    private static CodingTrack.LogRequest request(long link, String result) {
        return new CodingTrack.LogRequest(String.valueOf(link), null, null, null, "Java", result, 20, "", "", null);
    }

    private long user() {
        long id = jdbc.queryForObject("insert into daily_career.app_user(display_name,email) values ('coder',?) returning id", Long.class, UUID.randomUUID() + "@example.test");
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", id);
        return id;
    }
}
