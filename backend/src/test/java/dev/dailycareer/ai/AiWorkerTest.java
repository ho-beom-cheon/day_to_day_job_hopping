package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiWorkerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AiProvider provider = mock(AiProvider.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    @Test
    void shortLessonNeverCallsPaidProvider() {
        givenClaim(snapshot("짧은 개발용 본문"));

        new AiWorker(jdbc, provider, json, manager, mock(AiLessonCatalog.class)).runSafely(42);

        verifyNoInteractions(provider);
        verify(jdbc).update(contains("AI_PRACTICE_SOURCE_TOO_SHORT"), eq(42L), anyString());
    }

    @Test
    void paidQualityFailureKeepsCandidateAndRecordsTokenUsage() throws Exception {
        givenClaim(snapshot("충분한 학습 본문입니다. ".repeat(50)));
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of());
        ObjectNode generated = json.createObjectNode();
        generated.putArray("questions").addObject().put("prompt", "짧은 문항");
        when(provider.complete(eq(7L), isNull(), eq("test"), eq("PRACTICE_PROBLEMS"), any()))
                .thenReturn(new AiProvider.Completion(generated, "provider-request", 100, 200));

        new AiWorker(jdbc, provider, json, manager, mock(AiLessonCatalog.class)).runSafely(42);

        verify(jdbc).update(contains("result_payload=?::jsonb"), eq(generated.toString()), eq(42L), anyString());
        verify(jdbc).update(contains("insert into daily_career.ai_usage"),
                eq(7L), eq(42L), eq(1), eq("OPENAI"), eq("provider-request"), eq(100L), eq(200L));
    }

    private ObjectNode snapshot(String body) {
        ObjectNode input = json.createObjectNode().put("contentId", "99").put("contentVersionId", "88")
                .put("sessionId", "77").put("questionCount", 5).put("estimatedMinutes", 45)
                .put("targetDifficulty", 4).put("body", body);
        input.set("difficultyLevels", json.valueToTree(List.of(3, 3, 4, 4, 5)));
        return input;
    }

    @Test void dailyFeedbackSendsOnlySelectedTopicsAndRestoresIdentityLocally() throws Exception {
        ObjectNode stored=json.createObjectNode().put("curriculumId","901").put("date","2026-09-27");
        stored.putArray("learningRecords").addObject().put("title","SQL 조인").put("status","COMPLETED").put("userId","private");
        when(jdbc.queryForList(contains("returning *"), any(Object[].class))).thenReturn(List.of(Map.of(
                "id",42L,"user_id",7L,"model","test","job_type","DAILY_FEEDBACK",
                "input_snapshot",stored.toString(),"attempt_count",1,"provider","OPENAI")));
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var generated=json.readTree("{\"summary\":\"SQL 조인 결과를 확인해 보세요.\",\"strengths\":[],\"weaknesses\":[],\"nextStudyFocus\":[\"중복 행 확인\"]}");
        when(provider.complete(eq(7L),isNull(),eq("test"),eq("DAILY_FEEDBACK"),any()))
                .thenReturn(new AiProvider.Completion(generated,"request",10,20));
        new AiWorker(jdbc,provider,json,manager,mock(AiLessonCatalog.class)).runSafely(42);
        verify(provider).complete(eq(7L),isNull(),eq("test"),eq("DAILY_FEEDBACK"),argThat(input ->
                input.has("studyFocus") && !input.has("learningRecords") && !input.has("curriculumId") && !input.has("date")));
        verify(jdbc).update(contains("set status='SUCCEEDED'"),argThat((String payload)->payload.contains("901") && payload.contains("2026-09-27")),eq(42L),anyString());
    }

    private void givenClaim(ObjectNode input) {
        when(jdbc.queryForList(contains("returning *"), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 42L, "user_id", 7L, "model", "test", "job_type", "PRACTICE_PROBLEMS",
                "input_snapshot", input.toString(), "attempt_count", 1, "provider", "OPENAI")));
    }

    @Test void lessonValidationFailureRecordsUsageWithoutPublishing() throws Exception {
        var lessons=mock(AiLessonCatalog.class);
        var input=AiLessonContentTest.request().put("curriculumId","private");
        when(jdbc.queryForList(contains("returning *"),any(Object[].class))).thenReturn(List.of(Map.of(
                "id",42L,"user_id",7L,"model","test","job_type","LEARNING_CONTENT","input_snapshot",input.toString(),"attempt_count",1,"provider","OPENAI")));
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(provider.complete(eq(7L),isNull(),eq("test"),eq("LEARNING_CONTENT"),any())).thenReturn(
                new AiProvider.Completion(AiLessonContentTest.candidate().put("status","UNSUPPORTED"),"lesson-req",101,202));
        new AiWorker(jdbc,provider,json,manager,lessons).runSafely(42);
        verifyNoInteractions(lessons);
        verify(provider).complete(eq(7L),isNull(),eq("test"),eq("LEARNING_CONTENT"),argThat(n->!n.has("curriculumId")));
        verify(jdbc).update(contains("AI_LEARNING_QUALITY_REJECTED"),anyString(),eq(42L),anyString());
        verify(jdbc).update(contains("insert into daily_career.ai_usage"),eq(7L),eq(42L),eq(1),eq("OPENAI"),eq("lesson-req"),eq(101L),eq(202L));
    }

    @Test void expiredLeaseCannotPublishGeneratedLesson() throws Exception {
        var lessons=mock(AiLessonCatalog.class);
        when(jdbc.queryForList(contains("returning *"),any(Object[].class))).thenReturn(List.of(Map.of(
                "id",42L,"user_id",7L,"model","test","job_type","LEARNING_CONTENT","input_snapshot",AiLessonContentTest.request().toString(),"attempt_count",1,"provider","OPENAI")));
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(jdbc.update(contains("set status='SUCCEEDED'"),any(Object[].class))).thenReturn(0);
        when(provider.complete(eq(7L),isNull(),eq("test"),eq("LEARNING_CONTENT"),any())).thenReturn(new AiProvider.Completion(AiLessonContentTest.candidate(),"lesson-stale",10,20));
        new AiWorker(jdbc,provider,json,manager,lessons).runSafely(42);
        verify(jdbc).update(contains("set status='SUCCEEDED'"),anyString(),eq(42L),anyString());
        verifyNoInteractions(lessons);
    }
}
