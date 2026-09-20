package dev.dailycareer.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.DailyCareerApplication;
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
    @LocalServerPort int port;
    static final ObjectMapper JSON=new ObjectMapper();
    static LearningFixture fixture;
    @BeforeEach void seed(){if(fixture==null)fixture=new LearningFixture(jdbc,catalog,true);}
    @BeforeEach void time(){NOW.set(Instant.parse("2026-09-20T15:00:00Z"));}

    @Test void emptyStateAuthenticationAndOwnershipBoundaries() throws Exception {
        var a=new Browser();var b=new Browser();
        var current=a.get("/curriculums/current");ok(current,200);assertThat(data(current).isNull()).isTrue();assertThat(current.headers().firstValue("etag")).isEmpty();
        assertThat(data(a.get("/learning-days/today")).isNull()).isTrue();
        assertThat(data(a.get("/learning-days?from=2026-09-21&to=2026-09-22")).isEmpty()).isTrue();
        assertThat(data(a.get("/learning-days/2026-09-21/sessions")).isEmpty()).isTrue();
        error(a.get("/learning-days/2026-09-21?curriculumId=9223372036854775807"),404,"RESOURCE_NOT_FOUND");
        var created=a.create();ok(created,201);String course=data(created).get("curriculumId").asText();
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
