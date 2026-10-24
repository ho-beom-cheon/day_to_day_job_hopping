package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class AiWorker {
    private final JdbcTemplate jdbc;
    private final AiProvider provider;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final AiLessonCatalog lessons;
    AiWorker(JdbcTemplate jdbc, AiProvider provider, ObjectMapper json, PlatformTransactionManager manager,AiLessonCatalog lessons) {
        this.jdbc=jdbc; this.provider=provider; this.json=json; this.tx=new TransactionTemplate(manager);this.lessons=lessons;
    }
    @Scheduled(fixedDelayString="${daily-career.ai.poll-delay-ms:5000}") void poll() {
        jdbc.update("""
                update daily_career.ai_job set status='UNKNOWN',error_code='AI_PROVIDER_RESULT_UNKNOWN',
                    finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1
                where status='RUNNING' and lease_until<clock_timestamp()
                """);
        jdbc.queryForList("select id from daily_career.ai_job where status='QUEUED' and (next_attempt_at is null or next_attempt_at<=clock_timestamp()) order by id limit 5", Long.class).forEach(this::runSafely);
    }
    void runSafely(long id) {
        String lease=UUID.randomUUID().toString();
        var rows=jdbc.queryForList("""
                update daily_career.ai_job set status='RUNNING',attempt_count=attempt_count+1,lease_token=?,
                    lease_until=clock_timestamp()+interval '3 minutes',updated_at=clock_timestamp(),revision=revision+1
                where id=? and status='QUEUED' returning *
                """, lease, id);
        if(rows.isEmpty()) return;
        Map<String,Object> row=rows.getFirst();
        try {
            if ("PRACTICE_PROBLEMS".equals(row.get("job_type"))) {
                JsonNode snapshot=json.readTree(row.get("input_snapshot").toString());
                AiPracticePlan plan=AiPracticePlan.forMinutes(snapshot.path("estimatedMinutes").asInt(),
                        snapshot.path("targetDifficulty").asInt(4));
                if (!plan.hasEnoughSource(snapshot.path("body").asText())) {
                    jdbc.update("""
                            update daily_career.ai_job set status='FAILED',error_code='AI_PRACTICE_SOURCE_TOO_SHORT',
                                finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1,
                                lease_token=null,lease_until=null
                            where id=? and status='RUNNING' and lease_token=?
                            """,id,lease);
                    return;
                }
            }
            String reference=row.get("browser_credential_ref")==null?null:row.get("browser_credential_ref").toString();
            JsonNode storedInput=json.readTree(row.get("input_snapshot").toString());
            JsonNode providerInput="PRACTICE_PROBLEMS".equals(row.get("job_type"))
                    ? StudyPrompts.practiceInput(json,storedInput):"DAILY_FEEDBACK".equals(row.get("job_type"))
                    ? StudyPrompts.feedbackInput(json,storedInput):AiLessonContent.PURPOSE.equals(row.get("job_type"))
                    ? AiLessonContent.providerInput(json,storedInput):StudyEvaluationContract.purpose(row.get("job_type").toString())
                    ? storedInput.path("providerInput"):storedInput;
            var completion=provider.complete(((Number)row.get("user_id")).longValue(),reference,row.get("model").toString(),
                    row.get("job_type").toString(),providerInput);
            JsonNode result;
            try {
                result = "PRACTICE_PROBLEMS".equals(row.get("job_type"))
                        ? AiPracticeQuestions.normalizeUsable(json, practiceInput(row), completion.result())
                        : "DAILY_FEEDBACK".equals(row.get("job_type"))
                        ? OpenRouterCompletion.normalize(json,completion.result(),storedInput):AiLessonContent.PURPOSE.equals(row.get("job_type"))
                        ? AiLessonContent.validate(json,storedInput,completion.result()):StudyEvaluationContract.purpose(row.get("job_type").toString())
                        ? StudyEvaluationContract.validate(json,row.get("job_type").toString(),storedInput,completion.result()):completion.result();
            } catch (IllegalArgumentException qualityFailure) {
                tx.executeWithoutResult(status -> {
                    int updated=jdbc.update("""
                            update daily_career.ai_job set status='FAILED',error_code='AI_PRACTICE_QUALITY_REJECTED',
                                result_payload=?::jsonb,reserved_amount=0,finished_at=clock_timestamp(),
                                updated_at=clock_timestamp(),revision=revision+1,lease_token=null,lease_until=null
                            where id=? and status='RUNNING' and lease_token=?
                            """.replace("AI_PRACTICE_QUALITY_REJECTED",AiLessonContent.PURPOSE.equals(row.get("job_type"))?"AI_LEARNING_QUALITY_REJECTED":StudyEvaluationContract.purpose(row.get("job_type").toString())?"AI_STUDY_QUALITY_REJECTED":"AI_PRACTICE_QUALITY_REJECTED"),completion.result().toString(),id,lease);
                    if(updated==1) recordUsage(row,id,completion);
                });
                return;
            }
            tx.executeWithoutResult(status -> {
                int updated=jdbc.update("""
                        update daily_career.ai_job set status='SUCCEEDED',result_payload=?::jsonb,reserved_amount=0,
                            finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1,lease_token=null,lease_until=null
                        where id=? and status='RUNNING' and lease_token=?
                        """,result.toString(),id,lease);
                if(updated==1) {
                    if(AiLessonContent.PURPOSE.equals(row.get("job_type"))) {
                        var published=lessons.publish(((Number)row.get("user_id")).longValue(),id,storedInput,result);
                        jdbc.update("update daily_career.ai_job set result_payload=?::jsonb where id=?",published.toString(),id);
                    }
                    recordUsage(row,id,completion);
                }
            });
        } catch(Exception failure) {
            boolean unknown=failure instanceof java.io.IOException || failure instanceof InterruptedException;
            if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            jdbc.update("""
                    update daily_career.ai_job set status=?,error_code=?,finished_at=clock_timestamp(),updated_at=clock_timestamp(),
                        revision=revision+1,lease_token=null,lease_until=null where id=? and status='RUNNING' and lease_token=?
                    """,unknown?"UNKNOWN":"FAILED",unknown?"AI_PROVIDER_RESULT_UNKNOWN":failure instanceof OpenAiCompletion.QuotaExceededException?"AI_PROVIDER_QUOTA_EXHAUSTED":"AI_PROVIDER_ERROR",id,lease);
        }
    }

    private void recordUsage(Map<String,Object> row, long id, AiProvider.Completion completion) {
        jdbc.update("""
                insert into daily_career.ai_usage(user_id,ai_job_id,attempt_no,provider,provider_request_id,status,
                    input_tokens,output_tokens,budget_cost_amount,pricing_snapshot,reconciled_at)
                values (?,?,?,?,?,'KNOWN',?,?,0,'{}'::jsonb,clock_timestamp())
                """,row.get("user_id"),id,row.get("attempt_count"),row.get("provider"),completion.requestId(),
                completion.inputTokens(),completion.outputTokens());
    }

    private JsonNode practiceInput(Map<String, Object> row) throws Exception {
        ObjectNode input = (ObjectNode) json.readTree(row.get("input_snapshot").toString());
        var prior = jdbc.queryForList("""
                select question->>'prompt' from daily_career.ai_job job
                cross join lateral jsonb_array_elements(job.result_payload->'questions') question
                where job.user_id=? and job.id<>? and job.job_type='PRACTICE_PROBLEMS'
                  and job.status='SUCCEEDED'
                  and exists (select 1 from daily_career.assigned_content previous
                              where previous.id::text=job.input_snapshot->>'contentId'
                                and previous.user_id=job.user_id
                                and previous.content_version_id::text=?)
                """,String.class,row.get("user_id"),row.get("id"),input.path("contentVersionId").asText());
        input.set("priorPrompts",json.valueToTree(prior));
        return input;
    }
}
