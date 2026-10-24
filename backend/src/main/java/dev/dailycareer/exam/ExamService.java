package dev.dailycareer.exam;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.*;
import dev.dailycareer.user.UserAccountService;
import jakarta.validation.constraints.Size;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.exam.ExamViews.*;

@Service
public class ExamService {
    public record AnswerRequest(@Size(max=10) String answer) {}
    public record Submitted(long runId) {}
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final UserAccountService users;
    private final IdempotencyService idempotency; private final ApplicationEventPublisher events;
    private final Clock clock;
    public ExamService(JdbcTemplate jdbc,ObjectMapper json,UserAccountService users,IdempotencyService idempotency,ApplicationEventPublisher events,Clock clock){
        this.jdbc=jdbc;this.json=json;this.users=users;this.idempotency=idempotency;this.events=events;this.clock=clock;
    }

    @Transactional(readOnly=true)
    public ExamPage exams(long user,String type,String status,int page,int size){
        users.current(user); if(page<0||size<1||size>100)throw new ApiException(VALIDATION_FAILED);
        if(type!=null&&!Set.of("DAILY","WEEKLY","MONTHLY","FINAL").contains(type))throw new ApiException(VALIDATION_FAILED);
        if(status!=null&&!Set.of("UPCOMING","AVAILABLE","COMPLETED").contains(status))throw new ApiException(VALIDATION_FAILED);
        var all=jdbc.queryForList(baseExamSql()+" order by d.local_date,i.order_no,t.id",user,user,user,user).stream().map(this::exam).filter(e->type==null||type.equals(e.type())).filter(e->status==null||status.equals(publicStatus(e))).toList();
        int from=Math.min(page*size,all.size()),to=Math.min(from+size,all.size());return new ExamPage(all.subList(from,to),page,size,all.size(),all.isEmpty()?0:(all.size()+size-1)/size);
    }
    @Transactional(readOnly=true)
    public Exam exam(long user,long test){users.current(user);return exam(examRow(user,test));}

