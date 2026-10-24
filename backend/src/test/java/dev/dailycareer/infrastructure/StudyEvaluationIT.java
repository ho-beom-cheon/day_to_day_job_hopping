package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.*;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.ai.*;
import dev.dailycareer.learning.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** Isolated PostgreSQL fixture: exercises the completion trigger, scope queries and frozen evaluations. */
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes=DailyCareerApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"management.server.port=0","daily-career.ai.poll-delay-ms=1000000"})
class StudyEvaluationIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired JdbcTemplate jdbc;@Autowired LearningCatalog catalog;@Autowired LearningCommandService commands;
    @Autowired StudyEvaluationService evaluations;@Autowired LearningQueryService learning;@Autowired ObjectMapper json;
    @Autowired ExtraLearningService extraLearning;
    long user,other,course;LocalDate today;List<Long> sources;
    @BeforeEach void seed() {
        user=jdbc.queryForObject("insert into daily_career.app_user(email,display_name) values (?,'Evaluation fixture') returning id",Long.class,UUID.randomUUID()+"@example.test");
        other=jdbc.queryForObject("insert into daily_career.app_user(email,display_name) values (?,'Other fixture') returning id",Long.class,UUID.randomUUID()+"@example.test");
        var fixture=new LearningFixture(jdbc,catalog,false,3);
        sources=jdbc.queryForList("select content_version_id from daily_career.study_topic_detail order by content_version_id limit 3",Long.class);
        jdbc.update("update daily_career.curriculum_item_content set content_version_id=? where content_version_id=? and curriculum_unit_id in(select id from daily_career.curriculum_unit where curriculum_version_id=?)",sources.get(0),fixture.readVersion,fixture.template);
        jdbc.update("update daily_career.curriculum_item_content set content_version_id=? where content_version_id=? and curriculum_unit_id in(select id from daily_career.curriculum_unit where curriculum_version_id=?)",sources.get(1),fixture.practiceVersion,fixture.template);
        catalog.publish(fixture.template);today=learning.today();
        commands.create(user,new LearningCommandService.Create(fixture.template,today.toString(),new LearningCommandService.Policy(30,0,List.of())),UUID.randomUUID());
        course=learning.currentId(user);
    }
    private long content(int day,boolean required,int sequence) {
        return jdbc.queryForObject("""
            select c.id from daily_career.assigned_content c join daily_career.learning_day_item i on i.id=c.learning_day_item_id
            join daily_career.assigned_learning_day a on a.learning_day_id=i.learning_day_id
            where i.user_id=? and a.day_no=? and i.required=? and c.sequence=?
            """,Long.class,user,day,required,sequence);
    }
    private void complete(long content,LocalDate day) {jdbc.update("update daily_career.assigned_content set completed_at=? where id=?",day.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),content);}
    private JsonNode scope(String kind,String key) {for(var s:evaluations.list(user).path("scopes"))if(s.path("kind").asText().equals(kind)&&s.path("key").asText().equals(key))return s;throw new AssertionError("missing scope");}
    private long historical(String subject,String title,String body,String goal,String digest) {
        long source=jdbc.queryForObject("insert into daily_career.learning_content(subject_id,title) select id,? from daily_career.subject where code=? returning id",Long.class,title,subject);
        long version=jdbc.queryForObject("insert into daily_career.learning_content_version(learning_content_id,version_no,status,body_markdown,source_references,content_digest) values (?,1,'DRAFT',?,'[]',?) returning id",Long.class,source,body,digest);
        jdbc.update("insert into daily_career.learning_content_detail(content_version_id,title,learning_objective,key_points,interview_points) values (?,?,?,jsonb_build_array(?::text),'[]')",version,title,goal,goal);
        jdbc.update("update daily_career.learning_content_version set status='PUBLISHED',published_at=clock_timestamp() where id=?",version);
        return version;
    }
    @Test void historicalAndOptionalSourcesPrepareAllElevenGoalsWithoutRewritingLearningRecords() {
        long sql=historical("SQL","SQL과 데이터 모델링","# JOIN과 데이터 모델\\n\\nJOIN 전에 기준 엔터티와 관계의 카디널리티를 확인합니다. 필요한 열만 선택하고 실행 계획으로 인덱스 사용과 예상 행 수를 점검합니다.",
                "JOIN의 기준 열과 결과 행 수의 관계를 설명한다.","0b491897718a411fc8876988e72f51dd1dfed905b3ead4855ccb2bcc9f933f0d");
        long spring=historical("SPRING","Spring Boot 웹 계층","# Spring Boot 요청 흐름\\n\\nController는 HTTP 경계를, Service는 업무 규칙과 트랜잭션을, Repository는 저장소 접근을 담당합니다. 입력 DTO와 응답 DTO를 분리합니다.",
                "HTTP 요청의 Controller와 Service 처리 흐름을 설명한다.","c6d526f661460665471216081ed9c71923b9b35caf3180a3057708b62bee93d1");
        var versions=new ArrayList<Long>(List.of(sql,spring));
        versions.addAll(jdbc.queryForList("select content_version_id from daily_career.extra_learning_catalog where slug in ('algorithm-batch-dedup','algorithm-priority-deadline') order by slug",Long.class));
        complete(content(1,true,1),today);
        for(int n=0;n<versions.size();n++) {
            long assigned=content(n+1,false,1);jdbc.update("update daily_career.assigned_content set content_version_id=? where id=?",versions.get(n),assigned);complete(assigned,today);
        }
        jdbc.update("update daily_career.schedule_policy set study_minutes=60 where user_id=?",user);
        var before=jdbc.queryForList("select to_jsonb(a)::text snapshot from daily_career.assigned_content a where user_id=? order by id",String.class,user);
        var sourceBefore=jdbc.queryForMap("select * from daily_career.learning_content_version where id=?",sql);
        for(String kind:List.of("DAILY","WEEKLY","MONTHLY","FINAL")) {
            var s=scope(kind,kind.equals("DAILY")?today.toString():kind.equals("FINAL")?"all":"1");
            assertThat(s.path("objectiveCount").asInt()).isEqualTo(11);
            assertThat(s.path("sourceCount").asInt()).isEqualTo(5);
            assertThat(s.path("sourceReady").asBoolean()).isTrue();assertThat(s.path("sourceIssues")).isEmpty();
        }
        var daily=evaluations.create(user,"DAILY",today.toString(),null,UUID.randomUUID());
        assertThat(daily.path("objectiveCount").asInt()).isEqualTo(11);assertThat(daily.path("partCount").asInt()).isEqualTo(3);
        String snapshot=jdbc.queryForObject("select scope_snapshot::text from daily_career.study_evaluation where id=?",String.class,daily.path("id").asLong());
        assertThat(snapshot).contains("INNER JOIN은 조인 조건을 만족하는 양쪽 행만 결과에 포함합니다.","Controller는 HTTP 경계에서 요청을 받고 검증 결과와 응답 형식을 조정합니다.");
        assertThat(snapshot).contains("v"+sql+":g1","v"+spring+":g1");
        assertThat(jdbc.queryForMap("select * from daily_career.learning_content_version where id=?",sql)).isEqualTo(sourceBefore);
        assertThat(jdbc.queryForList("select to_jsonb(a)::text snapshot from daily_career.assigned_content a where user_id=? order by id",String.class,user)).isEqualTo(before);
        // Reach the period end without altering completion attribution or the daily snapshot.
        jdbc.update("update daily_career.learning_day set local_date=local_date-interval '20 days' where user_id=?",user);
        jdbc.update("update daily_career.assigned_learning_day set month_no=1 where user_id=? and day_no=4",user);
        assertThat(evaluations.create(user,"WEEKLY","1",null,UUID.randomUUID()).path("objectiveCount").asInt()).isEqualTo(11);
        assertThat(evaluations.create(user,"MONTHLY","1",null,UUID.randomUUID()).path("objectiveCount").asInt()).isEqualTo(11);
        assertThat(evaluations.create(user,"FINAL","all",null,UUID.randomUUID()).path("objectiveCount").asInt()).isEqualTo(11);
    }
    @Test void unknownOrDigestMismatchedSourcesStayBlockedWithSpecificReasonsAndAreNotOffered() {
        long fake=historical("SQL","미검수 원문","아직 근거가 부족합니다.","JOIN을 설명한다.","0b491897718a411fc8876988e72f51dd1dfed905b3ead4855ccb2bcc9f933f0d");
        long assigned=content(1,false,1);jdbc.update("update daily_career.assigned_content set content_version_id=? where id=?",fake,assigned);complete(assigned,today);
        var s=scope("DAILY",today.toString());assertThat(s.path("sourceReady").asBoolean()).isFalse();
        assertThat(s.path("objectiveCount").asInt()).isEqualTo(1);
        assertThat(s.path("sourceIssues").toString()).contains("미검수 원문","검수 기록 연결 필요","원문·해설의 평가 근거 부족");
        assertThatThrownBy(()->evaluations.create(user,"DAILY",today.toString(),null,UUID.randomUUID())).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
        jdbc.update("insert into daily_career.extra_learning_catalog(slug,content_version_id,display_order) values (?,?,?)",UUID.randomUUID().toString(),fake,1000+(int)user);
        assertThat(extraLearning.topics(user,ExtraLearningService.Mode.EXPLORE)).noneMatch(t->t.contentVersionId()==fake);
        var offered=extraLearning.topics(user,ExtraLearningService.Mode.EXPLORE);
        assertThat(offered).anyMatch(t->t.title().equals("배치 중복 제거와 자료구조 선택"));
        for(var topic:offered)assertThat(jdbc.queryForObject("select ready from daily_career.study_source_readiness where content_version_id=?",Boolean.class,topic.contentVersionId())).isTrue();
    }
    @Test void completionAddsExtraGoalsDeduplicatesRepeatsAndKeepsCreatedScopeUnchanged() {
        complete(content(1,true,1),today);
        assertThat(scope("DAILY",today.toString()).path("objectiveCount").asInt()).isEqualTo(3);
        var request=UUID.randomUUID();var first=evaluations.create(user,"DAILY",today.toString(),null,request);long id=first.path("id").asLong();
        assertThat(first.path("parts").size()).isEqualTo(2);
        assertThat(first.toString()).doesNotContain("modelAnswer","sourceEvidence","body_markdown");
        assertThat(evaluations.create(user,"DAILY",today.toString(),null,request).path("id")).isEqualTo(first.path("id"));
        assertThatThrownBy(()->evaluations.get(other,id)).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
        assertThatThrownBy(()->evaluations.start(other,id,1)).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
        complete(content(1,false,1),today); // Same source, optional repetition.
        assertThat(scope("DAILY",today.toString()).path("objectiveCount").asInt()).isEqualTo(3);
        long extra=content(2,false,1);jdbc.update("update daily_career.assigned_content set content_version_id=? where id=?",sources.get(2),extra);complete(extra,today);
        for(String kind:List.of("DAILY","WEEKLY","MONTHLY"))assertThat(scope(kind,kind.equals("DAILY")?today.toString():"1").path("objectiveCount").asInt()).isEqualTo(6);
        assertThat(scope("DAILY",today.toString()).path("newObjectiveCount").asInt()).isEqualTo(3);
        assertThat(evaluations.get(user,id).path("objectiveCount").asInt()).isEqualTo(3);
        var second=evaluations.create(user,"DAILY",today.toString(),null,UUID.randomUUID());
        assertThat(second.path("objectiveCount").asInt()).isEqualTo(3);
        assertThat(second.path("objectives").toString()).doesNotContain("v"+sources.getFirst()+":");
    }
    @Test void monthScopesNeverPullPreviousMonthsAndPeriodEndReplacesDailyAndWeekly() {
        complete(content(1,true,1),today);
        complete(content(3,true,2),today.plusDays(2));
        assertThat(scope("DAILY",today.plusDays(2).toString()).path("replacement").asText()).isEqualTo("MONTHLY");
        assertThat(scope("WEEKLY","1").path("replacement").asText()).isEqualTo("MONTHLY");
        long otherMonth=content(4,false,1);jdbc.update("update daily_career.assigned_content set content_version_id=? where id=?",sources.get(2),otherMonth);complete(otherMonth,today.plusDays(3));
        assertThat(scope("MONTHLY","1").path("objectiveCount").asInt()).isEqualTo(6);
        assertThat(scope("MONTHLY","2").path("objectiveCount").asInt()).isEqualTo(3);
        assertThat(scope("FINAL","all").path("objectiveCount").asInt()).isEqualTo(9);
        assertThatThrownBy(()->evaluations.create(user,"MONTHLY","2",null,UUID.randomUUID())).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
    }
    @Test void attributionSurvivesScheduleChangesAndIncompleteStudyDoesNotEnterScope() {
        assertThat(evaluations.list(user).path("scopes")).isEmpty();
        long id=content(1,true,1);complete(id,today);
        var before=jdbc.queryForMap("select * from daily_career.study_completion where assigned_content_id=?",id);
        jdbc.update("update daily_career.assigned_learning_day set week_no=9,month_no=3 where user_id=?",user);
        complete(id,today.plusDays(10));
        assertThat(jdbc.queryForMap("select * from daily_career.study_completion where assigned_content_id=?",id)).isEqualTo(before);
    }
    @Test void answersAreHiddenUntilSubmissionAndExpirationCannotReplaceSavedAnswers() {
        complete(content(1,true,1),today);
        var created=evaluations.create(user,"DAILY",today.toString(),null,UUID.randomUUID());long id=created.path("id").asLong();
        long budget=jdbc.queryForObject("insert into daily_career.ai_budget(user_id,period_start,period_end,limit_amount) values (?,?,?,0) returning id",Long.class,user,today,today.plusDays(1));
        var assessment=json.createObjectNode();var questions=assessment.putArray("questions");
        for(var slot:created.path("parts").get(0).path("plan").path("slots")) {
            var q=questions.addObject().put("id",slot.path("id").asText()).put("type",slot.path("type").asText()).put("prompt","Synthetic test question with enough detail")
                    .put("modelAnswer","private model answer").put("explanation","private explanation").put("answer",1).put("maxPoints",10);
            q.set("objectiveIds",slot.path("objectiveIds"));q.putArray("choices").add("one").add("two").add("three").add("four");q.putArray("criteria");q.putArray("sourceEvidence");
        }
        long job=jdbc.queryForObject("insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,provider,model,prompt_version,input_snapshot,result_payload) values (?,?,'STUDY_DAILY',?,'SUCCEEDED','TEST','TEST','TEST','{}',?::jsonb) returning id",Long.class,user,budget,UUID.randomUUID().toString(),assessment.toString());
        jdbc.update("update daily_career.study_evaluation_part set generation_job_id=? where evaluation_id=? and part_no=1",job,id);
        assertThat(evaluations.get(user,id).toString()).doesNotContain("private model answer","private explanation");
        var started=evaluations.start(user,id,1);assertThat(started.toString()).doesNotContain("private model answer","private explanation");
        var saved=evaluations.answer(user,id,1,json.createObjectNode().put("q1","1").put("q2","saved answer"),started.path("parts").get(0).path("revision").asInt(),false);
        assertThatThrownBy(()->evaluations.answer(user,id,1,json.createObjectNode(),0,false)).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
        jdbc.update("update daily_career.study_evaluation_part set expires_at=clock_timestamp()-interval '1 second' where evaluation_id=? and part_no=1",id);
        var submitted=evaluations.answer(user,id,1,json.createObjectNode().put("q1","2").put("q2","late changed answer"),saved.path("parts").get(0).path("revision").asInt(),true);
        assertThat(submitted.path("parts").get(0).path("answers").path("q1").asText()).isEqualTo("1");
        assertThat(submitted.toString()).contains("private model answer","saved answer").doesNotContain("late changed answer");
        assertThat(submitted.path("parts").get(0).path("result").path("status").asText()).isEqualTo("HOLD");
        assertThatThrownBy(()->evaluations.answer(user,id,1,json.createObjectNode(),3,false)).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
        assertThatThrownBy(()->evaluations.create(user,"DAILY",today.toString(),id,UUID.randomUUID())).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
    }
}
