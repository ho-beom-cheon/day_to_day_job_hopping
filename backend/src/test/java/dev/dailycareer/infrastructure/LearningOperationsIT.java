package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.DailyCareerApplication;
import dev.dailycareer.ai.AiPracticePlan;
import dev.dailycareer.learning.LearningCatalog;
import dev.dailycareer.user.AppPrincipal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static dev.dailycareer.infrastructure.LearningContractAssertions.contract;

/** Real HTTP/security/JDBC session/CSRF and PostgreSQL. Authentication is a test-only persisted principal. */
@Testcontainers
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@SpringBootTest(classes={DailyCareerApplication.class,LearningOperationsIT.TimeConfiguration.class},
        webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"management.server.port=0","server.servlet.session.cookie.secure=false"})
class LearningOperationsIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres@sha256:5a1b083da321ba67c86c3169d22778c561fa0935f17acbff7bbc0537f1e50dd6").withUsername("daily_career");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    static final AtomicReference<Instant> NOW=new AtomicReference<>(Instant.parse("2026-09-20T15:00:00Z"));
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock testLearningClock() {return new Clock() {
            public ZoneId getZone(){return ZoneId.of("Asia/Seoul");}public Clock withZone(ZoneId z){return this;}public Instant instant(){return NOW.get();}
        };}
    }
    @Autowired JdbcTemplate jdbc;@Autowired LearningCatalog catalog;@Autowired SessionRepository<? extends Session> repository;
    @Autowired dev.dailycareer.learning.CatalogImportService imports;
    @LocalServerPort int port;
    static final ObjectMapper JSON=new ObjectMapper();
    static LearningFixture fixture;
    @BeforeEach void seed(){if(fixture==null)fixture=new LearningFixture(jdbc,catalog,true);}
    @BeforeEach void time(){NOW.set(Instant.parse("2026-09-20T15:00:00Z"));}

    @Test void emptyStateAuthenticationAndOwnershipBoundaries() throws Exception {
        var a=new Browser();var b=new Browser();
        var current=a.get("/curriculums/current");ok(current,200);assertThat(data(current).isNull()).isTrue();assertThat(current.headers().firstValue("etag")).isEmpty();
        assertThat(data(a.get("/dashboard")).get("recentSessions")).isEmpty();
        assertThat(data(a.get("/learning-days/today")).isNull()).isTrue();
        assertThat(data(a.get("/learning-days?from=2026-09-21&to=2026-09-22")).isEmpty()).isTrue();
        assertThat(data(a.get("/learning-days/2026-09-21/sessions")).isEmpty()).isTrue();
        error(a.get("/learning-days/2026-09-21?curriculumId=9223372036854775807"),404,"RESOURCE_NOT_FOUND");
        var created=a.create();ok(created,201);String course=data(created).get("curriculumId").asText();
        assertThat(data(b.get("/dashboard")).get("recentSessions")).isEmpty();
        String session=firstSession(a,"2026-09-21");String content=data(a.get("/learning-sessions/"+session+"/contents")).get(0).get("contentId").asText();
        for(String path:List.of("/curriculums/"+course,"/curriculums/"+course+"/weeks","/learning-sessions/"+session,"/learning-sessions/"+session+"/contents","/learning-contents/"+content,
                "/learning-days/2026-09-21?curriculumId="+course))error(b.get(path),404,"RESOURCE_NOT_FOUND");
        error(b.post("/learning-sessions/"+session+"/start",null,null),404,"RESOURCE_NOT_FOUND");
        error(b.post("/learning-contents/"+content+"/complete",null,null),404,"RESOURCE_NOT_FOUND");
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base()+"/curriculums/current")).GET().build(),HttpResponse.BodyHandlers.ofString());
        error(anonymous,401,"AUTH_REQUIRED");
        error(a.send("POST","/learning-sessions/"+session+"/start",null,null,false),403,"CSRF_INVALID");
        jdbc.update("update daily_career.app_user set status='SUSPENDED' where id=?",a.user);
        error(a.get("/curriculums/"+course),401,"AUTH_REQUIRED");
    }

    @Test void allReadEndpointsHonorCanonicalShapesAndReadDoesNotStartLearning() throws Exception {
        var b=new Browser();var templates=data(b.get("/curriculum-templates"));for(var t:templates)contract("CurriculumTemplate",t);
        var create=b.create();ok(create,201);var c=data(create);contract("Curriculum",c);String course=c.get("curriculumId").asText();
        assertThat(c.get("totalRequiredSessions").asInt()).isEqualTo(6);assertThat(c.get("totalLearningDays").asInt()).isEqualTo(6);
        assertThat(c.get("endDate").asText()).isEqualTo("2026-09-28");assertThat(c.get("nominalEndDate").asText()).isEqualTo("2027-03-20");
        assertThat(create.headers().firstValue("location").orElseThrow()).isEqualTo("/api/v1/curriculums/"+course);
        for(String path:List.of("/curriculums/current","/curriculums/"+course))contract("Curriculum",data(b.get(path)));
        for(var m:data(b.get("/curriculums/"+course+"/months")))contract("CurriculumMonth",m);
        contract("CurriculumMonth",data(b.get("/curriculums/"+course+"/months/1")));
        for(var w:data(b.get("/curriculums/"+course+"/weeks")))contract("WeekSummary",w);
        contract("WeekDetail",data(b.get("/curriculums/"+course+"/weeks/1")));
        contract("LearningDay",data(b.get("/learning-days/today")));
        var range=data(b.get("/learning-days?from=2026-09-20&to=2026-09-29"));assertThat(range.size()).isEqualTo(10);
        for(var d:range)contract("LearningDay",d);
        var rest=data(b.get("/learning-days/2026-09-26"));assertThat(rest.get("kind").asText()).isEqualTo("REST");assertThat(rest.get("learningDayId").isNull()).isTrue();
        var none=data(b.get("/learning-days/2026-09-29"));assertThat(none.get("kind").asText()).isEqualTo("NONE");
        for(var s:data(b.get("/learning-days/2026-09-21/sessions")))contract("SessionSummary",s);
        String session=firstSession(b,"2026-09-21");var s=data(b.get("/learning-sessions/"+session));contract("Session",s);assertThat(s.get("status").asText()).isEqualTo("NOT_STARTED");
        for(var content:data(b.get("/learning-sessions/"+session+"/contents"))) {
            contract("ContentSummary",content);var full=b.get("/learning-contents/"+content.get("contentId").asText());contract("LearningContent",data(full));
            assertThat(full.body()).doesNotContain("secret explanation","answer_key","rubric");
        }
        assertThat(jdbc.queryForObject("select count(*) from daily_career.learning_day_item where user_id=? and activity_type='TEST' and test_version_id=?",Integer.class,b.user,fixture.testVersion)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.learning_session where user_id=?",Integer.class,b.user)).isZero();
    }

    @Test void restOnStartDateAndFutureLearningAndStrictDateRanges() throws Exception {
        var b=new Browser();ok(b.post("/curriculums",createBody("2026-09-26"),key()),201);
        assertThat(data(b.get("/learning-days/2026-09-26")).get("kind").asText()).isEqualTo("REST");
        String s=firstSession(b,"2026-09-28");error(b.post("/learning-sessions/"+s+"/start",null,null),409,"LEARNING_NOT_AVAILABLE");
        for(String path:List.of("/learning-days/2026-02-30","/learning-days/2026-9-21","/learning-days?from=2026-09-22&to=2026-09-21",
                "/learning-days?from=2026-01-01&to=2026-04-04"))error(b.get(path),400,"VALIDATION_FAILED");
        assertThat(data(b.get("/learning-days?from=2026-01-01&to=2026-04-03")).size()).isEqualTo(93);
        NOW.set(Instant.parse("2026-10-01T00:00:00Z"));ok(b.post("/learning-sessions/"+s+"/start",null,null),200);
    }

    @Test void lifecycleCountsSelfReportedMinutesOnceAndPreservesPriorCourse() throws Exception {
        var b=new Browser();String course=data(b.create()).get("curriculumId").asText();String s=firstSession(b,"2026-09-21");
        var contents=data(b.get("/learning-sessions/"+s+"/contents"));String read=contents.get(0).get("contentId").asText(),practice=contents.get(1).get("contentId").asText();
        error(b.post("/learning-contents/"+read+"/complete",null,null),409,"SESSION_NOT_STARTED");
        error(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":30}",key()),409,"SESSION_NOT_STARTED");
        var start=b.post("/learning-sessions/"+s+"/start",null,null);ok(start,200);contract("Session",data(start));
        assertThat(data(b.post("/learning-sessions/"+s+"/start",null,null))).isEqualTo(data(start));
        assertThat(data(b.get("/curriculums/current")).get("scheduleRevision").asInt()).isEqualTo(2);
        error(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":30}",key()),422,"SESSION_CONTENT_INCOMPLETE");
        var readDone=b.post("/learning-contents/"+read+"/complete",null,null);ok(readDone,200);contract("ContentCompleteResult",data(readDone));
        assertThat(data(b.post("/learning-contents/"+read+"/complete",null,null))).isEqualTo(data(readDone));
        error(b.post("/learning-contents/"+practice+"/complete",null,null),422,"CONTENT_PROBLEMS_INCOMPLETE");
        submit(b.user,Long.parseLong(course));ok(b.post("/learning-contents/"+practice+"/complete",null,null),200);
        String key=key();var completed=b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":30}",key);ok(completed,200);contract("SessionCompleteResult",data(completed));
        assertThat(data(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":30}",key))).isEqualTo(data(completed));
        assertThat(data(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":30}",key()))).isEqualTo(data(completed));
        error(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":31}",key()),409,"SESSION_ALREADY_COMPLETED");
        error(b.post("/learning-sessions/"+s+"/start",null,null),409,"SESSION_ALREADY_COMPLETED");
        assertThat(data(b.get("/learning-days/today")).get("actualMinutes").asInt()).isEqualTo(30);
        assertThat(eventCount("SESSION_COMPLETED",Long.parseLong(s))).isEqualTo(1);
        NOW.set(Instant.parse("2026-10-01T00:00:00Z"));
        for(var day:data(b.get("/learning-days?from=2026-09-22&to=2026-09-28")))if(day.get("kind").asText().equals("STUDY"))finish(b,day.get("sessions").get(0).get("sessionId").asText());
        var done=data(b.get("/curriculums/current"));assertThat(done.get("status").asText()).isEqualTo("COMPLETED");assertThat(done.get("currentDayNo").isNull()).isTrue();assertThat(done.get("completionRate").asDouble()).isEqualTo(100);
        assertThat(data(b.get("/users/me")).get("curriculumId").asText()).isEqualTo(course);
        assertThat(eventCount("CURRICULUM_COMPLETED",Long.parseLong(course))).isEqualTo(1);
        var next=b.post("/curriculums",createBody("2026-10-01"),key());ok(next,201);assertThat(data(next).get("curriculumId").asText()).isNotEqualTo(course);
        assertThat(data(b.get("/curriculums/"+course))).isEqualTo(done);
        assertThat(data(b.get("/learning-contents/"+read)).get("completedAt").isNull()).isFalse();
    }

    @Test void sessionSizedAiQuestionsCanCompletePracticeWithoutCuratedAttempt() throws Exception {
        var b = new Browser();
        b.create();
        String session = firstSession(b, "2026-09-21");
        var contents = data(b.get("/learning-sessions/" + session + "/contents"));
        String practice = contents.get(1).get("contentId").asText();
        ok(b.post("/learning-sessions/" + session + "/start", null, null), 200);

        long budget = jdbc.queryForObject("""
                insert into daily_career.ai_budget(user_id,period_start,period_end,limit_amount)
                values (?,?::date,?::date,0) returning id
                """, Long.class, b.user, "2026-09-21", "2026-09-22");
        long job = jdbc.queryForObject("""
                insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,provider,model,prompt_version,input_snapshot,result_payload)
                values (?,?,'PRACTICE_PROBLEMS',?,'SUCCEEDED','TEST','test','ai-practice-v2',?::jsonb,'{"questions":[1,2,3,4,5]}'::jsonb) returning id
                """, Long.class, b.user, budget, "test-ai-practice-" + practice, "{\"contentId\":\"" + practice + "\"}");
        for (int number = 1; number <= 4; number++) {
            jdbc.update("""
                    insert into daily_career.ai_practice_attempt(user_id,ai_job_id,question_no,answer_no,correct)
                    values (?,?,?,?,false)
                    """, b.user, job, number, 1);
        }
        error(b.post("/learning-contents/" + practice + "/complete", null, null), 422, "CONTENT_PROBLEMS_INCOMPLETE");
        jdbc.update("""
                insert into daily_career.ai_practice_attempt(user_id,ai_job_id,question_no,answer_no,correct)
                values (?,?,?,?,false)
                """, b.user, job, 5, 1);
        ok(b.post("/learning-contents/" + practice + "/complete", null, null), 200);
    }

    @Test void inputValidationAndIdempotencyReplay() throws Exception {
        var b=new Browser();String body=createBody("2026-09-21");
        error(b.post("/curriculums",body,null),400,"IDEMPOTENCY_KEY_REQUIRED");
        error(b.post("/curriculums",body,"bad"),400,"INVALID_REQUEST");
        error(b.post("/curriculums",body.replace("\"2026-09-21\"","\"2026-09-20\""),key()),400,"VALIDATION_FAILED");
        error(b.post("/curriculums",body.replace("\"2026-09-21\"","\"2026-12-21\""),key()),400,"VALIDATION_FAILED");
        error(b.post("/curriculums",body.replace("120","14"),key()),400,"VALIDATION_FAILED");
        error(b.post("/curriculums",body.replace("SUNDAY","SATURDAY"),key()),400,"VALIDATION_FAILED");
        error(b.post("/curriculums",body.replace("\"restDaysPerWeek\":2","\"restDaysPerWeek\":1"),key()),400,"VALIDATION_FAILED");
        error(b.post("/curriculums",body.replace("{\"templateId\"","{\"extra\":true,\"templateId\""),key()),400,"INVALID_REQUEST");
        error(b.post("/curriculums",body.replace("\"templateId\":\""+fixture.template+"\"","\"templateId\":"+fixture.template),key()),400,"INVALID_REQUEST");
        String key=key();var c=b.post("/curriculums",body,key);ok(c,201);
        NOW.set(Instant.parse("2026-09-22T00:00:00Z"));assertThat(data(b.post("/curriculums",body,key))).isEqualTo(data(c));
        error(b.post("/curriculums",body.replace("120","121"),key),409,"IDEMPOTENCY_KEY_REUSED");
        error(b.post("/curriculums",createBody("2026-09-22"),key()),409,"CURRICULUM_ALREADY_ACTIVE");
    }

    @Test void concurrentAssignmentCannotCreateTwoActiveCourses() throws Exception {
        var b=new Browser();var replies=concurrent(()->b.post("/curriculums",createBody("2026-09-21"),key()));
        assertThat(replies.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.user_curriculum where user_id=?",Integer.class,b.user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.assigned_content where user_id=?",Integer.class,b.user)).isEqualTo(18);
    }

    @Test void concurrentCompletionAndDifferentPayloadNeverDoubleCount() throws Exception {
        var b=new Browser();String course=data(b.create()).get("curriculumId").asText();submit(b.user,Long.parseLong(course));String s=firstSession(b,"2026-09-21");
        ok(b.post("/learning-sessions/"+s+"/start",null,null),200);
        for(var c:data(b.get("/learning-sessions/"+s+"/contents"))) {
            var replies=concurrent(()->b.post("/learning-contents/"+c.get("contentId").asText()+"/complete",null,null));
            assertThat(replies.stream().map(HttpResponse::statusCode)).containsOnly(200);
            assertThat(data(replies.get(0))).isEqualTo(data(replies.get(1)));
        }
        var replies=concurrent(()->b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":42}",key()));
        assertThat(replies.stream().map(HttpResponse::statusCode)).containsOnly(200);
        assertThat(data(replies.get(0))).isEqualTo(data(replies.get(1)));
        assertThat(data(b.get("/learning-days/today")).get("actualMinutes").asInt()).isEqualTo(42);
        assertThat(eventCount("SESSION_COMPLETED",Long.parseLong(s))).isEqualTo(1);
        assertThat(data(b.get("/curriculums/current")).get("scheduleRevision").asInt()).isEqualTo(3);
    }

    @Test void failedOutboxWriteRollsBackEntireCompletionAndKeyCanBeRetried() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();submit(b.user,course);String s=firstSession(b,"2026-09-21");
        ok(b.post("/learning-sessions/"+s+"/start",null,null),200);
        for(var c:data(b.get("/learning-sessions/"+s+"/contents")))ok(b.post("/learning-contents/"+c.get("contentId").asText()+"/complete",null,null),200);
        // Only this fixture's event is rejected; all writes are real and rollback is observed after HTTP 500.
        jdbc.execute("alter table daily_career.outbox_event add constraint fixture_reject_event check (event_key <> 'SESSION_COMPLETED:"+s+"')");
        String key=key();
        try {error(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":20}",key),500,"INTERNAL_SERVER_ERROR");}
        finally {jdbc.execute("alter table daily_career.outbox_event drop constraint fixture_reject_event");}
        var state=data(b.get("/learning-sessions/"+s));assertThat(state.get("status").asText()).isEqualTo("IN_PROGRESS");assertThat(state.get("actualMinutes").asInt()).isZero();
        assertThat(data(b.get("/curriculums/current")).get("scheduleRevision").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.idempotency_record where user_id=? and idempotency_key=?",Integer.class,b.user,key)).isZero();
        ok(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":20}",key),200);
    }

    @Test void publicationIsExplicitAndRejectsIncompleteComposition() {
        var draft=new LearningFixture(jdbc,catalog,false);
        assertThat(catalog.templates().stream().map(v->v.templateId())).doesNotContain(draft.template);
        jdbc.update("delete from daily_career.curriculum_item_content where curriculum_unit_id=?",draft.firstUnit);
        assertThatThrownBy(()->catalog.publish(draft.template)).hasMessage("CONTENT_NOT_READY");
        assertThat(jdbc.queryForObject("select status from daily_career.curriculum_version where id=?",String.class,draft.template)).isEqualTo("DRAFT");
        assertThatThrownBy(()->jdbc.update("update daily_career.curriculum_release set title='changed' where curriculum_version_id=?",fixture.template)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update daily_career.curriculum_unit set title='changed' where id=?",fixture.firstUnit)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update daily_career.learning_content_version set body_markdown='changed' where id=?",fixture.readVersion)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("delete from daily_career.curriculum_item_content where curriculum_unit_id=?",fixture.firstUnit)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void assignmentOver366DaysRollsBackAndUnpublishedTemplatesCannotBeAssigned() throws Exception {
        var longPlan=new LearningFixture(jdbc,catalog,true,10);var draft=new LearningFixture(jdbc,catalog,false);
        var b=new Browser();String body=createBody("2026-09-21").replace("\"templateId\":\""+fixture.template+"\"","\"templateId\":\""+longPlan.template+"\"")
                .replace("\"restDaysPerWeek\":2","\"restDaysPerWeek\":6").replace("[\"SATURDAY\",\"SUNDAY\"]","[\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]");
        error(b.post("/curriculums",body,key()),422,"RESCHEDULE_OUT_OF_RANGE");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.user_curriculum where user_id=?",Integer.class,b.user)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.idempotency_record where user_id=?",Integer.class,b.user)).isZero();
        error(b.post("/curriculums",createBody("2026-09-21").replace("\"templateId\":\""+fixture.template+"\"","\"templateId\":\""+draft.template+"\""),key()),409,"TEMPLATE_NOT_PUBLISHED");
    }

    @Test void retirementPreservesAssignedContentAndBlocksNewAssignment() throws Exception {
        var old=new LearningFixture(jdbc,catalog,true);var b=new Browser();String body=createBody("2026-09-21").replace("\"templateId\":\""+fixture.template+"\"","\"templateId\":\""+old.template+"\"");
        var assigned=b.post("/curriculums",body,key());ok(assigned,201);String s=firstSession(b,"2026-09-21");String c=data(b.get("/learning-sessions/"+s+"/contents")).get(0).get("contentId").asText();
        var original=data(b.get("/learning-contents/"+c));
        jdbc.update("update daily_career.curriculum_version set status='RETIRED' where id=?",old.template);
        jdbc.update("update daily_career.learning_content_version set status='RETIRED' where id=?",old.readVersion);
        assertThat(data(b.get("/learning-contents/"+c))).isEqualTo(original);
        ok(b.post("/learning-sessions/"+s+"/start",null,null),200);ok(b.post("/learning-contents/"+c+"/complete",null,null),200);
        error(new Browser().post("/curriculums",body,key()),409,"TEMPLATE_NOT_PUBLISHED");
        assertThatThrownBy(()->jdbc.update("update daily_career.curriculum_version set status='PUBLISHED' where id=?",old.template)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void identicalKeyRaceHasOneEffectAndCanReplayAfterBusy() throws Exception {
        var b=new Browser();String key=key();String body=createBody("2026-09-21");
        var replies=concurrent(()->b.post("/curriculums",body,key));
        assertThat(replies.stream().map(HttpResponse::statusCode)).contains(201).allMatch(status->status==201 || status==409);
        for(var r:replies)if(r.statusCode()==409)error(r,409,"REQUEST_IN_PROGRESS");
        var replay=b.post("/curriculums",body,key);ok(replay,201);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.user_curriculum where user_id=?",Integer.class,b.user)).isEqualTo(1);
    }

    @Test void restAddPreviewAndApplyMatchAndPreserveAssignedIds() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        String originalId=data(b.get("/learning-days/2026-09-22")).get("learningDayId").asText();
        String body=preview("REST_ADD","2026-09-22","\"2026-09-22\"","\"PERSONAL\"","null");
        var created=b.postMatch("/curriculums/"+course+"/reschedule-preview",body,key(),1);ok(created,201);
        var p=data(created);assertThat(p.get("action").asText()).isEqualTo("REST_ADD");assertThat(p.get("affectedLearningDayCount").asInt()).isEqualTo(5);
        assertThat(p.get("previousEndDate").asText()).isEqualTo("2026-09-28");assertThat(p.get("newEndDate").asText()).isEqualTo("2026-09-29");
        String previewId=p.get("previewId").asText();String apply="{\"date\":\"2026-09-22\",\"reason\":\"PERSONAL\",\"previewId\":\""+previewId+"\"}";
        var applied=b.postMatch("/rest-days",apply,key(),1);ok(applied,200);assertThat(applied.headers().firstValue("etag")).contains("\"2\"");
        assertThat(data(applied).get("affectedLearningDayCount").asInt()).isEqualTo(p.get("affectedLearningDayCount").asInt());
        assertThat(data(b.get("/learning-days/2026-09-22")).get("kind").asText()).isEqualTo("REST");
        assertThat(data(b.get("/learning-days/2026-09-23")).get("learningDayId").asText()).isEqualTo(originalId);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.schedule_change where user_curriculum_id=?",Integer.class,course)).isEqualTo(1);
        error(b.postMatch("/rest-days",apply,key(),2),409,"RESCHEDULE_PREVIEW_CONSUMED");
    }

    @Test void regularRestCanBeRemovedAndPolicyCanBeReplaced() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        var remove=data(b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("REST_REMOVE","2026-09-26","\"2026-09-26\"","null","null"),key(),1));
        String previewId=remove.get("previewId").asText();ok(b.deleteMatch("/rest-days/2026-09-26?previewId="+previewId,key(),1),200);
        assertThat(data(b.get("/learning-days/2026-09-26")).get("kind").asText()).isEqualTo("STUDY");
        assertThat(data(b.get("/curriculums/"+course)).get("endDate").asText()).isEqualTo("2026-09-26");
        String policy="{\"dailyStudyMinutes\":90,\"restDaysPerWeek\":2,\"restWeekdays\":[\"WEDNESDAY\",\"SUNDAY\"]}";
        var p=data(b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("POLICY_APPLY","2026-09-22","null","null",policy),key(),2));
        ok(b.postMatch("/curriculums/"+course+"/reschedule","{\"previewId\":\""+p.get("previewId").asText()+"\"}",key(),2),200);
        var current=data(b.get("/curriculums/"+course));assertThat(current.get("scheduleRevision").asInt()).isEqualTo(3);
        assertThat(current.get("studyPolicy").get("dailyStudyMinutes").asInt()).isEqualTo(90);
        assertThat(current.get("studyPolicy").get("restWeekdays").toString()).contains("WEDNESDAY","SUNDAY");
        assertThat(data(b.get("/learning-days/2026-09-26")).get("kind").asText()).isEqualTo("STUDY");
    }

    @Test void startedDayStaleRevisionExpiredPreviewAndOwnershipAreRejected() throws Exception {
        var a=new Browser();var b=new Browser();long course=data(a.create()).get("curriculumId").asLong();String session=firstSession(a,"2026-09-21");
        var stale=data(a.postMatch("/curriculums/"+course+"/reschedule-preview",preview("REST_ADD","2026-09-21","\"2026-09-21\"","\"WORK\"","null"),key(),1));
        ok(a.post("/learning-sessions/"+session+"/start",null,null),200);
        error(a.postMatch("/rest-days","{\"date\":\"2026-09-21\",\"reason\":\"WORK\",\"previewId\":\""+stale.get("previewId").asText()+"\"}",key(),2),409,"RESCHEDULE_PREVIEW_MISMATCH");
        error(a.postMatch("/curriculums/"+course+"/reschedule-preview",preview("REST_ADD","2026-09-21","\"2026-09-21\"","\"WORK\"","null"),key(),2),409,"RESCHEDULE_LOCKED_DAY");
        var p=data(a.postMatch("/curriculums/"+course+"/reschedule-preview",preview("SHIFT_BACKLOG","2026-09-22","null","null","null"),key(),2));
        String previewId=p.get("previewId").asText();error(b.postMatch("/curriculums/"+course+"/reschedule","{\"previewId\":\""+previewId+"\"}",key(),2),404,"RESOURCE_NOT_FOUND");
        jdbc.update("update daily_career.schedule_preview set created_at=created_at-interval '11 minutes',expires_at=expires_at-interval '11 minutes' where id=?",Long.parseLong(previewId));
        error(a.postMatch("/curriculums/"+course+"/reschedule","{\"previewId\":\""+previewId+"\"}",key(),2),409,"RESCHEDULE_PREVIEW_EXPIRED");
        error(a.postMatch("/curriculums/"+course+"/reschedule-preview",preview("SHIFT_BACKLOG","2026-09-22","null","null","null"),key(),1),412,"PRECONDITION_FAILED");
    }

    @Test void previewMismatchAndFailedAuditWriteRollbackThenRetry() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        var p=data(b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("REST_ADD","2026-09-22","\"2026-09-22\"","\"HEALTH\"","null"),key(),1));
        String previewId=p.get("previewId").asText();
        error(b.postMatch("/rest-days","{\"date\":\"2026-09-22\",\"reason\":\"WORK\",\"previewId\":\""+previewId+"\"}",key(),1),409,"RESCHEDULE_PREVIEW_MISMATCH");
        jdbc.execute("alter table daily_career.schedule_change add constraint fixture_reject_schedule_change check (reason_code <> 'REST_ADD')");String retry=key();
        try {error(b.postMatch("/rest-days","{\"date\":\"2026-09-22\",\"reason\":\"HEALTH\",\"previewId\":\""+previewId+"\"}",retry,1),500,"INTERNAL_SERVER_ERROR");}
        finally {jdbc.execute("alter table daily_career.schedule_change drop constraint fixture_reject_schedule_change");}
        assertThat(data(b.get("/learning-days/2026-09-22")).get("kind").asText()).isEqualTo("STUDY");
        assertThat(jdbc.queryForObject("select consumed_at is null from daily_career.schedule_preview where id=?",Boolean.class,Long.parseLong(previewId))).isTrue();
        ok(b.postMatch("/rest-days","{\"date\":\"2026-09-22\",\"reason\":\"HEALTH\",\"previewId\":\""+previewId+"\"}",retry,1),200);
    }

    @Test void concurrentConfirmationHasOneScheduleEffect() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        var p=data(b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("SHIFT_BACKLOG","2026-09-22","null","null","null"),key(),1));String body="{\"previewId\":\""+p.get("previewId").asText()+"\"}";
        var replies=concurrent(()->b.postMatch("/curriculums/"+course+"/reschedule",body,key(),1));
        assertThat(replies.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,412);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.schedule_change where user_curriculum_id=?",Integer.class,course)).isEqualTo(1);
        assertThat(data(b.get("/curriculums/"+course)).get("scheduleRevision").asInt()).isEqualTo(2);
    }

    @Test void inconsistentContentReadKeepsTheWeekAndEmitsOperationalEvent(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var b=new Browser();String course=data(b.create()).get("curriculumId").asText();String session=firstSession(b,"2026-09-21");
        jdbc.update("update daily_career.learning_day_item set content_status='PENDING' where id=?",Long.parseLong(session));
        var week=b.get("/curriculums/"+course+"/weeks/1");ok(week,200);contract("WeekDetail",data(week));
        assertThat(output).contains("LEARNING_CONTENT_MISMATCH curriculumId="+course+" sessionId="+session);
        error(b.post("/learning-sessions/"+session+"/start",null,null),409,"CONTENT_NOT_READY");
    }

    @Test void problemSubmissionMustBelongToCurrentUserAndCourseAndBodylessRoutesRejectBodies() throws Exception {
        var a=new Browser();var b=new Browser();long ca=data(a.create()).get("curriculumId").asLong();long cb=data(b.create()).get("curriculumId").asLong();
        submit(b.user,cb);String s=firstSession(a,"2026-09-21");
        error(a.post("/learning-sessions/"+s+"/start","{}",null),400,"INVALID_REQUEST");ok(a.post("/learning-sessions/"+s+"/start",null,null),200);
        String content=data(a.get("/learning-sessions/"+s+"/contents")).get(1).get("contentId").asText();
        error(a.post("/learning-contents/"+content+"/complete","{}",null),400,"INVALID_REQUEST");error(a.post("/learning-contents/"+content+"/complete",null,null),422,"CONTENT_PROBLEMS_INCOMPLETE");
        jdbc.update("insert into daily_career.problem_attempt(user_id,user_curriculum_id,problem_version_id) values (?,?,?)",a.user,ca,fixture.problemVersion);
        error(a.post("/learning-contents/"+content+"/complete",null,null),422,"CONTENT_PROBLEMS_INCOMPLETE");submit(a.user,ca);ok(a.post("/learning-contents/"+content+"/complete",null,null),200);
        assertThatThrownBy(()->jdbc.update("update daily_career.assigned_content set user_id=? where id=?",b.user,Long.parseLong(content))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void successfullyGradedSubmissionIsNotAnActiveExam() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        long attempt=jdbc.queryForObject("""
                insert into daily_career.test_attempt(user_id,user_curriculum_id,test_version_id,attempt_no,status,started_at,deadline_at,submitted_at,submit_reason,submission_digest)
                values (?,?,?,1,'SUBMITTED',clock_timestamp(),clock_timestamp()+interval '10 minutes',clock_timestamp(),'USER',repeat('a',64)) returning id
                """,Long.class,b.user,course,fixture.testVersion);
        long run=jdbc.queryForObject("""
                insert into daily_career.grading_run(user_id,test_attempt_id,run_no,status,grader_type,rule_version,input_digest)
                values (?,?,1,'QUEUED','RULE','fixture',repeat('a',64)) returning id
                """,Long.class,b.user,attempt);
        var waiting=data(b.get("/tests")).get("content");assertThat(waiting).hasSize(1);
        assertThat(waiting.get(0).get("activeAttemptId").asText()).isEqualTo(Long.toString(attempt));

        jdbc.update("update daily_career.grading_run set status='SUCCEEDED',is_current=true,finished_at=clock_timestamp() where id=?",run);
        var completed=data(b.get("/tests")).get("content");assertThat(completed).hasSize(1);
        assertThat(completed.get(0).get("activeAttemptId").isNull()).isTrue();
        assertThat(completed.get(0).get("attemptCount").asInt()).isEqualTo(1);
    }

    @Test void practiceSubmissionGradesImmutableVersionAndDrivesWrongAnswerReview() throws Exception {
        var b=new Browser();var other=new Browser();ok(b.create(),201);String session=firstSession(b,"2026-09-21");
        var contents=data(b.get("/learning-sessions/"+session+"/contents"));String content=contents.get(1).get("contentId").asText();
        String problem=contents.get(1).isNull()?null:data(b.get("/learning-contents/"+content)).get("problems").get(0).get("problemId").asText();
        var problemResponse=b.get("/problems/"+problem);ok(problemResponse,200);var problemView=data(problemResponse);assertThat(problemView.get("contentId").asText()).isEqualTo(content);
        assertThat(problemView.get("question").asText()).isEqualTo("fixture question");assertThat(problemView.get("options").size()).isEqualTo(2);
        assertThat(problemView.toString()).doesNotContain("secret explanation","correctAnswer");
        error(other.get("/problems/"+problem),404,"RESOURCE_NOT_FOUND");

        String wrongKey=key();String wrongBody="{\"problemId\":\""+problem+"\",\"answer\":\"2\",\"contentId\":\""+content+"\"}";
        var wrong=b.post("/problem-attempts",wrongBody,wrongKey);ok(wrong,201);var first=data(wrong);
        assertThat(first.get("correct").asBoolean()).isFalse();assertThat(first.get("correctAnswer").asText()).isEqualTo("1");
        assertThat(first.get("wrongAnswerCreated").asBoolean()).isTrue();String wrongId=first.get("wrongAnswerId").asText();
        assertThat(data(b.post("/problem-attempts",wrongBody,wrongKey))).isEqualTo(first);
        error(b.post("/problem-attempts",wrongBody.replace("\"answer\":\"2\"","\"answer\":\"1\""),wrongKey),409,"IDEMPOTENCY_KEY_REUSED");
        error(b.post("/problem-attempts",wrongBody.replace("\"answer\":\"2\"","\"answer\":\"9\""),key()),422,"PROBLEM_INVALID_ANSWER");
        error(other.get("/problem-attempts/"+first.get("attemptId").asText()),404,"RESOURCE_NOT_FOUND");

        String correctBody=wrongBody.replace("\"answer\":\"2\"","\"answer\":\"1\"");var correct=b.post("/problem-attempts",correctBody,key());ok(correct,201);var retry=data(correct);
        assertThat(retry.get("correct").asBoolean()).isTrue();assertThat(retry.get("attemptNo").asInt()).isEqualTo(2);
        assertThat(data(b.get("/problem-attempts/"+retry.get("attemptId").asText())).get("explanation").asText()).isEqualTo("secret explanation");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.grading_run where user_id=?",Integer.class,b.user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.wrong_answer_occurrence where user_id=?",Integer.class,b.user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.review_task where user_id=? and status='PLANNED'",Integer.class,b.user)).isEqualTo(1);

        var page=data(b.get("/wrong-answers"));assertThat(page.get("totalElements").asInt()).isEqualTo(1);assertThat(page.get("content").get(0).get("wrongAnswerId").asText()).isEqualTo(wrongId);
        var detailResponse=b.get("/wrong-answers/"+wrongId);var detail=data(detailResponse);assertThat(detail.get("status").asText()).isEqualTo("UNRESOLVED");
        String etag=detailResponse.headers().firstValue("etag").orElseThrow();var noted=b.send("PATCH","/wrong-answers/"+wrongId,"{\"note\":\"review this\"}",null,true,etag);ok(noted,200);
        assertThat(data(noted).get("note").asText()).isEqualTo("review this");
        error(b.send("PATCH","/wrong-answers/"+wrongId,"{\"note\":\"stale\"}",null,true,etag),412,"PRECONDITION_FAILED");

        String resolveKey=key();String resolve="{\"resolutionType\":\"CORRECT_RETRY\",\"attemptId\":\""+retry.get("attemptId").asText()+"\"}";
        var resolved=b.post("/wrong-answers/"+wrongId+"/resolve",resolve,resolveKey);ok(resolved,200);
        assertThat(data(resolved).get("status").asText()).isEqualTo("RESOLVED");assertThat(data(resolved).get("reviewCount").asInt()).isEqualTo(1);
        assertThat(data(b.post("/wrong-answers/"+wrongId+"/resolve",resolve,resolveKey))).isEqualTo(data(resolved));
        ok(b.post("/wrong-answers/"+wrongId+"/reopen",null,null),200);assertThat(data(b.get("/wrong-answers/"+wrongId)).get("status").asText()).isEqualTo("UNRESOLVED");

        String reportKey=key();var report=b.post("/problems/"+problem+"/reports","{\"reason\":\"UNCLEAR_QUESTION\",\"comment\":\"fixture report\"}",reportKey);ok(report,201);
        assertThat(data(report).get("status").asText()).isEqualTo("RECEIVED");assertThat(jdbc.queryForObject("select count(*) from daily_career.problem_report where user_id=?",Integer.class,b.user)).isEqualTo(1);
        var noContext=b.post("/problem-attempts","{\"problemId\":\""+problem+"\",\"answer\":\"1\",\"contentId\":null}",key());ok(noContext,201);
        assertThat(data(noContext).get("contentId").isNull()).isTrue();
    }

    @Test void failedPracticeWriteRollsBackAttemptGradeWrongAnswerAndIdempotency() throws Exception {
        var b=new Browser();ok(b.create(),201);String session=firstSession(b,"2026-09-21");var content=data(b.get("/learning-sessions/"+session+"/contents")).get(1);
        String contentId=content.get("contentId").asText(),problem=data(b.get("/learning-contents/"+contentId)).get("problems").get(0).get("problemId").asText(),requestKey=key();
        jdbc.execute("alter table daily_career.wrong_answer_occurrence add constraint fixture_reject_wrong check (false) not valid");
        try {error(b.post("/problem-attempts","{\"problemId\":\""+problem+"\",\"answer\":\"2\",\"contentId\":\""+contentId+"\"}",requestKey),500,"INTERNAL_SERVER_ERROR");}
        finally {jdbc.execute("alter table daily_career.wrong_answer_occurrence drop constraint fixture_reject_wrong");}
        for(String table:List.of("problem_attempt","grading_run","grading_result","wrong_answer","wrong_answer_occurrence","review_task"))
            assertThat(jdbc.queryForObject("select count(*) from daily_career."+table+" where user_id=?",Integer.class,b.user)).as(table).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.idempotency_record where user_id=? and operation_scope='ATTEMPT-001'",Integer.class,b.user)).isZero();
        ok(b.post("/problem-attempts","{\"problemId\":\""+problem+"\",\"answer\":\"2\",\"contentId\":\""+contentId+"\"}",requestKey),201);
    }

    @Test void restartStartsFreshAndReusesMatchingAiQuestionsWithoutAnswers() throws Exception {
        var b=new Browser();var created=b.create();ok(created,201);
        long oldCourse=data(created).get("curriculumId").asLong();
        String oldSession=firstSession(b,"2026-09-21");
        ok(b.post("/learning-sessions/"+oldSession+"/start",null,null),200);
        String oldContent=data(b.get("/learning-sessions/"+oldSession+"/contents")).get(1).get("contentId").asText();
        String oldProblem=data(b.get("/learning-contents/"+oldContent)).get("problems").get(0).get("problemId").asText();
        var oldWrong=data(b.post("/problem-attempts",JSON.createObjectNode().put("problemId",oldProblem).put("contentId",oldContent).put("answer","2").toString(),key()));
        assertThat(oldWrong.get("correct").asBoolean()).isFalse();
        String oldWrongId=oldWrong.get("wrongAnswerId").asText();
         int minutes=jdbc.queryForObject("""
                 select s.estimated_minutes from daily_career.assigned_content a
                 join daily_career.assigned_session_detail s on s.learning_day_item_id=a.learning_day_item_id
                 where a.id=?
                 """,Integer.class,Long.parseLong(oldContent));
         assertThat(data(b.get("/ai/practice-sets/by-content/"+oldContent)).get("generationAvailable").asBoolean()).isTrue();
        var plan=AiPracticePlan.forMinutes(minutes,4);
        var snapshot=JSON.createObjectNode().put("contentId",oldContent).put("sessionId",oldSession)
                .put("questionCount",plan.questionCount()).put("targetDifficulty",4).put("generationSequence",1);
        snapshot.set("difficultyLevels",JSON.valueToTree(plan.difficulties()));
        var result=JSON.createObjectNode().put("contentId",oldContent).put("sessionId",oldSession);
        var questions=result.putArray("questions");
        for(int i=0;i<plan.questionCount();i++) questions.addObject().put("prompt","Fixture question "+i)
                .put("answer",1).put("explanation","Fixture explanation").putArray("choices")
                .add("A").add("B").add("C").add("D");
        long budget=jdbc.queryForObject("""
                insert into daily_career.ai_budget(user_id,period_start,period_end,limit_amount)
                values (?,'2026-09-21','2026-09-22',0) returning id
                """,Long.class,b.user);
        long oldJob=jdbc.queryForObject("""
                insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,provider,
                  model,prompt_version,input_snapshot,result_payload)
                values (?,?,'PRACTICE_PROBLEMS',?,'SUCCEEDED','OPENAI','fixture','ai-practice-v2',?::jsonb,?::jsonb)
                returning id
                """,Long.class,b.user,budget,"fixture:"+key(),snapshot.toString(),result.toString());
        jdbc.update("insert into daily_career.ai_practice_attempt(user_id,ai_job_id,question_no,answer_no,correct) values (?,?,1,1,true)",b.user,oldJob);
        long oldTest=jdbc.queryForObject("""
                insert into daily_career.test_attempt(user_id,user_curriculum_id,test_version_id,attempt_no,started_at,deadline_at)
                values (?,?,?,1,now(),now()+interval '10 minutes') returning id
                """,Long.class,b.user,oldCourse,fixture.testVersion);
        int revision=data(b.get("/curriculums/current")).get("scheduleRevision").asInt();
        String restartKey=key();
        var restarted=b.postMatch("/curriculums/"+oldCourse+"/restart","{\"startDate\":\"2026-09-22\"}",restartKey,revision);
        ok(restarted,201);
        long newCourse=data(restarted).get("curriculumId").asLong();
        var replay=b.postMatch("/curriculums/"+oldCourse+"/restart","{\"startDate\":\"2026-09-22\"}",restartKey,revision);
        ok(replay,201);
        assertThat(data(replay).get("curriculumId").asLong()).isEqualTo(newCourse);
        assertThat(newCourse).isNotEqualTo(oldCourse);
        assertThat(data(restarted).get("startDate").asText()).isEqualTo("2026-09-22");
        assertThat(data(restarted).get("completedRequiredSessions").asInt()).isZero();
         assertThat(jdbc.queryForObject("select status from daily_career.user_curriculum where id=?",String.class,oldCourse)).isEqualTo("CANCELED");
         assertThat(data(b.get("/curriculums/"+oldCourse)).get("status").asText()).isEqualTo("CANCELED");
         var oldPractice=data(b.get("/ai/practice-sets/by-content/"+oldContent));
         assertThat(oldPractice.get("generationAvailable").asBoolean()).isFalse();
         assertThat(oldPractice.get("availableDifficulties")).isEmpty();
        assertThat(jdbc.queryForObject("select status from daily_career.test_attempt where id=?",String.class,oldTest)).isEqualTo("VOIDED");
        String newSession=firstSession(b,"2026-09-22");
        assertThat(data(b.get("/learning-sessions/"+newSession)).get("status").asText()).isEqualTo("NOT_STARTED");
        String newContent=data(b.get("/learning-sessions/"+newSession+"/contents")).get(1).get("contentId").asText();
        var latest=data(b.get("/ai/practice-sets/by-content/"+newContent));
        assertThat(latest.get("status").asText()).isEqualTo("SUCCESS");
        long newJob=latest.get("jobId").asLong();
        assertThat(newJob).isNotEqualTo(oldJob);
        assertThat(jdbc.queryForObject("select reused_from_job_id from daily_career.ai_job where id=?",Long.class,newJob)).isEqualTo(oldJob);
        assertThat(data(b.get("/ai/practice-sets/"+newJob)).get("questions").get(0).get("submittedAnswer").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.ai_practice_attempt where ai_job_id=?",Integer.class,newJob)).isZero();
        ok(b.post("/ai/practice-sets/"+oldJob+"/answers","{\"questionNo\":2,\"answerNo\":2}",null),200);
        assertThat(data(b.get("/ai/wrong-notes")).get(0).get("jobId").asText()).isEqualTo(Long.toString(oldJob));
        assertThat(data(b.get("/wrong-answers")).get("content").get(0).get("wrongAnswerId").asText()).isEqualTo(oldWrongId);
        var activity=data(b.get("/learning-progress"));
        assertThat(activity.get("aiPracticeAttemptCount").asInt()).isEqualTo(2);
        assertThat(activity.get("aiPracticeCorrectCount").asInt()).isEqualTo(1);
        assertThat(data(b.get("/ai/usage")).get("usedRequestCount").asInt()).isEqualTo(1);
    }

    @Test void switchingToReviewedCoursePreservesOldWorkAndStartsARealFirstDay() throws Exception {
        var b=new Browser();var created=b.create();ok(created,201);
        long oldCourse=data(created).get("curriculumId").asLong();
        String oldSession=firstSession(b,"2026-09-21");
        ok(b.post("/learning-sessions/"+oldSession+"/start",null,null),200);
        String oldContent=data(b.get("/learning-sessions/"+oldSession+"/contents")).get(1).get("contentId").asText();
        String oldProblem=data(b.get("/learning-contents/"+oldContent)).get("problems").get(0).get("problemId").asText();
        ok(b.post("/problem-attempts",JSON.createObjectNode().put("problemId",oldProblem)
                .put("contentId",oldContent).put("answer","2").toString(),key()),201);
        long testAttempt=jdbc.queryForObject("""
                insert into daily_career.test_attempt(user_id,user_curriculum_id,test_version_id,attempt_no,started_at,deadline_at)
                values (?,?,?,1,now(),now()+interval '10 minutes') returning id
                """,Long.class,b.user,oldCourse,fixture.testVersion);

        var manuscript=manuscript();var draft=imports.importDraft(manuscript.toString());
        long target=Long.parseLong(draft.templateId());
        imports.publishReviewed(target,draft.digest(),"Automated switch integration fixture");
        int revision=data(b.get("/curriculums/current")).get("scheduleRevision").asInt();
        String body="{\"templateId\":\""+target+"\",\"startDate\":\"2026-09-22\"}";
        error(b.postMatch("/curriculums/"+oldCourse+"/switch",body,key(),revision+1),412,"PRECONDITION_FAILED");
        assertThat(jdbc.queryForObject("select status from daily_career.user_curriculum where id=?",String.class,oldCourse)).isEqualTo("ACTIVE");
        error(b.postMatch("/curriculums/"+oldCourse+"/switch",
                "{\"templateId\":\""+fixture.template+"\",\"startDate\":\"2026-09-22\"}",key(),revision),400,"VALIDATION_FAILED");
        var other=new Browser();
        error(other.postMatch("/curriculums/"+oldCourse+"/switch",body,key(),revision),404,"RESOURCE_NOT_FOUND");

        String switchKey=key();
        var switched=b.postMatch("/curriculums/"+oldCourse+"/switch",body,switchKey,revision);ok(switched,201);
        long newCourse=data(switched).get("curriculumId").asLong();
        var replay=b.postMatch("/curriculums/"+oldCourse+"/switch",body,switchKey,revision);ok(replay,201);
        assertThat(data(replay).get("curriculumId").asLong()).isEqualTo(newCourse);
        assertThat(data(switched).get("templateId").asLong()).isEqualTo(target);
        assertThat(data(switched).get("totalLearningDays").asInt()).isEqualTo(20);
        assertThat(data(switched).get("completedRequiredSessions").asInt()).isZero();
        assertThat(jdbc.queryForObject("select status from daily_career.user_curriculum where id=?",String.class,oldCourse)).isEqualTo("CANCELED");
        assertThat(data(b.get("/curriculums/"+oldCourse)).get("status").asText()).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("select status from daily_career.test_attempt where id=?",String.class,testAttempt)).isEqualTo("VOIDED");
        assertThat(jdbc.queryForObject("select count(*) from daily_career.problem_attempt where user_curriculum_id=?",Integer.class,oldCourse)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from daily_career.problem_attempt where user_curriculum_id=?",Integer.class,newCourse)).isZero();
        String newSession=firstSession(b,"2026-09-22");
        String newContent=data(b.get("/learning-sessions/"+newSession+"/contents")).get(0).get("contentId").asText();
        var firstLesson=data(b.get("/learning-contents/"+newContent));
        assertThat(firstLesson.get("body").asText().length()).isGreaterThan(500);
        assertThat(firstLesson.get("problems").size()).isEqualTo(10);
    }

    @Test void publishedRealCourseHidesDevelopmentFixtureFromNewEnrollmentAndExtraLearning() throws Exception {
        var demo=new LearningFixture(jdbc,catalog,true,1,"BACKEND-FOUNDATIONS-6M");
        assertThat(catalog.templates()).noneMatch(t->t.templateId()==demo.template);
        assertThatThrownBy(()->catalog.plan(demo.template,true))
                .isInstanceOf(dev.dailycareer.common.api.ApiException.class)
                .hasMessageContaining("TEMPLATE_NOT_PUBLISHED");
        var learner=new Browser();ok(learner.create(),201);
        var topics=data(learner.get("/extra-learning/topics?mode=EXPLORE"));
        assertThat(topics.toString()).contains("\"contentVersionId\":\""+fixture.readVersion+"\"")
                .doesNotContain("\"contentVersionId\":\""+demo.readVersion+"\"")
                .doesNotContain("\"contentVersionId\":\""+demo.practiceVersion+"\"");
        var categories=new java.util.HashSet<String>();
        topics.forEach(topic->categories.add(topic.path("category").asText()));
        assertThat(categories).contains("SPRING","SQL","ALGORITHM","FINANCE","INTERVIEW").hasSize(6);
        for(var topic:topics) {
            if(!java.util.Set.of("SPRING","SQL","ALGORITHM","FINANCE","INTERVIEW")
                    .contains(topic.path("category").asText()))continue;
            String selected=topic.path("contentVersionId").asText();
            String start=JSON.createObjectNode().put("mode","EXPLORE")
                    .put("contentVersionId",selected).put("targetMinutes",60).toString();
            var created=learner.post("/extra-learning",start,key());ok(created,200);
            String session=data(created).path("sessionId").asText();
            String content=data(learner.get("/learning-sessions/"+session+"/contents")).get(0).path("contentId").asText();
            var lesson=data(learner.get("/learning-contents/"+content));
            assertThat(lesson.path("body").asText().codePointCount(0,lesson.path("body").asText().length())).isGreaterThan(800);
            assertThat(lesson.path("problems")).hasSize(3);
            assertThat(data(learner.get("/ai/practice-sets/by-content/"+content)).path("generationAvailable").asBoolean()).isTrue();
            for(var problem:lesson.path("problems")) {
                String attempt=JSON.createObjectNode().put("problemId",problem.path("problemId").asText())
                        .put("answer","1").put("contentId",content).toString();
                ok(learner.post("/problem-attempts",attempt,key()),201);
            }
            ok(learner.post("/learning-contents/"+content+"/complete",null,null),200);
            ok(learner.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":null}",key()),200);
        }
        String request=JSON.createObjectNode().put("mode","EXPLORE")
                .put("contentVersionId",Long.toString(demo.practiceVersion))
                .put("targetMinutes",30).toString();
        error(learner.post("/extra-learning",request,key()),404,"RESOURCE_NOT_FOUND");
    }

    private com.fasterxml.jackson.databind.node.ObjectNode manuscript() throws Exception {
        var source=java.nio.file.Path.of(System.getProperty("catalog.test-file","../content/java-four-week-v1/course.json"));
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(java.nio.file.Files.readString(source));
        node.put("code","TEST-4W-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        return node;
    }

    @Test void fourWeekPracticeCanBeConfirmedWithoutQuestionAttempts() throws Exception {
        var source=manuscript();source.put("code","JAVA-CORE-4W");
        var imported=imports.importDraft(source.toString());
        imports.publishReviewed(Long.parseLong(imported.templateId()),imported.digest(),
                "Automated integration fixture: hands-on lesson completion policy checked");
        var request=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(createBody("2026-09-21"));
        request.put("templateId",imported.templateId());
        var learner=new Browser();ok(learner.post("/curriculums",request.toString(),key()),201);
        String session=firstSession(learner,"2026-09-21");
        String content=data(learner.get("/learning-sessions/"+session+"/contents")).get(0).get("contentId").asText();
        assertThat(data(learner.get("/learning-contents/"+content)).get("completionRule").asText()).isEqualTo("READ_ACK");
        ok(learner.post("/learning-sessions/"+session+"/start",null,null),200);
        ok(learner.post("/learning-contents/"+content+"/complete",null,null),200);
        ok(learner.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":null}",key()),200);
        assertThat(data(learner.get("/learning-sessions/"+session)).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test void reviewedFourWeekCourseSupportsARealLearnerJourneyAndKeepsExistingRelease() throws Exception {
        var source=manuscript();int originalCourses=jdbc.queryForObject("select count(*) from daily_career.user_curriculum",Integer.class);
        var imported=imports.importDraft(source.toString());long template=Long.parseLong(imported.templateId());
        assertThat(imported.status()).isEqualTo("DRAFT");assertThat(imported.reused()).isFalse();
        assertThat(imports.importDraft(source.toPrettyString()).templateId()).isEqualTo(imported.templateId());
        assertThat(catalog.templates()).noneMatch(t->t.templateId()==template);
        assertThatThrownBy(()->catalog.publish(template)).isInstanceOf(dev.dailycareer.common.api.ApiException.class);
        assertThatThrownBy(()->imports.publishReviewed(template,"0".repeat(64),"Official references and executable examples checked")).isInstanceOf(IllegalArgumentException.class);
        var published=imports.publishReviewed(template,imported.digest(),"Automated integration fixture: manuscript and DB digest verified");
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(imports.publishReviewed(template,imported.digest(),"Idempotent replay of the same reviewed fixture").reused()).isTrue();
        assertThat(catalog.plan(template,true).durationMonths()).isEqualTo(1);
        assertThat(catalog.plan(fixture.template,true).durationMonths()).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.user_curriculum",Integer.class)).isEqualTo(originalCourses);
        assertThatThrownBy(()->jdbc.update("update daily_career.curriculum_release set title='changed' where curriculum_version_id=?",template)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var request=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(createBody("2026-09-21"));
        request.put("templateId",imported.templateId());
        var b=new Browser();var created=b.post("/curriculums",request.toString(),key());
        ok(created,201);var course=data(created);contract("Curriculum",course);
        assertThat(course.get("durationMonths").asInt()).isEqualTo(1);
        assertThat(course.get("nominalEndDate").asText()).isEqualTo("2026-10-20");
        assertThat(course.get("endDate").asText()).isEqualTo("2026-10-16");
        assertThat(course.get("totalLearningDays").asInt()).isEqualTo(20);
        assertThat(course.get("totalRequiredSessions").asInt()).isEqualTo(20);
        String session=firstSession(b,"2026-09-21");ok(b.post("/learning-sessions/"+session+"/start",null,null),200);
        String content=data(b.get("/learning-sessions/"+session+"/contents")).get(0).get("contentId").asText();
        var lesson=data(b.get("/learning-contents/"+content));assertThat(lesson.get("problems")).hasSize(10);
        assertThat(lesson.get("body").asText()).contains("직접 해보기","완료 기준","공식 참고 자료").doesNotContain("reviewerGuide");
        error(b.post("/learning-contents/"+content+"/complete",null,null),422,"CONTENT_PROBLEMS_INCOMPLETE");
        String wrongId=null,firstAttempt=null;int index=0;
        for(var problem:lesson.get("problems")) {
            String id=problem.get("problemId").asText();int correct=source.get("days").get(0).get("questions").get(index).get("answer").asInt();
            String body=JSON.createObjectNode().put("problemId",id).put("contentId",content).put("answer",Integer.toString(index==0?correct%3+1:correct)).toString();
            var response=b.post("/problem-attempts",body,key());ok(response,201);
            if(index++==0){wrongId=data(response).get("wrongAnswerId").asText();
                var retry=b.post("/problem-attempts",JSON.createObjectNode().put("problemId",id).put("contentId",content).put("answer",Integer.toString(correct)).toString(),key());ok(retry,201);firstAttempt=data(retry).get("attemptId").asText();}
        }
        ok(b.post("/wrong-answers/"+wrongId+"/resolve",JSON.createObjectNode().put("resolutionType","CORRECT_RETRY").put("attemptId",firstAttempt).toString(),key()),200);
        ok(b.post("/learning-contents/"+content+"/complete",null,null),200);
        ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":90}",key()),200);
        var recent=data(b.get("/dashboard")).get("recentSessions");
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).get("sessionId").asText()).isEqualTo(session);
        assertThat(recent.get(0).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(recent.get(0).get("scheduledDate").asText()).isEqualTo("2026-09-21");
        assertThat(data(b.get("/curriculums/current")).get("completedRequiredSessions").asInt()).isEqualTo(1);
        var progress=data(b.get("/learning-progress"));
        assertThat(progress.get("totalRequiredSessions").asInt()).isEqualTo(20);
        assertThat(progress.get("completedRequiredSessions").asInt()).isEqualTo(1);
        assertThat(progress.get("completionRate").asDouble()).isEqualTo(5.0);
        assertThat(progress.get("totalStudyMinutes").asInt()).isEqualTo(90);
        assertThat(progress.get("streakDays").asInt()).isEqualTo(1);
        var daily=data(b.get("/learning-progress/daily?date=2026-09-21"));
        assertThat(daily.get("totalSessionCount").asInt()).isEqualTo(1);
        assertThat(daily.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(daily.get("actualMinutes").asInt()).isEqualTo(90);
        assertThat(data(b.get("/learning-progress/categories")).get(0).get("totalSessionCount").asInt()).isEqualTo(20);
        var exams=data(b.get("/tests?size=50"));assertThat(exams.get("totalElements").asInt()).isEqualTo(26);
        String exam=null;for(var e:exams.get("content"))if(e.get("type").asText().equals("DAILY") && e.get("scheduledDate").asText().equals("2026-09-21"))exam=e.get("testId").asText();
        assertThat(exam).isNotNull();var started=b.post("/tests/"+exam+"/attempts",null,key());ok(started,201);
        String attempt=data(started).get("attemptId").asText();var attemptView=data(b.get("/test-attempts/"+attempt));assertThat(attemptView.get("problems")).hasSize(2);
        int q=10;for(var problem:attemptView.get("problems")) {
            var answer=b.send("PUT","/test-attempts/"+attempt+"/answers/"+problem.get("problemId").asText(),
                    JSON.createObjectNode().put("answer",Integer.toString(source.get("days").get(0).get("questions").get(q++).get("answer").asInt())).toString(),null,true,"\""+problem.get("answerRevision").asInt()+"\"");ok(answer,200);
        }
        var submitted=b.post("/test-attempts/"+attempt+"/submit",null,key());assertThat(submitted.statusCode()).isIn(200,202);
        assertThat(data(submitted).get("gradingStatus").asText()).isEqualTo("WAITING");
        // Poll the real grading worker with a bounded deadline, without assuming immediate completion.
        HttpResponse<String> result=null;for(int i=0;i<50;i++){result=b.get("/test-attempts/"+attempt+"/result");if(result.statusCode()==200)break;Thread.sleep(100);}
        ok(result,200);assertThat(data(result).get("score").asInt()).isEqualTo(100);
        assertThat(data(b.get("/learning-sessions/"+session)).get("status").asText()).isEqualTo("COMPLETED");
        // Advance the test clock through the remaining assigned days, using the same public APIs.
        Map<String,Integer> answers=new HashMap<>();
        for(var day:source.get("days"))for(var question:day.get("questions"))answers.put(question.get("id").asText(),question.get("answer").asInt());
        LocalDate assigned=LocalDate.of(2026,9,21);
        for(int dayIndex=1;dayIndex<20;dayIndex++){
            do{assigned=assigned.plusDays(1);}while(Set.of(DayOfWeek.SATURDAY,DayOfWeek.SUNDAY).contains(assigned.getDayOfWeek()));
            NOW.set(assigned.atTime(9,0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
            String nextSession=firstSession(b,assigned.toString());
            ok(b.post("/learning-sessions/"+nextSession+"/start",null,null),200);
            String nextContent=data(b.get("/learning-sessions/"+nextSession+"/contents")).get(0).get("contentId").asText();
            var nextLesson=data(b.get("/learning-contents/"+nextContent));int questionIndex=0;
            for(var problem:nextLesson.get("problems")){
                int correct=source.get("days").get(dayIndex).get("questions").get(questionIndex++).get("answer").asInt();
                ok(b.post("/problem-attempts",JSON.createObjectNode().put("problemId",problem.get("problemId").asText()).put("contentId",nextContent).put("answer",Integer.toString(correct)).toString(),key()),201);
            }
            ok(b.post("/learning-contents/"+nextContent+"/complete",null,null),200);
            ok(b.post("/learning-sessions/"+nextSession+"/complete","{\"actualMinutes\":90}",key()),200);
            for(var planned:data(b.get("/tests?size=50")).get("content")){
                if(!planned.get("scheduledDate").asText().equals(assigned.toString()))continue;
                JsonNode definition=null;for(var candidate:source.get("assessments"))if(candidate.get("title").asText().equals(planned.get("title").asText()))definition=candidate;
                assertThat(definition).isNotNull();
                var examStart=b.post("/tests/"+planned.get("testId").asText()+"/attempts",null,key());ok(examStart,201);
                String examAttempt=data(examStart).get("attemptId").asText();int examIndex=0;
                for(var question:data(examStart).get("problems")){
                    String correct=Integer.toString(answers.get(definition.get("questions").get(examIndex++).asText()));
                    ok(b.send("PUT","/test-attempts/"+examAttempt+"/answers/"+question.get("problemId").asText(),JSON.createObjectNode().put("answer",correct).toString(),null,true,"\""+question.get("answerRevision").asInt()+"\""),200);
                }
                assertThat(b.post("/test-attempts/"+examAttempt+"/submit",null,key()).statusCode()).isIn(200,202);
                HttpResponse<String> graded=null;for(int poll=0;poll<50;poll++){graded=b.get("/test-attempts/"+examAttempt+"/result");if(graded.statusCode()==200)break;Thread.sleep(100);}
                ok(graded,200);assertThat(data(graded).get("score").asInt()).isEqualTo(100);
            }
        }
        var finished=data(b.get("/curriculums/current"));
        assertThat(finished.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(finished.get("completedRequiredSessions").asInt()).isEqualTo(20);
        var finishedProgress=data(b.get("/learning-progress"));
        assertThat(finishedProgress.get("completionRate").asDouble()).isEqualTo(100.0);
        assertThat(finishedProgress.get("totalStudyMinutes").asInt()).isEqualTo(1800);
        assertThat(finishedProgress.get("streakDays").asInt()).isEqualTo(20);
        assertThat(data(b.get("/tests?status=COMPLETED&size=50")).get("totalElements").asInt()).isEqualTo(26);
    }

    @Test void manuscriptRevisionRequiresANewReleaseAndRejectsTampering() throws Exception {
        var source=manuscript();var first=imports.importDraft(source.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)source.get("days").get(0)).put("labTask","수정된 실습 과제를 검증하고 결과를 설명하세요.");
        assertThatThrownBy(()->imports.importDraft(source.toString())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("new version");
        source.put("version","2026.09.2");var second=imports.importDraft(source.toString());assertThat(second.templateId()).isNotEqualTo(first.templateId());
        long version=jdbc.queryForObject("select (imported_ids->'problems'->>'d01-q01')::bigint from daily_career.catalog_import where curriculum_version_id=?",Long.class,Long.parseLong(second.templateId()));
        jdbc.update("update daily_career.problem_version set explanation_markdown='tampered explanation' where id=?",version);
        assertThatThrownBy(()->imports.publishReviewed(Long.parseLong(second.templateId()),second.digest(),"Review cannot authorize changed database payload")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DB content changed");
        assertThat(jdbc.queryForObject("select status from daily_career.curriculum_version where id=?",String.class,Long.parseLong(second.templateId()))).isEqualTo("DRAFT");
    }

    @Test void malformedManuscriptsAndLateDatabaseFailureLeaveNoPartialImport() throws Exception {
        var source=manuscript();int before=jdbc.queryForObject("select count(*) from daily_career.problem",Integer.class);
        var broken=source.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)broken.get("days").get(0).get("questions").get(0)).put("answer",9);
        assertThatThrownBy(()->imports.importDraft(broken.toString())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("answer");
        var incomplete=source.deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)incomplete.get("days")).remove(19);
        assertThatThrownBy(()->imports.importDraft(incomplete.toString())).isInstanceOf(IllegalArgumentException.class);
        var missing=source.deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)missing.get("assessments").get(0).get("questions")).set(0,JSON.getNodeFactory().textNode("missing-question"));
        assertThatThrownBy(()->imports.importDraft(missing.toString())).isInstanceOf(IllegalArgumentException.class);
        jdbc.execute("alter table daily_career.catalog_import add constraint fixture_reject_catalog check(false) not valid");
        try {assertThatThrownBy(()->imports.importDraft(source.toString())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
        finally {jdbc.execute("alter table daily_career.catalog_import drop constraint fixture_reject_catalog");}
        assertThat(jdbc.queryForObject("select count(*) from daily_career.problem",Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from daily_career.curriculum where code=?",Integer.class,source.get("code").asText())).isZero();
        assertThat(imports.importDraft(source.toString()).status()).isEqualTo("DRAFT");
    }

    @Test void extraLearningCompletesWithoutRecordingEstimatedDurationAsActualTime() throws Exception {
        var b=new Browser();ok(b.create(),201);
        String body=JSON.createObjectNode().put("mode","EXPLORE").put("contentVersionId",Long.toString(fixture.readVersion))
                .putNull("sourceSessionId").put("targetMinutes",30).toString();
        String session=data(b.post("/extra-learning",body,key())).get("sessionId").asText();
        String content=data(b.get("/learning-sessions/"+session+"/contents")).get(0).get("contentId").asText();
        ok(b.post("/learning-contents/"+content+"/complete",null,null),200);
        String completionKey=key();
        ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":null}",completionKey),200);
        ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":null}",completionKey),200);
        var completed=data(b.get("/learning-sessions/"+session));
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("estimatedMinutes").asInt()).isEqualTo(30);
        assertThat(completed.get("actualMinutes").asInt()).isZero();
        assertThat(completed.get("timeSource").asText()).isEqualTo("NOT_RECORDED");
        var progress=data(b.get("/learning-progress"));
        assertThat(progress.get("totalStudyMinutes").asInt()).isZero();
        assertThat(progress.get("completedEstimatedMinutes").asInt()).isEqualTo(30);
        assertThat(progress.get("extraLearningCount").asInt()).isEqualTo(1);
        assertThat(progress.get("completedExtraLearningCount").asInt()).isEqualTo(1);
    }

    @Test void extraLearningKeepsRequiredProgressAndHasIndependentAttemptsAndActivityDates() throws Exception {
        var b=new Browser();var other=new Browser();ok(b.create(),201);
        long course=data(b.get("/curriculums/current")).get("curriculumId").asLong();
        submit(b.user,course); // A previous regular attempt must not complete a new practice session.
        finish(b,firstSession(b,"2026-09-21"));
        String body=JSON.createObjectNode().put("mode","EXPLORE").put("contentVersionId",Long.toString(fixture.practiceVersion)).putNull("sourceSessionId").put("targetMinutes",15).toString();
        String startKey=key();var first=b.post("/extra-learning",body,startKey);ok(first,200);
        String session=data(first).get("sessionId").asText();
        assertThat(data(b.post("/extra-learning",body,startKey)).get("sessionId").asText()).isEqualTo(session);
        var duplicate=concurrent(()->b.post("/extra-learning",body,key()));
        for(var response:duplicate){ok(response,200);assertThat(data(response).get("sessionId").asText()).isEqualTo(session);}
        assertThat(data(b.get("/learning-days/today")).get("totalSessionCount").asInt()).isEqualTo(1);
        assertThat(data(b.get("/curriculums/current")).get("completedRequiredSessions").asInt()).isEqualTo(1);
        assertThat(data(b.get("/learning-days/today")).get("status").asText()).isEqualTo("COMPLETED");
        String content=data(b.get("/learning-sessions/"+session+"/contents")).get(0).get("contentId").asText();
        String problem=data(b.get("/learning-contents/"+content)).get("problems").get(0).get("problemId").asText();
        assertThat(data(b.get("/problems/"+problem+"?contentId="+content)).get("answered").asBoolean()).isFalse();
        error(b.post("/learning-contents/"+content+"/complete",null,null),422,"CONTENT_PROBLEMS_INCOMPLETE");
        error(other.get("/extra-learning/"+session),404,"RESOURCE_NOT_FOUND");
        error(other.get("/problems/"+problem+"?contentId="+content),404,"RESOURCE_NOT_FOUND");
        String timeKey=key();ok(b.post("/extra-learning/"+session+"/time","{\"actualMinutes\":11}",timeKey),200);
        ok(b.post("/extra-learning/"+session+"/time","{\"actualMinutes\":11}",timeKey),200);
        assertThat(data(b.get("/learning-progress")).get("totalStudyMinutes").asInt()).isEqualTo(41);
        NOW.set(Instant.parse("2026-09-21T15:00:00Z"));
        ok(b.post("/problem-attempts",JSON.createObjectNode().put("problemId",problem).put("contentId",content).put("answer","1").toString(),key()),201);
        ok(b.post("/learning-contents/"+content+"/complete",null,null),200);
        String completeKey=key();ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":7}",completeKey),200);
        ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":7}",completeKey),200);
        assertThat(data(b.get("/extra-learning/"+session)).get("recordedMinutes").asInt()).isEqualTo(18);
        assertThat(data(b.get("/extra-learning/"+session)).get("todayMinutes").asInt()).isEqualTo(7);
        var progress=data(b.get("/learning-progress"));assertThat(progress.get("totalStudyMinutes").asInt()).isEqualTo(48);
        assertThat(progress.get("extraPracticeAttemptCount").asInt()).isEqualTo(1);
        assertThat(progress.get("completedRequiredSessions").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select minutes from daily_career.extra_learning_time where session_id=? and activity_date='2026-09-21'",Integer.class,Long.parseLong(session))).isEqualTo(11);
    }

    @Test void aheadLearningKeepsOriginalAssignmentAndCountsTimeOnlyOnActualDay() throws Exception {
        var b=new Browser();ok(b.create(),201);long course=data(b.get("/curriculums/current")).get("curriculumId").asLong();
        var topic=data(b.get("/extra-learning/topics?mode=AHEAD")).get(0);String session=topic.get("sourceSessionId").asText();
        error(b.post("/learning-sessions/"+session+"/start",null,null),409,"LEARNING_NOT_AVAILABLE");
        String body=JSON.createObjectNode().put("mode","AHEAD").putNull("contentVersionId").put("sourceSessionId",session).put("targetMinutes",30).toString();
        ok(b.post("/extra-learning",body,key()),200);submit(b.user,course);
        for(var c:data(b.get("/learning-sessions/"+session+"/contents")))ok(b.post("/learning-contents/"+c.get("contentId").asText()+"/complete",null,null),200);
        ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":12}",key()),200);
        var original=data(b.get("/learning-sessions/"+session));assertThat(original.get("scheduledDate").asText()).isEqualTo("2026-09-22");
        assertThat(data(b.get("/curriculums/current")).get("completedRequiredSessions").asInt()).isEqualTo(1);
        assertThat(data(b.get("/learning-progress")).get("totalStudyMinutes").asInt()).isEqualTo(12);
        NOW.set(Instant.parse("2026-09-21T15:00:00Z"));
        assertThat(data(b.get("/learning-days/today")).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(data(b.get("/learning-days/today")).get("actualMinutes").asInt()).isZero();
        assertThat(data(b.get("/learning-sessions/"+session)).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(data(b.get("/extra-learning/"+session)).get("todayMinutes").asInt()).isZero();
        assertThat(data(b.get("/learning-progress")).get("totalStudyMinutes").asInt()).isEqualTo(12);
    }

    @Test void extraLearningIsAvailableOnRestAndUnassignedDaysWithoutChangingThePlan() throws Exception {
        var b=new Browser();ok(b.create(),201);
        int total=data(b.get("/curriculums/current")).get("totalRequiredSessions").asInt();
        String body=JSON.createObjectNode().put("mode","EXPLORE").put("contentVersionId",Long.toString(fixture.readVersion)).putNull("sourceSessionId").put("targetMinutes",60).toString();
        NOW.set(Instant.parse("2026-09-25T15:00:00Z"));
        assertThat(data(b.get("/learning-days/today")).get("kind").asText()).isEqualTo("REST");
        var created=b.post("/extra-learning",body,key());ok(created,200);String session=data(created).get("sessionId").asText();
        ok(b.post("/extra-learning/"+session+"/time","{\"actualMinutes\":30}",key()),200);
        for(var c:data(b.get("/learning-sessions/"+session+"/contents")))ok(b.post("/learning-contents/"+c.get("contentId").asText()+"/complete",null,null),200);
        ok(b.post("/learning-sessions/"+session+"/complete","{\"actualMinutes\":0}",key()),200);
        assertThat(data(b.get("/learning-days/today")).get("kind").asText()).isEqualTo("REST");
        NOW.set(Instant.parse("2026-10-10T15:00:00Z"));
        var after=b.post("/extra-learning",body,key());ok(after,200);
        assertThat(data(b.get("/learning-days/today")).get("kind").asText()).isEqualTo("NONE");
        assertThat(data(b.get("/curriculums/current")).get("totalRequiredSessions").asInt()).isEqualTo(total);
        assertThat(data(b.get("/learning-progress")).get("totalLearningDays").asInt()).isEqualTo(6);
        assertThat(data(b.get("/curriculums/current")).get("completedRequiredSessions").asInt()).isZero();
    }

    @Test void extraLearningOnRestDaySurvivesPolicyRefreshAndRestRemoval() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        NOW.set(Instant.parse("2026-09-25T15:00:00Z"));
        String body=JSON.createObjectNode().put("mode","EXPLORE").put("contentVersionId",Long.toString(fixture.readVersion)).put("targetMinutes",15).toString();
        String session=data(b.post("/extra-learning",body,key())).get("sessionId").asText();
        ok(b.post("/extra-learning/"+session+"/time","{\"actualMinutes\":5}",key()),200);
        int revision=data(b.get("/curriculums/current")).get("scheduleRevision").asInt();
        String policy="{\"dailyStudyMinutes\":90,\"restDaysPerWeek\":2,\"restWeekdays\":[\"SATURDAY\",\"SUNDAY\"]}";
        var prepared=b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("POLICY_APPLY","2026-09-26","null","null",policy),key(),revision);ok(prepared,201);var p=data(prepared);
        ok(b.postMatch("/curriculums/"+course+"/reschedule","{\"previewId\":\""+p.get("previewId").asText()+"\"}",key(),revision),200);
        revision++;
        assertThat(data(b.get("/learning-days/today")).get("kind").asText()).isEqualTo("REST");
        var removal=b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("REST_REMOVE","2026-09-26","\"2026-09-26\"","null","null"),key(),revision);ok(removal,201);var remove=data(removal);
        ok(b.deleteMatch("/rest-days/2026-09-26?previewId="+remove.get("previewId").asText(),key(),revision),200);
        assertThat(data(b.get("/learning-days/today")).get("kind").asText()).isEqualTo("STUDY");
        assertThat(data(b.get("/extra-learning/"+session)).get("recordedMinutes").asInt()).isEqualTo(5);
        assertThat(data(b.get("/learning-progress")).get("totalStudyMinutes").asInt()).isEqualTo(5);
        assertThat(data(b.get("/learning-days/today")).get("totalSessionCount").asInt()).isEqualTo(1);
    }

    @Test void optionalPracticeDoesNotLockAnUnstartedRegularDayAgainstRescheduling() throws Exception {
        var b=new Browser();long course=data(b.create()).get("curriculumId").asLong();
        String body=JSON.createObjectNode().put("mode","DEEPEN").put("contentVersionId",Long.toString(fixture.readVersion)).put("targetMinutes",15).toString();
        var started=b.post("/extra-learning",body,key());ok(started,200);String session=data(started).get("sessionId").asText();
        ok(b.post("/extra-learning/"+session+"/time","{\"actualMinutes\":5}",key()),200);
        int revision=data(b.get("/curriculums/current")).get("scheduleRevision").asInt();
        var p=b.postMatch("/curriculums/"+course+"/reschedule-preview",preview("REST_ADD","2026-09-21","\"2026-09-21\"","\"PERSONAL\"","null"),key(),revision);ok(p,201);
        ok(b.postMatch("/rest-days","{\"date\":\"2026-09-21\",\"reason\":\"PERSONAL\",\"previewId\":\""+data(p).get("previewId").asText()+"\"}",key(),revision),200);
        assertThat(data(b.get("/learning-days/today")).get("kind").asText()).isEqualTo("REST");
        assertThat(data(b.get("/extra-learning/"+session)).get("todayMinutes").asInt()).isEqualTo(5);
        assertThat(data(b.get("/curriculums/current")).get("completedRequiredSessions").asInt()).isZero();
    }

    private void finish(Browser b,String s) throws Exception {
        ok(b.post("/learning-sessions/"+s+"/start",null,null),200);for(var c:data(b.get("/learning-sessions/"+s+"/contents")))ok(b.post("/learning-contents/"+c.get("contentId").asText()+"/complete",null,null),200);
        ok(b.post("/learning-sessions/"+s+"/complete","{\"actualMinutes\":30}",key()),200);
    }
    private String firstSession(Browser b,String date)throws Exception{return data(b.get("/learning-days/"+date+"/sessions")).get(0).get("sessionId").asText();}
    private int eventCount(String type,long id){return jdbc.queryForObject("select count(*) from daily_career.outbox_event where event_key=?",Integer.class,type+":"+id);}
    private void submit(long user,long course){jdbc.update("insert into daily_career.problem_attempt(user_id,user_curriculum_id,problem_version_id,status,submitted_at) values (?,?,?,'SUBMITTED',now())",user,course,fixture.problemVersion);}
    private String createBody(String date){return "{\"templateId\":\""+fixture.template+"\",\"startDate\":\""+date+"\",\"studyPolicy\":{\"dailyStudyMinutes\":120,\"restDaysPerWeek\":2,\"restWeekdays\":[\"SATURDAY\",\"SUNDAY\"]}}";}
    private static String preview(String action,String base,String date,String reason,String policy){return "{\"strategy\":\"SHIFT\",\"action\":\""+action+"\",\"baseDate\":\""+base+"\",\"date\":"+date+",\"reason\":"+reason+",\"studyPolicy\":"+policy+"}";}
    private String base(){return "http://localhost:"+port+"/api/v1";}
    private static String key(){return UUID.randomUUID().toString();}
    private static JsonNode data(HttpResponse<String> r)throws Exception{return JSON.readTree(r.body()).get("data");}
    private static void ok(HttpResponse<String> r,int status)throws Exception {assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(JSON.readTree(r.body()).get("success").asBoolean()).isTrue();assertThat(r.headers().firstValue("cache-control")).contains("no-store");}
    private static void error(HttpResponse<String> r,int status,String code)throws Exception {assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(JSON.readTree(r.body()).path("error").path("code").asText()).isEqualTo(code);}
    private static List<HttpResponse<String>> concurrent(Callable<HttpResponse<String>> call)throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);Callable<HttpResponse<String>> task=()->{gate.await();return call.call();};var a=pool.submit(task);var b=pool.submit(task);gate.countDown();return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}
    }
    class Browser {
        final long user;final String cookie;final String csrf;final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        Browser()throws Exception {
            user=jdbc.queryForObject("insert into daily_career.app_user(email,display_name) values (?,'Fixture') returning id",Long.class,UUID.randomUUID()+"@example.test");
            cookie="CAREER_SESSION="+Base64.getEncoder().encodeToString(authenticatedSession(repository,user).getBytes(StandardCharsets.UTF_8));
            csrf=JSON.readTree(get("/auth/csrf").body()).get("data").get("token").asText();
        }
        HttpResponse<String> get(String path)throws Exception{return send("GET",path,null,null,false);}
        HttpResponse<String> create()throws Exception{return post("/curriculums",createBody("2026-09-21"),key());}
        HttpResponse<String> post(String path,String body,String key)throws Exception{return send("POST",path,body,key,true);}
        HttpResponse<String> postMatch(String path,String body,String key,int revision)throws Exception{return send("POST",path,body,key,true,"\""+revision+"\"");}
        HttpResponse<String> deleteMatch(String path,String key,int revision)throws Exception{return send("DELETE",path,null,key,true,"\""+revision+"\"");}
        HttpResponse<String> send(String method,String path,String body,String key,boolean withCsrf)throws Exception {
            return send(method,path,body,key,withCsrf,null);
        }
        HttpResponse<String> send(String method,String path,String body,String key,boolean withCsrf,String etag)throws Exception {
            var r=HttpRequest.newBuilder(URI.create(base()+path)).timeout(Duration.ofSeconds(30)).header("Cookie",cookie);
            if(body!=null)r.header("Content-Type","application/json");if(key!=null)r.header("Idempotency-Key",key);if(withCsrf)r.header("X-CSRF-TOKEN",csrf);if(etag!=null)r.header("If-Match",etag);
            return client.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private static <S extends Session> String authenticatedSession(SessionRepository<S> repository,long user) {
        S session=repository.createSession();var authentication=UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(user),null,List.of(new SimpleGrantedAuthority("ROLE_USER")));
        session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(authentication));repository.save(session);return session.getId();
    }
}