    @Transactional
    public StoredReply start(long user,long test,UUID key){
        users.current(user);examRow(user,test);
        return idempotency.execute(user,"TEST-002",key,json.valueToTree(Map.of("testId",Long.toString(test))),()->startLocked(user,test));
    }
    private StoredReply startLocked(long user,long test){
        lockUser(user);var row=examRow(user,test);OffsetDateTime now=now();
        LocalDate scheduled=date(row,"local_date");OffsetDateTime availableFrom=scheduled.atStartOfDay(SEOUL).toOffsetDateTime(),availableUntil=scheduled.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime();
        if(now.isBefore(availableFrom)||!now.isBefore(availableUntil))throw new ApiException(TEST_NOT_AVAILABLE);
        var active=jdbc.queryForList("""
                select a.id from daily_career.test_attempt a where a.user_id=? and a.test_version_id=?
                  and a.user_curriculum_id=?
                  and (a.status='IN_PROGRESS' or (a.status='SUBMITTED' and not exists (
                    select 1 from daily_career.grading_run g where g.test_attempt_id=a.id and g.is_current and g.status='SUCCEEDED'
                  ))) order by a.id desc limit 1
                """,Long.class,user,id(row,"test_version_id"),id(row,"curriculum_id"));
        if(!active.isEmpty())throw new ApiException(TEST_ACTIVE_ATTEMPT_EXISTS);
        int count=number(row,"attempt_count"),limit=row.get("attempt_limit")==null?100:number(row,"attempt_limit");if(count>=limit)throw new ApiException(TEST_ATTEMPT_LIMIT_EXCEEDED);
        OffsetDateTime deadline=now.plusSeconds(number(row,"time_limit_seconds"));
        long attempt=insert("insert into daily_career.test_attempt(user_id,user_curriculum_id,test_version_id,attempt_no,started_at,deadline_at) values (?,?,?,?,?,?) returning id",user,id(row,"curriculum_id"),id(row,"test_version_id"),count+1,now,deadline);
        var items=jdbc.queryForList("""
                select ti.id test_item_id,ti.problem_id,ti.item_no,ti.max_score,pv.id problem_version_id,pv.question_type,pv.prompt_markdown,
                  pv.choices::text,pv.answer_key::text,pv.rubric::text,pv.explanation_markdown,pv.grading_rule_version,s.code category
                from daily_career.test_item ti join daily_career.problem_version pv on pv.id=ti.problem_version_id
                join daily_career.problem p on p.id=pv.problem_id join daily_career.subject s on s.id=p.subject_id
                where ti.test_version_id=? and pv.status='PUBLISHED' order by ti.item_no
                """,id(row,"test_version_id"));
        if(items.isEmpty())throw new ApiException(CONTENT_NOT_READY);
        for(var item:items){
            ObjectNode question=json.createObjectNode().put("problemId",Long.toString(id(item,"problem_id"))).put("category",(String)item.get("category"))
                    .put("type",publicType((String)item.get("question_type"))).put("question",(String)item.get("prompt_markdown"));
            question.set("options",optionSnapshot((String)item.get("question_type"),(String)item.get("choices")));
            ObjectNode grading=json.createObjectNode().put("problemVersionId",Long.toString(id(item,"problem_version_id"))).put("points",integer(item,"max_score"))
                    .put("ruleVersion",(String)item.get("grading_rule_version")).put("explanation",(String)item.get("explanation_markdown"));
            grading.set("answerKey",tree((String)item.get("answer_key")));grading.set("choices",tree((String)item.get("choices")));grading.set("rubric",tree((String)item.get("rubric")));
            jdbc.update("insert into daily_career.test_attempt_item(user_id,test_attempt_id,test_version_id,test_item_id,display_order,question_snapshot,grading_snapshot) values (?,?,?,?,?,?::jsonb,?::jsonb)",user,attempt,id(row,"test_version_id"),id(item,"test_item_id"),number(item,"item_no"),question.toString(),grading.toString());
        }
        return new StoredReply(201,json.valueToTree(attempt(user,attempt)),null,"/api/v1/test-attempts/"+attempt);
    }

    @Transactional(readOnly=true)
    public Attempt attempt(long user,long attempt){users.current(user);return attemptView(attemptRow(user,attempt));}

