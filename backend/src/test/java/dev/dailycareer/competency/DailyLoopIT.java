package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.ai.AiModelClient;
import dev.dailycareer.ai.AiRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static dev.dailycareer.competency.DailyTaskViews.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The S1 loop on real PostgreSQL with a fake model: generation, grading, follow-ups, cost records and the AI-free fallback. */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(DailyLoopIT.FakeModelConfiguration.class)
@SpringBootTest(classes = DailyCareerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "daily-career.ai.poll-delay-ms=100", "AI_PROVIDER_TYPE=openai",
                "OPENAI_API_KEY=test-key", "OPENAI_MODEL=gpt-6-luna"})
class DailyLoopIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired DailyTaskService tasks;
    @Autowired CareerFacts career;
    @Autowired FakeModel model;
    @Autowired JdbcTemplate jdbc;

    @Test @Order(1) void runsTheWholeLoopAndRecordsMeasuredCost() {
        long user = user(null);
        Task task = tasks.startToday(user);
        assertThat(task.status()).isEqualTo("PREPARING");
        assertThat(task.competency().code()).isEqualTo("A1");
        long id = task.taskId();
        task = await(() -> tasks.view(user, id), value -> "READY".equals(value.status()));
        assertThat(task.task().question()).isNotBlank();
        assertThat(task.rubric()).as("rubric stays hidden before submission").isNull();

        tasks.start(user, id);
        tasks.saveDraft(user, id, 1, new AnswerRequest("초안", true));
        assertThat(tasks.submit(user, id, 1, new AnswerRequest("self-invocation 때문에 REQUIRES_NEW가 적용되지 않습니다.", true)).status()).isEqualTo("GRADING");
        task = await(() -> tasks.view(user, id), value -> "FEEDBACK".equals(value.status()));
        assertThat(task.result().baseScore()).isEqualTo(60);
        assertThat(task.turns()).hasSize(2);
        assertThat(task.turns().get(1).checkPoints()).as("check points stay hidden until answered").isNull();

        tasks.submit(user, id, 2, new AnswerRequest("별도 빈으로 분리하고 통합 테스트로 커밋 여부를 확인합니다.", false));
        task = await(() -> tasks.view(user, id), value -> "FEEDBACK".equals(value.status()) && !value.pending());
        assertThat(task.turns().get(1).scoreDelta()).as("requested +20 is capped").isEqualTo(15);
        assertThat(task.result().finalScore()).isEqualTo(75);
        assertThat(task.turns()).hasSize(2);
        assertThat(task.cardDraft().path("title").asText()).isEqualTo("꼬리질문 반영 카드");
        JsonNode followupInput = model.inputs("followup_turn").getLast();
        assertThat(followupInput.path("conversation")).as("only this task's own turns are sent").hasSize(2);

        tasks.saveCard(user, id, new CardRequest("프록시와 self-invocation", "요약", "면접 한 문장", "왜 적용되지 않나요?"));
        tasks.feedback(user, id, new FeedbackRequest(5, "좋았음"));
        assertThat(tasks.finish(user, id).status()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                select level_estimate from daily_career.user_competency u join daily_career.competency c on c.id=u.competency_id
                where u.user_id=? and c.code='A1'
                """, BigDecimal.class, user)).isEqualByComparingTo("2.00");

        var usage = jdbc.queryForList("select budget_cost_amount,pricing_snapshot->>'priceVerifiedAt' as verified from daily_career.ai_usage where user_id=?", user);
        assertThat(usage).hasSize(3).allSatisfy(row -> {
            assertThat((BigDecimal) row.get("budget_cost_amount")).isPositive();
            assertThat(row.get("verified")).isEqualTo("2026-10-24");
        });
        assertThat(jdbc.queryForObject("select coalesce(sum(reserved_amount),0) from daily_career.ai_job where user_id=?", BigDecimal.class, user)).isZero();
        assertThat(tasks.today(user).week().completed()).isEqualTo(1);

        Task next = tasks.startToday(user);
        assertThat(next.competency().code()).isNotEqualTo("A1");
        Task swapped = tasks.swap(user, next.taskId());
        assertThat(swapped.competency().code()).isNotIn("A1", next.competency().code());
    }

    @Test @Order(2) void fallsBackToSelfGradingWhenTheMonthlyLimitIsReached() {
        long user = user(new BigDecimal("0.01"));
        jdbc.update("update daily_career.task_bank_item set status='RETIRED' where owner_user_id is null");
        for (long competency : jdbc.queryForList("select competency_id from daily_career.competency_sheet_version where status='PUBLISHED'", Long.class)) {
            jdbc.update("""
                    insert into daily_career.task_bank_item(competency_id,sheet_version_id,format,difficulty,task_payload,rubric_payload,reserve_followups,fingerprint)
                    select s.competency_id,s.id,case when c.area='I' then 'INTERVIEW' else 'CASE' end,3,
                        '{"title":"수동 과제","situation":"상황","material":"자료","question":"원인을 설명하세요.","estimatedMinutes":20}'::jsonb,
                        '[{"criterionId":"R1","sheetCriteria":["K1"],"description":"원인","points":60},{"criterionId":"R2","sheetCriteria":["K2"],"description":"검증","points":40}]'::jsonb,
                        '[{"question":"검증은요?","checkPoints":["테스트"]}]'::jsonb, ?
                    from daily_career.competency_sheet_version s join daily_career.competency c on c.id=s.competency_id
                    where s.competency_id=? and s.status='PUBLISHED'
                    """, UUID.randomUUID().toString(), competency);
        }
        long id = tasks.startToday(user).taskId();
        Task task = tasks.submit(user, id, 1, new AnswerRequest("원인은 프록시 우회입니다.", false));
        assertThat(task.status()).isEqualTo("FEEDBACK");
        assertThat(task.gradingMode()).isEqualTo("SELF");
        assertThat(task.rubric()).hasSize(2);
        task = tasks.selfGrade(user, id, new SelfGradeRequest(List.of("R1")));
        assertThat(task.result().status()).isEqualTo("SELF_GRADED");
        assertThat(task.result().finalScore()).isEqualTo(60);
        assertThat(task.turns().get(1).question()).isEqualTo("검증은요?");
        task = tasks.submit(user, id, 2, new AnswerRequest("통합 테스트로 확인합니다.", false));
        assertThat(task.turns().get(1).checkPoints()).containsExactly("테스트");
        assertThat(tasks.finish(user, id).status()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.ai_job where user_id=?", Integer.class, user)).isZero();
    }

    @Test @Order(3) void reviewsAResumeClaimAndStartsAPressureInterviewFromConfirmedFacts() {
        long user = user(null);
        var fact = career.createFact(user, new CareerFacts.FactRequest("기업 현황 조회 재구축", "공공 금융기관", "2020-12", "2021-10",
                "조회 화면 개발", "Java, Oracle", "기업 현황 조회 성능 개선 담당", "수치 미확인"));
        assertThat(fact.status()).isEqualTo("CANDIDATE");
        fact = career.confirmFact(user, fact.factId(), fact.revision());
        var claim = career.createClaim(user, new CareerFacts.ClaimRequest(fact.factId().toString(), "조회 시간을 330ms(0.66초)로 줄였습니다."));
        assertThat(career.review(user, claim.claimId()).reviewPending()).isTrue();
        var reviewed = await(() -> career.claim(user, claim.claimId()), value -> value.reviewStatus() != null);
        assertThat(reviewed.reviewStatus()).isEqualTo("NEEDS_FIX");
        assertThat(model.inputs("claim_review").getLast().path("fact").path("projectName").asText()).isEqualTo("기업 현황 조회 재구축");

        String revised = "조회 시간을 330ms로 줄였습니다(측정 조건 확인 필요).";
        var patched = career.patchClaim(user, claim.claimId(), new CareerFacts.ClaimPatch(null, revised), career.claim(user, claim.claimId()).revision());
        assertThat(career.review(user, claim.claimId()).reviewPending()).isTrue();
        await(() -> career.claim(user, claim.claimId()), value -> !value.reviewPending());
        assertThat(patched.claimText()).isEqualTo("조회 시간을 330ms(0.66초)로 줄였습니다.");
        assertThat(model.inputs("claim_review").getLast().path("claim").asText()).isEqualTo(revised);

        long id = career.interview(user, claim.claimId()).taskId();
        Task task = await(() -> tasks.view(user, id), value -> "READY".equals(value.status()));
        assertThat(task.format()).isEqualTo("INTERVIEW");
        assertThat(jdbc.queryForObject("select owner_user_id from daily_career.task_bank_item b join daily_career.daily_task t on t.bank_item_id=b.id where t.id=?",
                Long.class, id)).isEqualTo(user);
        assertThat(model.inputs("task_generate").getLast().path("claimContext").path("claim").asText()).isEqualTo(revised);
    }

    private long user(BigDecimal limit) {
        long id = jdbc.queryForObject("insert into daily_career.app_user(display_name,email) values ('loop',?) returning id", Long.class, UUID.randomUUID() + "@example.test");
        jdbc.update("insert into daily_career.user_setting(user_id) values (?)", id);
        if (limit != null) jdbc.update("update daily_career.user_setting set ai_monthly_limit_krw=? where user_id=?", limit, id);
        return id;
    }

    private static <T> T await(Supplier<T> read, java.util.function.Predicate<T> done) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        T value = read.get();
        while (!done.test(value)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting, last value: " + value);
            try { Thread.sleep(100); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            value = read.get();
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelConfiguration {
        @Bean @Primary FakeModel fakeModel() { return new FakeModel(); }
    }

    static final class FakeModel implements AiModelClient {
        private final ObjectMapper json = new ObjectMapper();
        private final AtomicInteger counter = new AtomicInteger();
        private final List<String[]> calls = new CopyOnWriteArrayList<>();

        List<JsonNode> inputs(String schema) {
            return calls.stream().filter(call -> call[0].equals(schema)).map(call -> {
                try { return json.readTree(call[1]); } catch (Exception invalid) { throw new IllegalStateException(invalid); }
            }).toList();
        }

        @Override public Completion complete(String model, AiRequest request) throws java.io.IOException {
            calls.add(new String[]{request.schemaName(), request.input()});
            int n = counter.incrementAndGet();
            String result = switch (request.schemaName()) {
                case "task_generate" -> """
                        {"tasks":[%s,%s]}""".formatted(task("이체 재시도 장애 " + n + "-a"), task("배치 재실행 장애 " + n + "-b"));
                case "grading" -> """
                        {"status":"GRADED","criteria":[{"criterionId":"R1","earnedPoints":25,"reason":"a"},{"criterionId":"R2","earnedPoints":20,"reason":"b"},
                         {"criterionId":"R3","earnedPoints":10,"reason":"c"},{"criterionId":"R4","earnedPoints":5,"reason":"d"}],
                         "feedback":"좋은 출발","missingPoints":["검증"],"improvedAnswer":"개선","holdReason":"",
                         "followupQuestion":"그럼 어떻게 검증하죠?","followupCheckPoints":["통합 테스트","로그"],
                         "cardDraft":{"title":"초안","summary":"요약","interviewLine":"한 문장","reviewPrompt":"질문"}}""";
                case "followup_turn" -> """
                        {"feedback":"근거 보완","scoreDelta":20,"checkPointsMet":["통합 테스트"],"nextQuestion":"","nextCheckPoints":[],
                         "cardDraft":{"title":"꼬리질문 반영 카드","summary":"요약","interviewLine":"한 문장","reviewPrompt":"질문"}}""";
                case "claim_review" -> """
                        {"status":"NEEDS_FIX","issues":[{"type":"NUMBER_MISMATCH","detail":"330ms와 0.66초는 다른 값"}],
                         "likelyQuestions":["무엇과 비교했나요?"],"suggestedRevision":"조회 구조를 바꿔 응답 시간을 줄였습니다."}""";
                default -> throw new IllegalStateException(request.schemaName());
            };
            return new Completion(json.readTree(result), "req-" + n, new Tokens(5_000, 1_000, 1_500, 300));
        }

        private static String task(String title) {
            return """
                    {"title":"%s","situation":"상황","material":"로그 발췌","question":"원인과 수정 방향, 검증 방법을 설명하세요.","estimatedMinutes":20,
                     "rubric":[{"criterionId":"R1","sheetCriteria":["K1"],"description":"원인","points":25},
                               {"criterionId":"R2","sheetCriteria":["K2"],"description":"예외","points":25},
                               {"criterionId":"R3","sheetCriteria":["K3"],"description":"전파","points":25},
                               {"criterionId":"R4","sheetCriteria":["K4"],"description":"검증","points":25}],
                     "modelAnswerPoints":["프록시"],"reserveFollowups":[{"question":"검증은요?","checkPoints":["테스트"]}]}""".formatted(title);
        }
    }
}
