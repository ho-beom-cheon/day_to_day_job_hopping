package dev.dailycareer.learning;

import com.fasterxml.jackson.databind.*;
import dev.dailycareer.common.api.*;
import dev.dailycareer.common.idempotency.*;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.learning.ProblemViews.*;

@Service
public class ProblemService {
    public record AttemptRequest(@NotNull @JsonId Long problemId,@NotBlank @Size(max=10) String answer,@JsonId Long contentId) {}
    public record ReportRequest(@NotNull ReportReason reason,@NotBlank @Size(max=2000) String comment) {}
    public enum ReportReason {WRONG_ANSWER,UNCLEAR_QUESTION,OTHER}
    public record NoteRequest(@NotNull @Size(max=10000) String note) {}
    public record ResolveRequest(@NotNull ResolutionType resolutionType,@JsonId Long attemptId) {}
    public enum ResolutionType {MANUAL,CORRECT_RETRY}

    private final JdbcTemplate jdbc; private final ObjectMapper json; private final UserAccountService users; private final IdempotencyService idempotency;
    public ProblemService(JdbcTemplate jdbc,ObjectMapper json,UserAccountService users,IdempotencyService idempotency) {
        this.jdbc=jdbc;this.json=json;this.users=users;this.idempotency=idempotency;
    }

    @Transactional(readOnly=true)
    public Problem problem(long user,long problem) {return view(access(user,problem,null));}

    @Transactional
    public StoredReply submit(long user,AttemptRequest input,UUID key) {
        users.current(user); access(user,input.problemId(),input.contentId());
        return idempotency.execute(user,"ATTEMPT-001",key,json.valueToTree(input),()->submitLocked(user,input));
    }

    private StoredReply submitLocked(long user,AttemptRequest input) {
        lockUser(user); var p=access(user,input.problemId(),input.contentId());
        Answer answer=answer(p,input.answer());
        long course=id(p,"user_curriculum_id"),version=id(p,"problem_version_id");Long session=nullableId(p,"learning_session_id");
        int attemptNo=jdbc.queryForObject("""
                select count(*)+1 from daily_career.problem_attempt a join daily_career.problem_version v on v.id=a.problem_version_id
                where a.user_id=? and v.problem_id=? and a.status='SUBMITTED'
                """,Integer.class,user,id(p,"problem_id"));
        Map<String,Object> answerPayload=new LinkedHashMap<>();answerPayload.put("answer",input.answer());
        answerPayload.put("contentId",input.contentId()==null?null:Long.toString(input.contentId()));
        long attempt=insert("""
                insert into daily_career.problem_attempt(user_id,user_curriculum_id,problem_version_id,learning_session_id,status,answer_payload,submitted_at)
                values (?,?,?,?,'SUBMITTED',?::jsonb,clock_timestamp()) returning id
                """,user,course,version,session,json.valueToTree(answerPayload).toString());
        String digest=RequestFingerprint.digest(json.valueToTree(Map.of("problemVersionId",Long.toString(version),"answer",input.answer())));
        long run=insert("""
                insert into daily_career.grading_run(user_id,problem_attempt_id,run_no,status,grader_type,rule_version,input_digest,is_current,finished_at)
                values (?,?,1,'SUCCEEDED','RULE',?,?,true,clock_timestamp()) returning id
                """,user,attempt,(String)p.get("grading_rule_version"),digest);
        long result=insert("""
                insert into daily_career.grading_result(user_id,grading_run_id,problem_attempt_id,earned_score,max_score,is_correct,rubric_result,feedback_markdown)
                values (?,?,?, ?,1,?,'{}'::jsonb,?) returning id
                """,user,run,attempt,answer.correct?1:0,answer.correct,(String)p.get("explanation_markdown"));
        Long wrong=null; boolean created=false;
        if(!answer.correct) {
            var existing=jdbc.queryForList("select id from daily_career.wrong_answer where user_id=? and problem_id=? for update",Long.class,user,id(p,"problem_id"));
            if(existing.isEmpty()) {wrong=insert("insert into daily_career.wrong_answer(user_id,problem_id,first_wrong_at) values (?,?,clock_timestamp()) returning id",user,id(p,"problem_id"));created=true;}
            else {wrong=existing.getFirst();jdbc.update("update daily_career.wrong_answer set status='OPEN',mastered_at=null,resolution_type=null,resolution_attempt_id=null,updated_at=clock_timestamp(),revision=revision+1 where id=?",wrong);}
            jdbc.update("insert into daily_career.wrong_answer_occurrence(user_id,wrong_answer_id,grading_result_id) values (?,?,?)",user,wrong,result);
            jdbc.update("""
                    insert into daily_career.review_task(user_id,wrong_answer_id,due_at,algorithm_version,interval_days)
                    select ?,?,clock_timestamp()+interval '1 day','spaced-v1',1 where not exists
                    (select 1 from daily_career.review_task where wrong_answer_id=? and status='PLANNED')
                    """,user,wrong,wrong);
        }
        Attempt response=new Attempt(attempt,id(p,"problem_id"),input.contentId(),input.answer(),answer.correct,answer.correctAnswer,
                (String)p.get("explanation_markdown"),attemptNo,wrong,created,now("select submitted_at from daily_career.problem_attempt where id=?",attempt));
        return new StoredReply(201,json.valueToTree(response),null,"/api/v1/problem-attempts/"+attempt);
    }

