package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import dev.dailycareer.common.api.*;
import dev.dailycareer.common.idempotency.*;
import dev.dailycareer.user.UserAccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
public class AiService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul"); private static final int DAILY_LIMIT=20;
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final UserAccountService users;private final IdempotencyService idempotency;private final AiProvider provider;
    private final AiLessonCatalog lessons;
    AiService(JdbcTemplate jdbc,ObjectMapper json,UserAccountService users,IdempotencyService idempotency,AiProvider provider,AiLessonCatalog lessons){this.jdbc=jdbc;this.json=json;this.users=users;this.idempotency=idempotency;this.provider=provider;this.lessons=lessons;}
    @Transactional public StoredReply create(long user,String operation,String purpose,JsonNode input,UUID key){users.current(user);jdbc.queryForList("select user_id from daily_career.browser_connection where user_id=? and provider='openrouter' for update",user);if(!provider.enabled(user))throw new ApiException(AI_DISABLED);validate(user,purpose,input);if(!provider.supports(user,purpose))throw new ApiException(AI_INPUT_NOT_ELIGIBLE);return idempotency.execute(user,operation,key,input,()->enqueue(user,purpose,input));}
    /** Internal only: scope and answers have already been selected from owned database rows. */
    @Transactional long enqueueStudy(long user,String purpose,JsonNode snapshot) {
        users.current(user);
        if(!StudyEvaluationContract.purpose(purpose))throw new ApiException(VALIDATION_FAILED);
        jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);
        if(!provider.enabled(user))throw new ApiException(AI_DISABLED);
        if(!provider.supports(user,purpose))throw new ApiException(AI_INPUT_NOT_ELIGIBLE);
        Integer active=jdbc.queryForObject("select count(*)::int from daily_career.ai_job where user_id=? and status in ('QUEUED','RUNNING')",Integer.class,user);
        if(active!=null && active>=2)throw new ApiException(AI_CONCURRENT_JOB_LIMIT);
        LocalDate today=LocalDate.now(SEOUL);
        Integer used=jdbc.queryForObject("select count(*)::int from daily_career.ai_job where user_id=? and status<>'FAILED' and created_at>=? and created_at<?",Integer.class,user,today.atStartOfDay(SEOUL).toOffsetDateTime(),today.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime());
        if(used!=null && used>=DAILY_LIMIT)throw new ApiException(AI_REQUEST_LIMIT_EXCEEDED);
        return Objects.requireNonNull(jdbc.queryForObject("insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,provider,model,prompt_version,input_snapshot) values (?,?,?,?,'QUEUED',?,?,?,?::jsonb) returning id",Long.class,
                user,ensureBudget(user,today),purpose,purpose+":"+UUID.randomUUID(),provider.name(user),provider.model(user),StudyPrompts.VERSION,snapshot.toString()));
    }
    private StoredReply enqueue(long user,String purpose,JsonNode input){
        JsonNode lessonSnapshot=null;
        if(AiLessonContent.PURPOSE.equals(purpose)) {
            jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);
            lessonSnapshot=lessons.snapshot(user,input);
            var prior=jdbc.queryForList("""
                select * from daily_career.ai_job where user_id=? and job_type='LEARNING_CONTENT'
                    and input_snapshot=?::jsonb and provider=? and model=? and status in ('QUEUED','RUNNING','SUCCEEDED')
                order by id desc limit 1
                """,user,lessonSnapshot.toString(),provider.name(user),provider.model(user));
            if(!prior.isEmpty())return receipt(prior.getFirst(),true);
        }
        if ("PRACTICE_PROBLEMS".equals(purpose)) {
            String content = input.path("contentId").asText();
            jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))",Object.class,
                    "ai-practice:"+user+":"+content);
            var generating = jdbc.queryForList("""
                    select * from daily_career.ai_job where user_id=? and job_type='PRACTICE_PROBLEMS'
                      and input_snapshot->>'contentId'=? and status in ('QUEUED','RUNNING')
                    order by id desc limit 1
                    """,user,content);
            if (!generating.isEmpty()) return receipt(generating.getFirst(),true);
        }
        Integer active=jdbc.queryForObject("select count(*)::int from daily_career.ai_job where user_id=? and status in ('QUEUED','RUNNING')",Integer.class,user);
        if(active!=null&&active>=2)throw new ApiException(AI_CONCURRENT_JOB_LIMIT);
        LocalDate today=LocalDate.now(SEOUL);
        long budget=ensureBudget(user,today);
        Integer used=jdbc.queryForObject("select count(*)::int from daily_career.ai_job where user_id=? and reused_from_job_id is null and status<>'FAILED' and created_at>=? and created_at<?",Integer.class,user,today.atStartOfDay(SEOUL).toOffsetDateTime(),today.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime());
        if(used!=null&&used>=DAILY_LIMIT)throw new ApiException(AI_REQUEST_LIMIT_EXCEEDED);
        JsonNode snapshot=lessonSnapshot==null?snapshot(user,purpose,input):lessonSnapshot;
        String version=sha(snapshot.toString()),dedupe=purpose+":"+version+":"+Objects.toString(provider.credentialReference(user),provider.name(user)+":"+provider.model(user));
        var prior=jdbc.queryForList("select * from daily_career.ai_job where user_id=? and (dedupe_key=? or dedupe_key like ?) and status='SUCCEEDED'",user,dedupe,dedupe+":%");
        if(!prior.isEmpty())return receipt(prior.getFirst(),true);
        long id=Objects.requireNonNull(jdbc.queryForObject("insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,provider,model,prompt_version,input_snapshot,browser_credential_ref) values (?,?,?,?,'QUEUED',?,?,?,?::jsonb,?::uuid) returning id",Long.class,user,budget,purpose,dedupe+":"+UUID.randomUUID(),provider.name(user),provider.model(user),"PRACTICE_PROBLEMS".equals(purpose)?StudyPrompts.VERSION:"DAILY_FEEDBACK".equals(purpose)?StudyPrompts.VERSION+"-feedback":AiLessonContent.PURPOSE.equals(purpose)?AiLessonContent.VERSION:"ai-v1",snapshot.toString(),provider.credentialReference(user)));
        return receipt(jdbc.queryForMap("select * from daily_career.ai_job where id=?",id),false);
    }
    @Transactional(readOnly=true) public Map<String,Object> get(long user,long id){users.current(user);var rows=jdbc.queryForList("select * from daily_career.ai_job where id=? and user_id=?",id,user);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return view(rows.getFirst());}
    @Transactional(readOnly=true) public Map<String,Object> list(long user,String purpose,String status,int page,int size){users.current(user);if(page<0||size<1||size>100)throw new ApiException(VALIDATION_FAILED);StringBuilder sql=new StringBuilder("select * from daily_career.ai_job where user_id=?");List<Object> args=new ArrayList<>();args.add(user);if(purpose!=null&&!purpose.isBlank()){sql.append(" and job_type=?");args.add(purpose);}if(status!=null&&!status.isBlank()){sql.append(" and status=?");args.add(status);}sql.append(" order by created_at desc,id desc");var rows=jdbc.queryForList(sql.toString(),args.toArray());int total=rows.size(),from=Math.min(page*size,total),to=Math.min(from+size,total);return Map.of("content",rows.subList(from,to).stream().map(this::summary).toList(),"page",page,"size",size,"totalElements",total,"totalPages",total==0?0:(total+size-1)/size);}
    @Transactional(readOnly=true) public Map<String,Object> usage(long user){users.current(user);LocalDate d=LocalDate.now(SEOUL);var r=jdbc.queryForMap("select count(distinct j.id) filter (where j.status<>'FAILED')::int requests,coalesce(sum(u.input_tokens),0)::bigint input,coalesce(sum(u.output_tokens),0)::bigint output from daily_career.ai_job j left join daily_career.ai_usage u on u.ai_job_id=j.id where j.user_id=? and j.reused_from_job_id is null and j.created_at>=? and j.created_at<?",user,d.atStartOfDay(SEOUL).toOffsetDateTime(),d.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime());int used=((Number)r.get("requests")).intValue();Map<String,Object> result=new LinkedHashMap<>(Map.of("date",d,"enabled",provider.enabled(user),"usedRequestCount",used,"maxRequestCount",DAILY_LIMIT,"reservedOutputTokens",0,"actualInputTokens",r.get("input"),"actualOutputTokens",r.get("output"),"dailyTokenBudget",100000,"remainingRequestCount",Math.max(0,DAILY_LIMIT-used),"resetAt",d.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime()));result.put("provider",provider.name(user));return result;}
    private JsonNode snapshot(long user, String purpose, JsonNode input) {
        if ("PRACTICE_PROBLEMS".equals(purpose)) {
            long contentId = parse(input.path("contentId").asText());
            var rows = jdbc.queryForList("""
                    select a.id,a.content_version_id,a.learning_day_item_id,d.title,d.learning_objective,
                           v.body_markdown,d.key_points::text,s.estimated_minutes
                    from daily_career.assigned_content a
                    join daily_career.learning_content_version v on v.id=a.content_version_id
                    join daily_career.learning_content_detail d on d.content_version_id=v.id
                    join daily_career.assigned_session_detail s on s.learning_day_item_id=a.learning_day_item_id
                      and s.user_id=a.user_id
                    join daily_career.learning_day_item i on i.id=a.learning_day_item_id and i.user_id=a.user_id
                    join daily_career.user_curriculum c on c.id=i.user_curriculum_id and c.user_id=i.user_id
                    where a.id=? and a.user_id=? and a.completion_rule='ALL_PROBLEMS_ATTEMPTED'
                      and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED')
                    """, contentId, user);
            if (rows.isEmpty()) throw new ApiException(AI_INPUT_NOT_ELIGIBLE);
            var row = rows.getFirst();
            int difficulty = input.has("difficulty") ? input.path("difficulty").intValue() : 4;
            AiPracticePlan plan = AiPracticePlan.forMinutes(((Number)row.get("estimated_minutes")).intValue(), difficulty);
            if (!plan.hasEnoughSource(row.get("body_markdown").toString()))
                throw new ApiException(AI_PRACTICE_SOURCE_TOO_SHORT);
            Integer previous = jdbc.queryForObject("""
                    select count(*)::int from daily_career.ai_job
                    where user_id=? and job_type='PRACTICE_PROBLEMS' and status='SUCCEEDED'
                      and input_snapshot->>'contentId'=?
                    """,Integer.class,user,Long.toString(contentId));
            var priorPrompts = jdbc.queryForList("""
                    select question->>'prompt' from daily_career.ai_job job
                    cross join lateral jsonb_array_elements(job.result_payload->'questions') question
                    where job.user_id=? and job.job_type='PRACTICE_PROBLEMS' and job.status='SUCCEEDED'
                      and exists (select 1 from daily_career.assigned_content prior
                                  where prior.id::text=job.input_snapshot->>'contentId'
                                    and prior.user_id=job.user_id and prior.content_version_id=?)
                    order by job.id desc limit 40
                    """,String.class,user,row.get("content_version_id"));
            var snapshot = json.createObjectNode().put("contentId", Long.toString(contentId))
                    .put("contentVersionId",row.get("content_version_id").toString())
                    .put("sessionId", row.get("learning_day_item_id").toString())
                    .put("questionCount", plan.questionCount())
                    .put("estimatedMinutes", plan.estimatedMinutes())
                    .put("targetDifficulty", plan.targetDifficulty())
                    .put("generationSequence",(previous==null?0:previous)+1)
                    .put("title", row.get("title").toString())
                    .put("learningObjective", row.get("learning_objective").toString())
                    .put("body", row.get("body_markdown").toString());
            snapshot.set("difficultyLevels",json.valueToTree(plan.difficulties()));
            snapshot.set("priorPrompts",json.valueToTree(priorPrompts));
            try { snapshot.set("keyPoints", json.readTree(row.get("key_points").toString())); }
            catch (Exception e) { throw new IllegalStateException("Invalid content key points", e); }
            return snapshot;
        }
        if (!Set.of("OPENROUTER", "OPENAI").contains(provider.name(user))) return input;
        var snapshot = json.createObjectNode().put("curriculumId", input.path("curriculumId").asText()).put("date", input.path("date").asText());
        var rows = jdbc.queryForList("""
                select item.activity_type,item.status,day.local_date::text as planned_date,
                       coalesce(content.title,problem.title,item.activity_type) as title
                from daily_career.learning_day_item item
                join daily_career.learning_day day on day.id=item.learning_day_id and day.user_id=item.user_id
                left join daily_career.learning_content_version version on version.id=item.content_version_id
                left join daily_career.learning_content content on content.id=version.learning_content_id
                left join daily_career.problem_version pv on pv.id=item.problem_version_id
                left join daily_career.problem problem on problem.id=pv.problem_id
                where item.user_id=? and item.user_curriculum_id=? and day.local_date=?::date
                  and item.status in ('IN_PROGRESS','COMPLETED')
                order by item.id limit 100
                """, user, parse(input.path("curriculumId").asText()), input.path("date").asText());
        if (rows.isEmpty()) throw new ApiException(AI_INPUT_NOT_ELIGIBLE);
        snapshot.set("studyFocus", json.valueToTree(rows.stream().map(row -> row.get("title").toString()).distinct().limit(10).toList()));
        return snapshot;
    }
    private void validate(long user,String purpose,JsonNode in){if(AiLessonContent.PURPOSE.equals(purpose)){AiLessonCatalog.validateRequest(in);return;}if(!in.isObject())throw new ApiException(VALIDATION_FAILED);if(purpose.equals("PRACTICE_PROBLEMS")&&in.has("difficulty")&&(!in.path("difficulty").isInt()||in.path("difficulty").intValue()<3||in.path("difficulty").intValue()>5))throw new ApiException(VALIDATION_FAILED);String field=switch(purpose){case"DAILY_FEEDBACK"->"curriculumId";case"MONTHLY_REVIEW"->"evaluationId";case"EXPLANATION","PRACTICE_PROBLEMS"->"contentId";case"WRONG_ANSWER_REVIEW"->"wrongAnswerId";default->throw new ApiException(VALIDATION_FAILED);};long id=parse(in.path(field).asText());String sql=switch(purpose){case"DAILY_FEEDBACK"->"select count(*) from daily_career.user_curriculum where id=? and user_id=?";case"MONTHLY_REVIEW"->"select count(*) from daily_career.monthly_evaluation where id=? and user_id=?";case"PRACTICE_PROBLEMS"->"select count(*) from daily_career.assigned_content a join daily_career.learning_day_item i on i.id=a.learning_day_item_id and i.user_id=a.user_id join daily_career.user_curriculum c on c.id=i.user_curriculum_id and c.user_id=i.user_id where a.id=? and a.user_id=? and a.completion_rule='ALL_PROBLEMS_ATTEMPTED' and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED')";case"EXPLANATION"->"select count(*) from daily_career.learning_session_content where learning_content_version_id in(select id from daily_career.learning_content_version where learning_content_id=?) and user_id=?";default->"select count(*) from daily_career.wrong_answer where id=? and user_id=?";};Long n=jdbc.queryForObject(sql,Long.class,id,user);if(n==null||n==0)throw new ApiException(AI_INPUT_NOT_ELIGIBLE);if(purpose.equals("DAILY_FEEDBACK")){try{LocalDate date=LocalDate.parse(in.path("date").asText());if(date.isAfter(LocalDate.now(SEOUL)))throw new ApiException(AI_INPUT_NOT_ELIGIBLE);}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(VALIDATION_FAILED);}}}
    private long ensureBudget(long user,LocalDate d){var ids=jdbc.queryForList("select id from daily_career.ai_budget where user_id=? and period_start<=? and period_end>? order by id limit 1",Long.class,user,d,d);if(!ids.isEmpty())return ids.getFirst();return Objects.requireNonNull(jdbc.queryForObject("insert into daily_career.ai_budget(user_id,period_start,period_end,limit_amount) values (?,?,?,0) returning id",Long.class,user,d,d.plusDays(1)));}
    private StoredReply receipt(Map<String,Object> r,boolean reused){long id=id(r);var data=json.valueToTree(Map.of("jobId",String.valueOf(id),"status",status(r),"statusPath","/api/v1/ai/jobs/"+id,"createdAt",time(r.get("created_at")),"reused",reused));return new StoredReply(202,data,null,"/api/v1/ai/jobs/"+id);}
    private Map<String,Object> view(Map<String,Object> r){Map<String,Object> v=new LinkedHashMap<>();v.put("jobId",String.valueOf(id(r)));v.put("purpose",r.get("job_type"));v.put("status",status(r));v.put("dataVersion",sha(r.get("input_snapshot").toString()));v.put("createdAt",time(r.get("created_at")));v.put("startedAt",null);v.put("completedAt",r.get("finished_at")==null?null:time(r.get("finished_at")));v.put("result",r.get("result_payload")==null||StudyEvaluationContract.purpose(r.get("job_type").toString())||"PRACTICE_PROBLEMS".equals(r.get("job_type"))||(AiLessonContent.PURPOSE.equals(r.get("job_type"))&&!"SUCCEEDED".equals(r.get("status")))?null:read(r.get("result_payload").toString()));v.put("error","FAILED".equals(status(r))?Map.of("code",Objects.toString(r.get("error_code"),"AI_PROVIDER_ERROR"),"message","AI_PROVIDER_QUOTA_EXHAUSTED".equals(r.get("error_code"))?"OpenAI API 잔액이 부족합니다. OpenAI Platform에서 결제 상태를 확인한 뒤 다시 시도해 주세요.":"AI 작업을 완료하지 못했습니다.","details",List.of(),"retryable",false,"retryAfterSeconds",5):null);return v;}
    private Map<String,Object> summary(Map<String,Object> r){Map<String,Object> v=new LinkedHashMap<>();v.put("jobId",String.valueOf(id(r)));v.put("purpose",r.get("job_type"));v.put("status",status(r));v.put("createdAt",time(r.get("created_at")));v.put("completedAt",r.get("finished_at")==null?null:time(r.get("finished_at")));return v;}
    private static String status(Map<String,Object> r){return switch(r.get("status").toString()){case"QUEUED"->"READY";case"RUNNING"->"PROCESSING";case"SUCCEEDED"->"SUCCESS";case"UNKNOWN"->"UNKNOWN";default->"FAILED";};}private static long id(Map<String,Object>r){return((Number)r.get("id")).longValue();}
    private static long parse(String v){try{long n=Long.parseLong(v);if(n<1)throw new NumberFormatException();return n;}catch(Exception e){throw new ApiException(VALIDATION_FAILED);}}private JsonNode read(String v){try{return json.readTree(v);}catch(Exception e){throw new IllegalStateException(e);}}private static String sha(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}private static OffsetDateTime time(Object v){return v instanceof java.sql.Timestamp t?t.toInstant().atZone(SEOUL).toOffsetDateTime():((OffsetDateTime)v).atZoneSameInstant(SEOUL).toOffsetDateTime();}
}
