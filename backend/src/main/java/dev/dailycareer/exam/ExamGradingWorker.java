package dev.dailycareer.exam;

import com.fasterxml.jackson.databind.*;
import java.math.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExamGradingWorker {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final TransactionTemplate transaction;
    public ExamGradingWorker(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager transactionManager){this.jdbc=jdbc;this.json=json;this.transaction=new TransactionTemplate(transactionManager);}

    @Async
    @EventListener
    public void submitted(ExamService.Submitted event){gradeSafely(event.runId());}

    @Scheduled(fixedDelayString="${daily-career.grading.poll-delay-ms:5000}")
    public void recoverQueued(){jdbc.queryForList("select id from daily_career.grading_run where status='QUEUED' order by id limit 20",Long.class).forEach(this::gradeSafely);}

    public long retryFailed(long attemptId){
        long run=Objects.requireNonNull(transaction.execute(status->{
            var previous=jdbc.queryForMap("select * from daily_career.grading_run where test_attempt_id=? order by run_no desc limit 1 for update",attemptId);
            if(!"FAILED".equals(previous.get("status")))throw new IllegalStateException("Only failed grading can be retried");
            return jdbc.queryForObject("""
                    insert into daily_career.grading_run(user_id,test_attempt_id,run_no,status,grader_type,rule_version,input_digest)
                    values (?,?,?,'QUEUED',?,?,?) returning id
                    """,Long.class,previous.get("user_id"),attemptId,((Number)previous.get("run_no")).intValue()+1,previous.get("grader_type"),previous.get("rule_version"),previous.get("input_digest"));
        }));
        gradeSafely(run);return run;
    }

    public void gradeSafely(long runId){try{transaction.executeWithoutResult(status->{try{grade(runId);}catch(Exception failure){throw new GradingFailure(failure);}});}catch(GradingFailure failure){transaction.executeWithoutResult(status->fail(runId,"GRADING_INTERNAL_ERROR"));}}

    public void grade(long runId) throws Exception {
        var runs=jdbc.queryForList("select * from daily_career.grading_run where id=? for update skip locked",runId);if(runs.isEmpty())return;var run=runs.getFirst();
        if(!"QUEUED".equals(run.get("status")))return;jdbc.update("update daily_career.grading_run set status='RUNNING',updated_at=clock_timestamp(),revision=revision+1 where id=?",runId);
        long attempt=((Number)run.get("test_attempt_id")).longValue(),user=((Number)run.get("user_id")).longValue();
        var items=jdbc.queryForList("select * from daily_career.test_attempt_item where test_attempt_id=? and user_id=? order by display_order",attempt,user);
        if(items.isEmpty())throw new IllegalStateException("No attempt items");
        for(var item:items){JsonNode question=json.readTree(item.get("question_snapshot").toString()),grading=json.readTree(item.get("grading_snapshot").toString());String answer=answer(item),correctAnswer=correctAnswer(question,grading);boolean correct=Objects.equals(answer,correctAnswer);BigDecimal max=new BigDecimal(grading.path("points").asText());
            jdbc.update("insert into daily_career.grading_result(user_id,grading_run_id,test_attempt_id,test_attempt_item_id,earned_score,max_score,is_correct,rubric_result,feedback_markdown) values (?,?,?,?,?,?,?,'{}'::jsonb,?)",user,runId,attempt,item.get("id"),correct?max:BigDecimal.ZERO,max,correct,grading.path("explanation").asText());}
        jdbc.update("update daily_career.grading_run set is_current=false,updated_at=clock_timestamp(),revision=revision+1 where test_attempt_id=? and is_current",attempt);
        jdbc.update("update daily_career.grading_run set status='SUCCEEDED',is_current=true,error_code=null,finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?",runId);
    }

    public void fail(long runId,String code){jdbc.update("update daily_career.grading_run set status='FAILED',is_current=false,error_code=?,finished_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=? and status in ('QUEUED','RUNNING')",code,runId);}

    private static String answer(Map<String,Object> item){String s=item.get("answer_payload").toString();int p=s.indexOf("\"answer\":");if(p<0)return null;String v=s.substring(p+9).replaceAll("^[\\s\"]+|[\\s\"}]+$","");return v.isEmpty()?null:v;}
    private static String correctAnswer(JsonNode q,JsonNode g){String expected=g.path("answerKey").path("correct").asText();if("TRUE_FALSE".equals(q.path("type").asText()))return expected;JsonNode choices=g.path("choices");for(int i=0;i<choices.size();i++){JsonNode c=choices.get(i);String key=c.hasNonNull("id")?c.get("id").asText():c.path("key").asText();if(key.equals(expected))return Integer.toString(i+1);}throw new IllegalStateException("Invalid answer key");}
    private static final class GradingFailure extends RuntimeException {GradingFailure(Throwable cause){super(cause);}}
}