    @Transactional
    public SavedAnswer save(long user,long attempt,long problem,AnswerRequest input,int revision){
        users.current(user);var a=lockAttempt(user,attempt);if(!"IN_PROGRESS".equals(a.get("status")))throw new ApiException(TEST_ATTEMPT_ALREADY_SUBMITTED);
        if(!now().isBefore(time(a,"deadline_at")))throw new ApiException(TEST_ATTEMPT_EXPIRED);
        var rows=jdbc.queryForList("select * from daily_career.test_attempt_item where test_attempt_id=? and user_id=? and question_snapshot->>'problemId'=? for update",attempt,user,Long.toString(problem));
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);var item=rows.getFirst();Revisions.requireMatch(revision,id(item,"revision"));
        validateAnswer(Objects.toString(item.get("question_snapshot")),input.answer());String payload=input.answer()==null?"{}":json.createObjectNode().put("answer",input.answer()).toString();
        jdbc.update("update daily_career.test_attempt_item set answer_payload=?::jsonb,answered_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?",payload,id(item,"id"));
        var saved=jdbc.queryForMap("select * from daily_career.test_attempt_item where id=?",id(item,"id"));return new SavedAnswer(attempt,problem,answer(saved),number(saved,"revision"),time(saved,"answered_at"));
    }

    @Transactional
    public StoredReply submit(long user,long attempt,UUID key){
        users.current(user);attemptRow(user,attempt);
        StoredReply reply=idempotency.execute(user,"TEST-005",key,json.valueToTree(Map.of("attemptId",Long.toString(attempt))),()->submitLocked(user,attempt));
        JsonNode data=reply.data(); if(data!=null&&data.has("gradingStatus")&&"WAITING".equals(data.get("gradingStatus").asText())){
            var runs=jdbc.queryForList("select id from daily_career.grading_run where test_attempt_id=? order by run_no desc limit 1",Long.class,attempt);if(!runs.isEmpty())events.publishEvent(new Submitted(runs.getFirst()));
        }return reply;
    }
    private StoredReply submitLocked(long user,long attempt){
        var a=lockAttempt(user,attempt);if("IN_PROGRESS".equals(a.get("status"))){
            boolean timeout=!now().isBefore(time(a,"deadline_at"));String reason=timeout?"TIME_LIMIT":"USER";OffsetDateTime submitted=timeout?time(a,"deadline_at"):now();
            var answers=jdbc.queryForList("select question_snapshot->>'problemId' problem_id,answer_payload->>'answer' answer from daily_career.test_attempt_item where test_attempt_id=? order by display_order",attempt);
            String digest=RequestFingerprint.digest(json.valueToTree(Map.of("answers",answers)));jdbc.update("update daily_career.test_attempt set status='SUBMITTED',submitted_at=?,submit_reason=?,submission_digest=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",submitted,reason,digest,attempt);
            insert("insert into daily_career.grading_run(user_id,test_attempt_id,run_no,status,grader_type,rule_version,input_digest) values (?,?,1,'QUEUED','RULE','test-rule-v1',?) returning id",user,attempt,digest);
        }
        a=attemptRow(user,attempt);String grading=gradingStatus(attempt);SubmitResult result=new SubmitResult(attempt,id(a,"test_id")>0?publicAttemptStatus(a,grading):"SUBMITTED",grading,(String)a.get("submit_reason"),time(a,"submitted_at"),"/api/v1/test-attempts/"+attempt+"/result");
        return new StoredReply(202,json.valueToTree(result),null,result.resultPath());
    }

    @Transactional(readOnly=true)
    public Result result(long user,long attempt){
        users.current(user);var a=attemptRow(user,attempt);var runs=jdbc.queryForList("select * from daily_career.grading_run where test_attempt_id=? and user_id=? and is_current",attempt,user);
        if(runs.isEmpty())throw new ApiException(TEST_RESULT_NOT_READY);var run=runs.getFirst();
        var rows=jdbc.queryForList("""
                select i.question_snapshot::text,i.grading_snapshot::text,i.answer_payload->>'answer' answer,r.earned_score,r.max_score,r.is_correct
                from daily_career.test_attempt_item i join daily_career.grading_result r on r.test_attempt_item_id=i.id and r.grading_run_id=?
                where i.test_attempt_id=? order by i.display_order
                """,id(run,"id"),attempt);
        List<ResultProblem> problems=rows.stream().map(this::resultProblem).toList();int total=problems.size(),correct=(int)problems.stream().filter(ResultProblem::correct).count(),unanswered=(int)problems.stream().filter(p->p.myAnswer()==null).count();
        int earned=problems.stream().mapToInt(ResultProblem::earnedPoints).sum(),points=problems.stream().mapToInt(ResultProblem::points).sum();BigDecimal score=percent(earned,points);
        Map<String,List<ResultProblem>> grouped=new LinkedHashMap<>();problems.forEach(p->grouped.computeIfAbsent(p.category(),x->new ArrayList<>()).add(p));
        var categories=grouped.entrySet().stream().map(e->{int ec=(int)e.getValue().stream().filter(ResultProblem::correct).count(),ep=e.getValue().stream().mapToInt(ResultProblem::earnedPoints).sum(),tp=e.getValue().stream().mapToInt(ResultProblem::points).sum();return new CategoryScore(e.getKey(),ec,e.getValue().size(),ep,tp,percent(ep,tp));}).toList();
        BigDecimal pass=decimal(a,"pass_percent");return new Result(attempt,id(a,"test_id"),(String)a.get("title"),score,pass,score.compareTo(pass)>=0,correct,total-correct,unanswered,total,earned,points,time(a,"submitted_at"),time(run,"finished_at"),categories,problems);
    }

    @Transactional(readOnly=true)
    public AttemptPage attempts(long user,long testId,int page,int size){
        users.current(user);if(page<0||size<1||size>100)throw new ApiException(VALIDATION_FAILED);
        var all=jdbc.queryForList("""
                select a.*,t.id test_id,t.title,g.status grading_status,g.finished_at,
                  case when g.is_current then (select round(sum(r.earned_score)/sum(r.max_score)*100,1) from daily_career.grading_result r where r.grading_run_id=g.id) end score
                from daily_career.test_attempt a join daily_career.test_version v on v.id=a.test_version_id join daily_career.test t on t.id=v.test_id
                left join daily_career.grading_run g on g.test_attempt_id=a.id and (g.is_current or g.id=(select max(x.id) from daily_career.grading_run x where x.test_attempt_id=a.id))
                where a.user_id=? and exists (select 1 from daily_career.user_curriculum c
                  where c.id=a.user_curriculum_id and c.user_id=a.user_id
                    and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED'))
                  and (?=0 or t.id=?) order by a.started_at desc,a.id desc
                """,user,testId,testId);int from=Math.min(page*size,all.size()),to=Math.min(from+size,all.size());
        var content=all.subList(from,to).stream().map(r->new AttemptSummary(id(r,"id"),id(r,"test_id"),(String)r.get("title"),number(r,"attempt_no"),publicAttemptStatus(r,(String)r.get("grading_status")),r.get("score")==null?null:decimal(r,"score"),time(r,"started_at"),time(r,"submitted_at"),time(r,"finished_at"))).toList();
        return new AttemptPage(content,page,size,all.size(),all.isEmpty()?0:(all.size()+size-1)/size);
    }

    private String baseExamSql(){return """
            select t.id test_id,t.title,t.test_type,v.id test_version_id,v.version_no,v.time_limit_seconds,v.attempt_limit,v.pass_percent,
              i.user_curriculum_id curriculum_id,d.local_date,count(ti.id)::integer problem_count,
              (select count(*) from daily_career.test_attempt a where a.user_id=? and a.test_version_id=v.id
                and a.user_curriculum_id=i.user_curriculum_id)::integer attempt_count,
              (select a.id from daily_career.test_attempt a where a.user_id=? and a.test_version_id=v.id
                and a.user_curriculum_id=i.user_curriculum_id
                and (a.status='IN_PROGRESS' or (a.status='SUBMITTED' and not exists (
                  select 1 from daily_career.grading_run g where g.test_attempt_id=a.id and g.is_current and g.status='SUCCEEDED'
                ))) order by a.id desc limit 1) active_attempt_id
            from daily_career.learning_day_item i join daily_career.learning_day d on d.id=i.learning_day_id
            join daily_career.user_curriculum c on c.id=i.user_curriculum_id and c.user_id=i.user_id
            join daily_career.test_version v on v.id=i.test_version_id join daily_career.test t on t.id=v.test_id
            join daily_career.test_item ti on ti.test_version_id=v.id
            where i.user_id=? and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED')
              and v.status='PUBLISHED' and t.enabled and (t.owner_user_id is null or t.owner_user_id=?)
            group by t.id,t.title,t.test_type,v.id,v.version_no,v.time_limit_seconds,v.attempt_limit,v.pass_percent,i.user_curriculum_id,d.local_date,i.order_no
            """;}
    private Map<String,Object> examRow(long user,long test){var rows=jdbc.queryForList(baseExamSql()+" having t.id=?",user,user,user,user,test);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();}
    private Exam exam(Map<String,Object> r){LocalDate date=date(r,"local_date");int limit=r.get("attempt_limit")==null?100:number(r,"attempt_limit");return new Exam(id(r,"test_id"),id(r,"curriculum_id"),(String)r.get("title"),"정해진 시간 안에 답안을 저장하고 제출하세요.",(String)r.get("test_type"),number(r,"problem_count"),(int)Math.ceil(number(r,"time_limit_seconds")/60d),decimal(r,"pass_percent"),date,date.atStartOfDay(SEOUL).toOffsetDateTime(),date.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime(),number(r,"attempt_count"),limit,nullableId(r,"active_attempt_id"),"test-"+id(r,"test_id")+"-r"+number(r,"version_no"));}
    private String publicStatus(Exam e){if(e.attemptCount()>0&&e.activeAttemptId()==null)return "COMPLETED";OffsetDateTime n=now();return n.isBefore(e.availableFrom())?"UPCOMING":n.isBefore(e.availableUntil())?"AVAILABLE":"COMPLETED";}
    private Map<String,Object> attemptRow(long user,long attempt){var rows=jdbc.queryForList("""
            select a.*,v.test_id,v.version_no,v.pass_percent,t.title from daily_career.test_attempt a
            join daily_career.test_version v on v.id=a.test_version_id join daily_career.test t on t.id=v.test_id where a.id=? and a.user_id=?
            """,attempt,user);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();}
    private Map<String,Object> lockAttempt(long user,long attempt){var rows=jdbc.queryForList("select * from daily_career.test_attempt where id=? and user_id=? for update",attempt,user);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();}
    private Attempt attemptView(Map<String,Object> a){long attempt=id(a,"id");var items=jdbc.queryForList("select * from daily_career.test_attempt_item where test_attempt_id=? order by display_order",attempt);String grading=gradingStatus(attempt);OffsetDateTime n=now(),deadline=time(a,"deadline_at");boolean open="IN_PROGRESS".equals(a.get("status"))&&n.isBefore(deadline);
        var problems=items.stream().map(this::problem).toList();return new Attempt(attempt,id(a,"test_id"),number(a,"attempt_no"),publicAttemptStatus(a,grading),grading,gradingError(attempt),(String)a.get("submit_reason"),time(a,"started_at"),deadline,time(a,"submitted_at"),completedAt(attempt),open,"IN_PROGRESS".equals(a.get("status"))&&!n.isBefore(deadline),Math.max(0,Duration.between(n,deadline).toSeconds()),(int)problems.stream().filter(p->p.answer()!=null).count(),problems.size(),"test-"+id(a,"test_id")+"-r"+number(a,"version_no"),problems);}
    private Problem problem(Map<String,Object> r){JsonNode q=tree(Objects.toString(r.get("question_snapshot"))),g=tree(Objects.toString(r.get("grading_snapshot")));List<Option> options=new ArrayList<>();q.path("options").forEach(o->options.add(new Option(o.path("optionNo").asInt(),o.path("text").asText())));return new Problem(Long.valueOf(q.path("problemId").asText()),number(r,"display_order"),q.path("category").asText(),q.path("type").asText(),q.path("question").asText(),options,g.path("points").asInt(),answer(r),number(r,"revision"),time(r,"answered_at"));}
    private ResultProblem resultProblem(Map<String,Object> r){JsonNode q=tree((String)r.get("question_snapshot")),g=tree((String)r.get("grading_snapshot"));return new ResultProblem(Long.valueOf(q.path("problemId").asText()),q.path("category").asText(),q.path("question").asText(),(String)r.get("answer"),correctAnswer(q,g),Boolean.TRUE.equals(r.get("is_correct")),integer(r,"max_score"),integer(r,"earned_score"),g.path("explanation").asText());}
    private ArrayNode optionSnapshot(String type,String choices){ArrayNode out=json.createArrayNode();if("TRUE_FALSE".equals(type))return out;JsonNode source=tree(choices);for(int i=0;i<source.size();i++)out.addObject().put("optionNo",i+1).put("text",source.get(i).path("text").asText());return out;}
    private void validateAnswer(String question,String answer){if(answer==null)return;JsonNode q=tree(question);if("TRUE_FALSE".equals(q.path("type").asText())){if(!Set.of("TRUE","FALSE").contains(answer))throw new ApiException(TEST_INVALID_ANSWER);return;}if(!answer.matches("[1-6]")||Integer.parseInt(answer)>q.path("options").size())throw new ApiException(TEST_INVALID_ANSWER);}
    private String correctAnswer(JsonNode q,JsonNode g){JsonNode key=g.path("answerKey");if("TRUE_FALSE".equals(q.path("type").asText()))return key.path("correct").asText();String wanted=key.path("correct").asText();JsonNode options=q.path("options");JsonNode raw=treeForGrading(g);for(int i=0;i<raw.size();i++)if(raw.get(i).path("id").asText(raw.get(i).path("key").asText()).equals(wanted))return Integer.toString(i+1);return wanted;}
    private JsonNode treeForGrading(JsonNode g){return g.path("choices");}
    private String gradingStatus(long attempt){var rows=jdbc.queryForList("select status from daily_career.grading_run where test_attempt_id=? order by run_no desc limit 1",String.class,attempt);if(rows.isEmpty())return "WAITING";String status=rows.getFirst();return "QUEUED".equals(status)?"WAITING":status;}
    private String gradingError(long attempt){var rows=jdbc.queryForList("select error_code from daily_career.grading_run where test_attempt_id=? order by run_no desc limit 1",String.class,attempt);return rows.isEmpty()?null:rows.getFirst();}
    private OffsetDateTime completedAt(long attempt){var rows=jdbc.queryForList("select finished_at from daily_career.grading_run where test_attempt_id=? and is_current",OffsetDateTime.class,attempt);return rows.isEmpty()?null:rows.getFirst().withOffsetSameInstant(ZoneOffset.ofHours(9));}
    private String publicAttemptStatus(Map<String,Object> r,String grading){if("SUCCEEDED".equals(grading))return "COMPLETED";return (String)r.get("status");}
    private static String publicType(String t){return "TRUE_FALSE".equals(t)?"TRUE_FALSE":"MULTIPLE_CHOICE";}
    private static BigDecimal percent(int earned,int total){return BigDecimal.valueOf(earned).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);}
    private void lockUser(long user){jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);users.current(user);}
    private long insert(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private JsonNode tree(String value){try{return json.readTree(value);}catch(Exception e){throw new ApiException(CONTENT_NOT_READY);}}
    private static String answer(Map<String,Object> r){Object value=r.get("answer_payload");String s=value==null?null:value.toString();if(s==null||s.equals("{}"))return null;int p=s.indexOf("\"answer\":");if(p<0)return null;String v=s.substring(p+9).replaceAll("^[\\s\"]+|[\\s\"}]+$","");return v.isEmpty()?null:v;}
    private OffsetDateTime now(){return OffsetDateTime.now(clock).atZoneSameInstant(SEOUL).toOffsetDateTime();}
    private static LocalDate date(Map<String,Object> r,String k){Object v=r.get(k);return v instanceof java.sql.Date d?d.toLocalDate():(LocalDate)v;}
    private static OffsetDateTime time(Map<String,Object> r,String k){Object v=r.get(k);if(v==null)return null;if(v instanceof java.sql.Timestamp t)return t.toInstant().atZone(SEOUL).toOffsetDateTime();return ((OffsetDateTime)v).withOffsetSameInstant(SEOUL.getRules().getOffset(Instant.now()));}
    private static long id(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();} private static Long nullableId(Map<String,Object> r,String k){return r.get(k)==null?null:id(r,k);} private static int number(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();} private static int integer(Map<String,Object> r,String k){return new BigDecimal(r.get(k).toString()).setScale(0,RoundingMode.UNNECESSARY).intValueExact();} private static BigDecimal decimal(Map<String,Object> r,String k){return new BigDecimal(r.get(k).toString());}
}