    @Transactional(readOnly=true)
    public Attempt attempt(long user,long attempt) {
        users.current(user);var rows=jdbc.queryForList("""
                select a.id,a.problem_version_id,v.problem_id,a.answer_payload->>'answer' answer,a.answer_payload->>'contentId' content_id,a.submitted_at,
                  v.question_type,v.choices::text,v.answer_key::text,v.explanation_markdown,r.is_correct,
                  (select count(*) from daily_career.problem_attempt x join daily_career.problem_version xv on xv.id=x.problem_version_id
                    where x.user_id=a.user_id and xv.problem_id=v.problem_id and x.status='SUBMITTED'
                    and (x.submitted_at<a.submitted_at or (x.submitted_at=a.submitted_at and x.id<=a.id))) attempt_no,
                  w.id wrong_id,(o.id=(select min(o2.id) from daily_career.wrong_answer_occurrence o2 where o2.wrong_answer_id=w.id and o2.status='VALID')) wrong_created
                from daily_career.problem_attempt a join daily_career.grading_run g on g.problem_attempt_id=a.id and g.is_current
                join daily_career.grading_result r on r.grading_run_id=g.id and r.problem_attempt_id=a.id
                join daily_career.problem_version v on v.id=a.problem_version_id
                left join daily_career.wrong_answer_occurrence o on o.grading_result_id=r.id and o.status='VALID'
                left join daily_career.wrong_answer w on w.id=o.wrong_answer_id
                where a.id=? and a.user_id=?
                """,attempt,user);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);var r=rows.getFirst();Answer key=answer(r,(String)r.get("answer"));
        Long content=r.get("content_id")==null?null:Long.valueOf((String)r.get("content_id"));
        return new Attempt(id(r,"id"),id(r,"problem_id"),content,(String)r.get("answer"),(Boolean)r.get("is_correct"),key.correctAnswer,
                (String)r.get("explanation_markdown"),number(r,"attempt_no"),nullableId(r,"wrong_id"),Boolean.TRUE.equals(r.get("wrong_created")),time(r,"submitted_at"));
    }

    @Transactional
    public StoredReply report(long user,long problem,ReportRequest input,UUID key) {
        users.current(user);access(user,problem,null);
        return idempotency.execute(user,"PROBLEM-002",key,json.valueToTree(Map.of("problemId",Long.toString(problem),"body",input)),()->{
            var p=access(user,problem,null);long id=insert("""
                    insert into daily_career.problem_report(user_id,problem_id,problem_version_id,reason,comment)
                    values (?,?,?,?,?) returning id
                    """,user,id(p,"problem_id"),id(p,"problem_version_id"),input.reason().name(),input.comment());
            return new StoredReply(201,json.valueToTree(new Report(id,problem,"RECEIVED",now("select created_at from daily_career.problem_report where id=?",id))),null,null);
        });
    }

    @Transactional(readOnly=true)
    public WrongPage wrongs(long user,String category,String status,int page,int size,String sort) {
        users.current(user);if(page<0||size<1||size>100)throw new ApiException(VALIDATION_FAILED);
        if(status!=null&&!Set.of("UNRESOLVED","RESOLVED").contains(status))throw new ApiException(VALIDATION_FAILED);
        String order=switch(sort==null?"lastWrongAt,desc":sort){case "lastWrongAt,desc"->"last_wrong_at desc,id";case "lastWrongAt,asc"->"last_wrong_at,id";case "wrongCount,desc"->"wrong_count desc,id";default->throw new ApiException(INVALID_SORT_FIELD);};
        List<Map<String,Object>> all=new ArrayList<>(wrongRows(user,category,status));all.sort(comparator(order));int from=Math.min(page*size,all.size()),to=Math.min(from+size,all.size());
        var content=all.subList(from,to).stream().map(this::summary).toList();int total=all.size();return new WrongPage(content,page,size,total,total==0?0:(total+size-1)/size);
    }

    @Transactional(readOnly=true)
    public WrongAnswer wrong(long user,long wrong) {return detail(wrongRow(user,wrong));}

    @Transactional
    public WrongAnswer note(long user,long wrong,NoteRequest input,long revision) {
        lockUser(user);wrongRow(user,wrong);int n=jdbc.update("update daily_career.wrong_answer set user_note=?,revision=revision+1,updated_at=clock_timestamp() where id=? and user_id=? and revision=?",input.note(),wrong,user,revision);
        if(n!=1)throw new ApiException(PRECONDITION_FAILED);return wrong(user,wrong);
    }

    @Transactional
    public StoredReply resolve(long user,long wrong,ResolveRequest input,UUID key) {
        wrongRow(user,wrong);return idempotency.execute(user,"WRONG-003",key,json.valueToTree(Map.of("wrongAnswerId",Long.toString(wrong),"body",input)),()->{
            lockUser(user);var w=wrongRow(user,wrong);validateResolution(user,w,input);
            if(!"MASTERED".equals(w.get("status"))) {
                jdbc.update("update daily_career.wrong_answer set status='MASTERED',mastered_at=clock_timestamp(),resolution_type=?,resolution_attempt_id=?,review_count=review_count+1,revision=revision+1,updated_at=clock_timestamp() where id=?",input.resolutionType().name(),input.attemptId(),wrong);
                jdbc.update("update daily_career.review_task set status='CANCELED',revision=revision+1,updated_at=clock_timestamp() where wrong_answer_id=? and status='PLANNED'",wrong);
            }
            return new StoredReply(200,json.valueToTree(wrong(user,wrong)),null,null);
        });
    }

    @Transactional
    public WrongAnswer reopen(long user,long wrong) {
        lockUser(user);var w=wrongRow(user,wrong);if("MASTERED".equals(w.get("status"))) {
            jdbc.update("update daily_career.wrong_answer set status='OPEN',mastered_at=null,resolution_type=null,resolution_attempt_id=null,revision=revision+1,updated_at=clock_timestamp() where id=?",wrong);
            jdbc.update("""
                    insert into daily_career.review_task(user_id,wrong_answer_id,due_at,algorithm_version,interval_days)
                    select ?,?,clock_timestamp()+interval '1 day','spaced-v1',1 where not exists(select 1 from daily_career.review_task where wrong_answer_id=? and status='PLANNED')
                    """,user,wrong,wrong);
        }return wrong(user,wrong);
    }

    private void validateResolution(long user,Map<String,Object> w,ResolveRequest input) {
        if(input.resolutionType()==ResolutionType.MANUAL) {if(input.attemptId()!=null)throw new ApiException(WRONG_RESOLUTION_EVIDENCE_INVALID);return;}
        if(input.attemptId()==null)throw new ApiException(WRONG_RESOLUTION_EVIDENCE_INVALID);
        int valid=jdbc.queryForObject("""
                select count(*) from daily_career.problem_attempt a join daily_career.problem_version v on v.id=a.problem_version_id
                join daily_career.grading_run g on g.problem_attempt_id=a.id and g.is_current
                join daily_career.grading_result r on r.grading_run_id=g.id and r.is_correct
                where a.id=? and a.user_id=? and v.problem_id=? and a.submitted_at>?
                """,Integer.class,input.attemptId(),user,id(w,"problem_id"),w.get("last_wrong_at"));
        if(valid!=1)throw new ApiException(WRONG_RESOLUTION_EVIDENCE_INVALID);
    }

    private Map<String,Object> access(long user,long problem,Long content) {
        users.current(user);String sql="""
                select v.id problem_version_id,v.problem_id,v.question_type,v.difficulty,v.prompt_markdown,v.choices::text,v.answer_key::text,
                  v.explanation_markdown,v.grading_rule_version,p.owner_user_id,s.code category,a.id content_id,a.learning_day_item_id,i.user_curriculum_id,
                  (select ls.id from daily_career.learning_session ls where ls.user_id=a.user_id and ls.learning_day_item_id=a.learning_day_item_id
                    order by ls.started_at desc,ls.id desc limit 1) learning_session_id,
                  (select count(*) from daily_career.problem_attempt x join daily_career.problem_version xv on xv.id=x.problem_version_id
                    where x.user_id=a.user_id and xv.problem_id=p.id and x.status='SUBMITTED') attempt_count
                from daily_career.problem_version v join daily_career.problem p on p.id=v.problem_id join daily_career.subject s on s.id=p.subject_id
                join daily_career.learning_content_problem cp on cp.problem_version_id=v.id
                join daily_career.assigned_content a on a.content_version_id=cp.content_version_id and a.user_id=?
                join daily_career.learning_day_item i on i.id=a.learning_day_item_id and i.user_id=a.user_id
                where p.id=? and v.status='PUBLISHED' and p.enabled and (p.owner_user_id is null or p.owner_user_id=?)
                """+(content==null?"":" and a.id=? ")+"""
                order by a.completed_at nulls first,a.id limit 1
                """;
        var rows=content==null?jdbc.queryForList(sql,user,problem,user):jdbc.queryForList(sql,user,problem,user,content);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();
    }
    private Problem view(Map<String,Object> p) {var choices=choices((String)p.get("choices"));String type="TRUE_FALSE".equals(p.get("question_type"))?"TRUE_FALSE":"MULTIPLE_CHOICE";
        List<Option> options="TRUE_FALSE".equals(type)?List.of():java.util.stream.IntStream.range(0,choices.size()).mapToObj(i->new Option(i+1,choices.get(i).path("text").asText())).toList();
        return new Problem(id(p,"problem_id"),id(p,"content_id"),(String)p.get("category"),type,difficulty(number(p,"difficulty")),(String)p.get("prompt_markdown"),options,
                p.get("owner_user_id")==null?"CURATED":"AI_GENERATED",p.get("owner_user_id")==null?"HUMAN_REVIEWED":"UNREVIEWED",number(p,"attempt_count"),number(p,"attempt_count")>0);}
    private Answer answer(Map<String,Object> p,String submitted) {try {String type=(String)p.get("question_type");JsonNode key=json.readTree((String)p.get("answer_key"));
        if("TRUE_FALSE".equals(type)){if(!Set.of("TRUE","FALSE").contains(submitted))throw new ApiException(PROBLEM_INVALID_ANSWER);String correct=key.path("correct").asText();return new Answer(submitted.equals(correct),correct);}
        List<JsonNode> choices=choices((String)p.get("choices"));if(!submitted.matches("[1-6]"))throw new ApiException(PROBLEM_INVALID_ANSWER);int index=Integer.parseInt(submitted)-1;if(index>=choices.size())throw new ApiException(PROBLEM_INVALID_ANSWER);
        String correctKey=key.path("correct").asText();int correct=-1;for(int i=0;i<choices.size();i++)if(choiceKey(choices.get(i)).equals(correctKey))correct=i+1;if(correct<1)throw new ApiException(CONTENT_NOT_READY);
        return new Answer(index+1==correct,Integer.toString(correct));}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(CONTENT_NOT_READY);}}
    private List<JsonNode> choices(String value){try{JsonNode n=json.readTree(value);if(!n.isArray())throw new ApiException(CONTENT_NOT_READY);var r=new ArrayList<JsonNode>();n.forEach(r::add);return r;}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(CONTENT_NOT_READY);}}
    private static String choiceKey(JsonNode n){return n.hasNonNull("id")?n.get("id").asText():n.path("key").asText();}

    private List<Map<String,Object>> wrongRows(long user,String category,String status){String sql="""
            with occurrence as (
              select o.wrong_answer_id,o.created_at,a.answer_payload->>'answer' my_last_answer,
                v.prompt_markdown,v.difficulty,v.answer_key::text answer_key,v.choices::text choices,v.question_type,v.explanation_markdown,
                count(*) over(partition by o.wrong_answer_id)::integer wrong_count,
                row_number() over(partition by o.wrong_answer_id order by o.created_at desc,o.id desc) occurrence_order
              from daily_career.wrong_answer_occurrence o
              join daily_career.grading_result r on r.id=o.grading_result_id
              join daily_career.problem_attempt a on a.id=r.problem_attempt_id
              join daily_career.problem_version v on v.id=a.problem_version_id
              where o.status='VALID'
            )
            select w.*,p.id problem_id,s.code category,o.prompt_markdown,o.difficulty,o.answer_key,o.choices,o.question_type,o.explanation_markdown,
              o.wrong_count,o.created_at last_wrong_at,o.my_last_answer
            from daily_career.wrong_answer w join daily_career.problem p on p.id=w.problem_id join daily_career.subject s on s.id=p.subject_id
            join occurrence o on o.wrong_answer_id=w.id and o.occurrence_order=1
            where w.user_id=?
            """;var all=jdbc.queryForList(sql,user);return all.stream().filter(r->category==null||category.equals(r.get("category"))).filter(r->status==null||status.equals(publicStatus((String)r.get("status")))).toList();}
    private Map<String,Object> wrongRow(long user,long wrong){return wrongRows(user,null,null).stream().filter(r->id(r,"id")==wrong).findFirst().orElseThrow(()->new ApiException(RESOURCE_NOT_FOUND));}
    private WrongSummary summary(Map<String,Object> r){return new WrongSummary(id(r,"id"),id(r,"problem_id"),(String)r.get("category"),(String)r.get("prompt_markdown"),number(r,"wrong_count"),publicStatus((String)r.get("status")),time(r,"last_wrong_at"),number(r,"review_count"));}
    private WrongAnswer detail(Map<String,Object> r){Answer a=answer(r,(String)r.get("my_last_answer"));return new WrongAnswer(id(r,"id"),id(r,"problem_id"),(String)r.get("category"),(String)r.get("prompt_markdown"),number(r,"wrong_count"),publicStatus((String)r.get("status")),time(r,"last_wrong_at"),number(r,"review_count"),difficulty(number(r,"difficulty")),(String)r.get("my_last_answer"),a.correctAnswer,(String)r.get("explanation_markdown"),time(r,"first_wrong_at"),time(r,"mastered_at"),(String)r.get("resolution_type"),Objects.toString(r.get("user_note"),""),number(r,"revision"));}
    private static Comparator<Map<String,Object>> comparator(String order){Comparator<Map<String,Object>> id=Comparator.comparingLong(r->id(r,"id"));if(order.startsWith("wrong_count"))return Comparator.<Map<String,Object>>comparingInt(r->number(r,"wrong_count")).reversed().thenComparing(id);Comparator<Map<String,Object>> c=Comparator.comparing(r->time(r,"last_wrong_at"));if(order.contains("desc"))c=c.reversed();return c.thenComparing(id);}
    private static String publicStatus(String s){return "MASTERED".equals(s)?"RESOLVED":"UNRESOLVED";}
    private static String difficulty(int n){return n<=1?"BASIC":n<=3?"INTERMEDIATE":"ADVANCED";}
    private void lockUser(long user){jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);users.current(user);}
    private long insert(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private OffsetDateTime now(String sql,Object...args){return time(jdbc.queryForMap(sql,args).values().iterator().next());}
    private static OffsetDateTime time(Map<String,Object> r,String k){return time(r.get(k));}
    private static OffsetDateTime time(Object v){if(v==null)return null;if(v instanceof java.sql.Timestamp t)return t.toInstant().atOffset(ZoneOffset.ofHours(9));return ((OffsetDateTime)v).withOffsetSameInstant(ZoneOffset.ofHours(9));}
    private static long id(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
    private static Long nullableId(Map<String,Object> r,String k){return r.get(k)==null?null:id(r,k);}
    private static int number(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
    private record Answer(boolean correct,String correctAnswer){}
}
