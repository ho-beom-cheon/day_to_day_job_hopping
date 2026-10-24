package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.dailycareer.common.api.*;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.learning.LearningQueryService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
public class StudyEvaluationService {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final AiService ai;private final LearningQueryService learning;
    StudyEvaluationService(JdbcTemplate jdbc,ObjectMapper json,AiService ai,LearningQueryService learning) {
        this.jdbc=jdbc;this.json=json;this.ai=ai;this.learning=learning;
    }
    record SourceIssue(String sourceId,String title,List<String> reasons) {}
    record Scope(String kind,String key,String title,List<StudyPolicy.Goal> goals,List<StudyAssessment.Source> sources,
                 boolean available,String replacement,int sourceCount,int extraCount,List<SourceIssue> sourceIssues) {}
    private long course(long user) {Long id=learning.currentId(user);if(id==null)throw new ApiException(CONTENT_NOT_READY);return id;}
    private void lock(long user) {jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);}
    private List<Scope> scopes(long user,long course) {
        var rows=jdbc.queryForList("""
            select c.*,v.id version_id,evidence.source_version,evidence.body_markdown,evidence.reviewed,evidence.published,
              detail.title,detail.learning_objective,detail.key_points,subject.code subject,
              evidence.ready
            from daily_career.study_completion c
            join daily_career.assigned_content a on a.id=c.assigned_content_id and a.user_id=c.user_id
            join daily_career.learning_content_version v on v.id=a.content_version_id
            join daily_career.study_source_readiness evidence on evidence.content_version_id=v.id
            join daily_career.learning_content_detail detail on detail.content_version_id=v.id
            join daily_career.learning_content content on content.id=v.learning_content_id
            join daily_career.subject subject on subject.id=content.subject_id
            where c.user_id=? and c.course_id=? and a.completed_at is not null
              and (content.owner_user_id is null or content.owner_user_id=c.user_id)
            order by c.studied_on,c.assigned_content_id
            """,user,course);
        var days=jdbc.queryForList("""
            select a.*,d.local_date,
              not exists(select 1 from daily_career.learning_day_item i
                join daily_career.assigned_content c on c.learning_day_item_id=i.id
                where i.learning_day_id=d.id and i.required and c.required and c.completed_at is null) learned
            from daily_career.assigned_learning_day a join daily_career.learning_day d on d.id=a.learning_day_id
            where d.user_id=? and d.user_curriculum_id=? order by a.day_no
            """,user,course);
        var grouped=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var row:rows)for(String key:List.of("DAILY:"+row.get("studied_on"),"WEEKLY:"+row.get("week_no"),"MONTHLY:"+row.get("month_no"),"FINAL:all"))
            grouped.computeIfAbsent(key,k->new ArrayList<>()).add(row);
        var result=new ArrayList<Scope>();
        grouped.forEach((id,items)->{
            String[] tokens=id.split(":",2);String kind=tokens[0],key=tokens[1];
            var goals=new LinkedHashMap<String,StudyPolicy.Goal>();var sources=new LinkedHashMap<String,StudyAssessment.Source>();
            var extras=new HashSet<String>();var issues=new ArrayList<SourceIssue>();
            for(var row:items) {
                String source="v"+row.get("version_id");if(Boolean.TRUE.equals(row.get("extra")))extras.add(source);
                if(sources.containsKey(source))continue;
                var points=StudyAssessment.strings(read(row.get("key_points")));if(points.isEmpty())points=List.of(row.get("learning_objective").toString());
                var ids=new ArrayList<String>();int n=0;
                for(String point:new LinkedHashSet<>(points)) {
                    String goal=source+":g"+(++n);ids.add(goal);
                    goals.put(goal,new StudyPolicy.Goal(goal,row.get("subject").toString(),point,number(row,"week_no"),Boolean.TRUE.equals(row.get("extra"))));
                }
                sources.put(source,new StudyAssessment.Source(source,row.get("source_version").toString(),row.get("body_markdown").toString(),ids,
                        Boolean.TRUE.equals(row.get("reviewed")),Boolean.TRUE.equals(row.get("published")),""));
                if(!Boolean.TRUE.equals(row.get("ready"))) {
                    var reasons=new ArrayList<String>();
                    if(!Boolean.TRUE.equals(row.get("reviewed")))reasons.add("검수 기록 연결 필요");
                    if(!Boolean.TRUE.equals(row.get("published")))reasons.add("발행 상태 확인 필요");
                    if(row.get("body_markdown").toString().length()<200)reasons.add("원문·해설의 평가 근거 부족");
                    issues.add(new SourceIssue(source,row.get("title").toString(),reasons));
                }
            }
            boolean ready=true;String replacement="";
            if(!kind.equals("DAILY")) {
                var period=days.stream().filter(d->kind.equals("FINAL") || number(d,kind.equals("WEEKLY")?"week_no":"month_no")==Integer.parseInt(key)).toList();
                ready=!period.isEmpty() && (!date(period.getLast().get("local_date")).isAfter(learning.today()) || period.stream().allMatch(d->Boolean.TRUE.equals(d.get("learned"))));
                if(kind.equals("WEEKLY") && !period.isEmpty()) {
                    int month=number(period.getLast(),"month_no"),lastDay=number(period.getLast(),"day_no");
                    if(days.stream().filter(d->number(d,"month_no")==month).mapToInt(d->number(d,"day_no")).max().orElse(-1)==lastDay)replacement="MONTHLY";
                }
            } else {
                int day=number(items.getLast(),"day_no"),week=number(items.getLast(),"week_no"),month=number(items.getLast(),"month_no");
                if(days.stream().filter(d->number(d,"month_no")==month).mapToInt(d->number(d,"day_no")).max().orElse(-1)==day)replacement="MONTHLY";
                else if(days.stream().filter(d->number(d,"week_no")==week).mapToInt(d->number(d,"day_no")).max().orElse(-1)==day)replacement="WEEKLY";
            }
            String title=switch(kind){case "DAILY"->key+" 일간 평가";case "WEEKLY"->key+"주차 주간 평가";case "MONTHLY"->key+"개월차 월간 평가";default->"최종 종합 평가";};
            result.add(new Scope(kind,key,title,List.copyOf(goals.values()),List.copyOf(sources.values()),ready,replacement,sources.size(),extras.size(),List.copyOf(issues)));
        });
        return result;
    }
    private Set<String> reserved(long user,long course,String kind,String key) {
        return new HashSet<>(jdbc.queryForList("""
            select goal->>'id' from daily_career.study_evaluation e
            cross join lateral jsonb_array_elements(e.scope_snapshot->'goals') goal
            where e.user_id=? and e.course_id=? and e.kind=? and e.period_key=? and e.retake_of is null
            """,String.class,user,course,kind,key));
    }
    @Transactional(readOnly=true) public ObjectNode list(long user) {
        var out=json.createObjectNode();var scopeRows=out.putArray("scopes");var history=out.putArray("evaluations");
        Long course=learning.currentId(user);if(course==null)return out;
        int minutes=minutes(user,course);out.put("sessionMinutes",minutes);
        for(var scope:scopes(user,course)) {
            var used=reserved(user,course,scope.kind,scope.key);var fresh=scope.goals.stream().filter(g->!used.contains(g.id())).toList();
            var row=scopeRows.addObject().put("kind",scope.kind).put("key",scope.key).put("title",scope.title)
                    .put("objectiveCount",scope.goals.size()).put("newObjectiveCount",fresh.size()).put("sourceCount",scope.sourceCount).put("extraSourceCount",scope.extraCount)
                    .put("available",scope.available).put("replacement",scope.replacement);
            var wanted=new HashSet<String>();fresh.forEach(g->wanted.add(g.id()));
            var pendingSourceIds=new HashSet<String>();scope.sources.stream().filter(s->s.objectiveIds().stream().anyMatch(wanted::contains)).forEach(s->pendingSourceIds.add(s.id()));
            var issues=scope.sourceIssues.stream().filter(i->pendingSourceIds.contains(i.sourceId())).toList();
            row.put("sourceReady",issues.isEmpty());row.set("sourceIssues",json.valueToTree(issues));
            row.put("partCount",fresh.isEmpty()?0:StudyEvaluationPlan.split(StudyPolicy.Kind.valueOf(scope.kind),minutes,4,fresh).size());
        }
        for(var e:jdbc.queryForList("select * from daily_career.study_evaluation where user_id=? and course_id=? order by id desc",user,course))history.add(summary(e));
        return out;
    }
    private int minutes(long user,long course) {
        var values=jdbc.queryForList("select study_minutes from daily_career.schedule_policy where user_id=? and user_curriculum_id=? and effective_from<=? order by effective_from desc limit 1",Integer.class,user,course,learning.today());
        int value=values.isEmpty()?60:values.getFirst();return List.of(30,60,90,120).contains(value)?value:60;
    }
    @Transactional public ObjectNode create(long user,String kind,String key,Long retake,UUID requestKey) {
        lock(user);long course=course(user);
        var duplicate=jdbc.queryForList("select * from daily_career.study_evaluation where user_id=? and request_key=?",user,requestKey);
        if(!duplicate.isEmpty()) {
            var row=duplicate.getFirst();if(!Objects.equals(row.get("kind"),kind)||!Objects.equals(row.get("period_key"),key)||!Objects.equals(row.get("retake_of"),retake))throw new ApiException(VALIDATION_FAILED);
            return get(user,id(row));
        }
        ObjectNode snapshot;String title;
        if(retake!=null) {
            var original=evaluation(user,retake);if(numberLong(original,"course_id")!=course || !Objects.equals(original.get("kind"),kind)||!Objects.equals(original.get("period_key"),key))throw new ApiException(VALIDATION_FAILED);
            var unfinished=jdbc.queryForObject("select count(*) from daily_career.study_evaluation_part where evaluation_id=? and submitted_at is null",Integer.class,retake);
            if(unfinished!=null&&unfinished>0)throw new ApiException(TEST_ACTIVE_ATTEMPT_EXISTS);
            snapshot=(ObjectNode)read(original.get("scope_snapshot")).deepCopy();title=original.get("title").toString().replace(" · 재응시","")+" · 재응시";
        } else {
            var scope=scopes(user,course).stream().filter(s->s.kind.equals(kind)&&s.key.equals(key)).findFirst().orElseThrow(()->new ApiException(CONTENT_NOT_READY));
            if(!scope.available || !scope.replacement.isEmpty())throw new ApiException(TEST_NOT_AVAILABLE);
            var used=reserved(user,course,kind,key);var goals=scope.goals.stream().filter(g->!used.contains(g.id())).toList();
            if(goals.isEmpty())throw new ApiException(TEST_NOT_AVAILABLE);
            snapshot=json.createObjectNode();snapshot.set("goals",json.valueToTree(goals));
            var wanted=new HashSet<String>();goals.forEach(g->wanted.add(g.id()));
            var sources=scope.sources.stream().filter(s->s.objectiveIds().stream().anyMatch(wanted::contains)).toList();snapshot.set("sources",json.valueToTree(sources));
            snapshot.put("sessionMinutes",minutes(user,course));snapshot.put("difficulty",4);title=scope.title;
        }
        var goals=Arrays.asList(json.convertValue(snapshot.path("goals"),StudyPolicy.Goal[].class));
        var sources=Arrays.asList(json.convertValue(snapshot.path("sources"),StudyAssessment.Source[].class));
        var plans=StudyEvaluationPlan.split(StudyPolicy.Kind.valueOf(kind),snapshot.path("sessionMinutes").asInt(),snapshot.path("difficulty").asInt(),goals);
        try {plans.forEach(plan->StudyAssessment.validateSources(sources,plan));}catch(IllegalArgumentException e){throw new ApiException(CONTENT_NOT_READY);}
        long id=Objects.requireNonNull(jdbc.queryForObject("insert into daily_career.study_evaluation(user_id,course_id,kind,period_key,title,scope_snapshot,retake_of,request_key) values (?,?,?,?,?,?::jsonb,?,?) returning id",Long.class,user,course,kind,key,title,snapshot.toString(),retake,requestKey));
        int n=0;for(var plan:plans)jdbc.update("insert into daily_career.study_evaluation_part(evaluation_id,user_id,part_no,plan) values (?,?,?,?::jsonb)",id,user,++n,json.valueToTree(plan).toString());
        return get(user,id);
    }
    @Transactional(readOnly=true) public ObjectNode get(long user,long id) {
        var e=evaluation(user,id);var out=summary(e);out.set("objectives",read(e.get("scope_snapshot")).path("goals"));
        var parts=out.putArray("parts");boolean graded=true;int points=0,questions=0;
        for(var row:jdbc.queryForList("select * from daily_career.study_evaluation_part where evaluation_id=? and user_id=? order by part_no",id,user)) {
            var shown=partView(row);parts.add(shown);graded&="GRADED".equals(shown.path("status").asText());
            for(var result:shown.path("result").path("results")){points+=result.path("earnedPoints").asInt();questions++;}
        }
        if(graded&&questions>0)out.put("score",Math.round(points*1000.0/questions)/100.0);else out.putNull("score");
        return out;
    }
    private ObjectNode summary(Map<String,Object> e) {
        var out=json.createObjectNode().put("id",Long.toString(id(e))).put("title",e.get("title").toString()).put("kind",e.get("kind").toString()).put("key",e.get("period_key").toString())
                .put("objectiveCount",read(e.get("scope_snapshot")).path("goals").size()).put("createdAt",instant(e.get("created_at")).toString());
        var counts=jdbc.queryForMap("select count(*)::int total,count(*) filter(where submitted_at is not null)::int submitted from daily_career.study_evaluation_part where evaluation_id=?",id(e));
        out.put("partCount",number(counts,"total")).put("submittedPartCount",number(counts,"submitted"));return out;
    }
    private ObjectNode partView(Map<String,Object> p) {
        var out=json.createObjectNode().put("id",Long.toString(id(p))).put("partNo",number(p,"part_no")).put("revision",number(p,"revision"));
        out.set("plan",read(p.get("plan")));out.set("answers",read(p.get("answers")));out.put("serverNow",Instant.now().toString());
        var generated=job(p.get("generation_job_id"));String state="PENDING";
        if(generated!=null)state=switch(generated.get("status").toString()){case "SUCCEEDED"->"READY";case "QUEUED","RUNNING"->"GENERATING";default->"FAILED";};
        if(generated!=null && generated.get("error_code")!=null)out.put("errorCode",generated.get("error_code").toString());
        if(p.get("started_at")!=null) {
            state="ACTIVE";out.put("expiresAt",instant(p.get("expires_at")).toString());
            if(p.get("submitted_at")==null && Instant.now().isAfter(instant(p.get("expires_at"))))state="EXPIRED";
            var assessment=read(generated.get("result_payload"));out.set("questions",StudyAssessment.publicQuestions(json,assessment).path("questions"));
            if(p.get("submitted_at")!=null) {
                var grade=job(p.get("grading_job_id"));JsonNode result=grade!=null&&"SUCCEEDED".equals(grade.get("status"))?read(grade.get("result_payload")):
                        StudyAssessment.grade(json,assessment,StudyEvaluationContract.answers(read(p.get("answers"))),emptyGrades());
                state=result.path("status").asText();
                if(grade!=null && List.of("QUEUED","RUNNING").contains(grade.get("status")))state="GRADING";
                out.set("result",result);out.set("review",assessment.path("questions"));
                out.put("gradingRequested",grade!=null);if(grade!=null&&grade.get("error_code")!=null)out.put("errorCode",grade.get("error_code").toString());
            }
        }
        return out.put("status",state);
    }
    @Transactional public ObjectNode generate(long user,long evaluation,int part) {
        lock(user);var e=evaluation(user,evaluation);current(e,user);var row=part(user,evaluation,part);var existing=job(row.get("generation_job_id"));
        if(existing!=null&&List.of("QUEUED","RUNNING","SUCCEEDED").contains(existing.get("status")))return get(user,evaluation);
        var scope=read(e.get("scope_snapshot"));var plan=json.convertValue(read(row.get("plan")),StudyPolicy.Plan.class);
        var wanted=new HashSet<String>();plan.slots().forEach(s->wanted.addAll(s.objectiveIds()));
        var sources=Arrays.stream(json.convertValue(scope.path("sources"),StudyAssessment.Source[].class)).filter(s->s.objectiveIds().stream().anyMatch(wanted::contains)).toList();
        var input=StudyRequest.assessment(json,plan,Arrays.asList(json.convertValue(scope.path("goals"),StudyPolicy.Goal[].class)),sources,UUID.randomUUID());
        var stored=json.createObjectNode();stored.set("providerInput",input);
        var previous=jdbc.queryForList("""
            select q->>'prompt' from daily_career.study_evaluation_part p
            join daily_career.study_evaluation e on e.id=p.evaluation_id join daily_career.ai_job j on j.id=p.generation_job_id
            cross join lateral jsonb_array_elements(j.result_payload->'questions') q
            where e.user_id=? and e.course_id=? and e.kind=? and e.period_key=? and j.status='SUCCEEDED'
            """,String.class,user,e.get("course_id"),e.get("kind"),e.get("period_key"));
        stored.set("previousPrompts",json.valueToTree(previous));
        long job=ai.enqueueStudy(user,e.get("retake_of")==null?"STUDY_"+e.get("kind"):"STUDY_RETAKE",stored);
        jdbc.update("update daily_career.study_evaluation_part set generation_job_id=?,revision=revision+1 where id=?",job,id(row));return get(user,evaluation);
    }
    @Transactional public ObjectNode start(long user,long evaluation,int part) {
        lock(user);var e=evaluation(user,evaluation);current(e,user);var row=part(user,evaluation,part);
        if(row.get("started_at")!=null)return get(user,evaluation);
        Integer active=jdbc.queryForObject("select count(*) from daily_career.study_evaluation_part where user_id=? and started_at is not null and submitted_at is null",Integer.class,user);
        if(active!=null&&active>0)throw new ApiException(TEST_ACTIVE_ATTEMPT_EXISTS);
        var generated=job(row.get("generation_job_id"));if(generated==null||!"SUCCEEDED".equals(generated.get("status")))throw new ApiException(TEST_NOT_AVAILABLE);
        int seconds=read(row.get("plan")).path("assessmentSeconds").asInt();
        jdbc.update("update daily_career.study_evaluation_part set started_at=clock_timestamp(),expires_at=clock_timestamp()+(? * interval '1 second'),revision=revision+1 where id=?",seconds,id(row));return get(user,evaluation);
    }
    @Transactional public ObjectNode answer(long user,long evaluation,int part,JsonNode answers,int revision,boolean submit) {
        lock(user);var e=evaluation(user,evaluation);current(e,user);var row=part(user,evaluation,part);
        if(row.get("submitted_at")!=null) {if(submit)return get(user,evaluation);throw new ApiException(TEST_ATTEMPT_ALREADY_SUBMITTED);}
        if(row.get("started_at")==null)throw new ApiException(TEST_NOT_AVAILABLE);
        Revisions.requireMatch(revision,number(row,"revision"));
        boolean expired=!Instant.now().isBefore(instant(row.get("expires_at")));
        if(expired&&!submit)throw new ApiException(TEST_ATTEMPT_EXPIRED);
        if(expired)answers=read(row.get("answers"));
        try {
            var values=StudyEvaluationContract.answers(answers);var assessment=read(job(row.get("generation_job_id")).get("result_payload"));
            StudyRequest.grading(json,assessment,values);var known=new HashSet<String>();
            for(var q:assessment.path("questions")){String id=q.path("id").asText();known.add(id);if("MCQ".equals(q.path("type").asText())&&!values.getOrDefault(id,"").matches("[1-4]?"))throw new IllegalArgumentException();}
            if(!known.containsAll(values.keySet()))throw new IllegalArgumentException();
        }catch(IllegalArgumentException invalid){throw new ApiException(TEST_INVALID_ANSWER);}
        jdbc.update("update daily_career.study_evaluation_part set answers=?::jsonb,submitted_at="+(submit?"clock_timestamp()":"null")+",revision=revision+1 where id=?",answers.toString(),id(row));return get(user,evaluation);
    }
    @Transactional public ObjectNode grade(long user,long evaluation,int part) {
        lock(user);var e=evaluation(user,evaluation);current(e,user);var row=part(user,evaluation,part);
        if(row.get("submitted_at")==null)throw new ApiException(TEST_RESULT_NOT_READY);
        var existing=job(row.get("grading_job_id"));if(existing!=null&&List.of("QUEUED","RUNNING").contains(existing.get("status")))return get(user,evaluation);
        if(existing!=null&&"SUCCEEDED".equals(existing.get("status"))&&"GRADED".equals(read(existing.get("result_payload")).path("status").asText()))return get(user,evaluation);
        var assessment=read(job(row.get("generation_job_id")).get("result_payload"));var answers=read(row.get("answers"));
        var input=StudyRequest.grading(json,assessment,StudyEvaluationContract.answers(answers));if(input.path("questions").isEmpty())return get(user,evaluation);
        var snapshot=json.createObjectNode();snapshot.set("providerInput",input);snapshot.set("assessment",assessment);snapshot.set("answers",answers);
        long job=ai.enqueueStudy(user,"STUDY_GRADING",snapshot);
        jdbc.update("update daily_career.study_evaluation_part set grading_job_id=?,revision=revision+1 where id=?",job,id(row));return get(user,evaluation);
    }
    private void current(Map<String,Object> e,long user) {if(numberLong(e,"course_id")!=course(user))throw new ApiException(TEST_NOT_AVAILABLE);}
    private ObjectNode emptyGrades(){var empty=json.createObjectNode();empty.putArray("results");return empty;}
    private Map<String,Object> evaluation(long user,long id) {var rows=jdbc.queryForList("select * from daily_career.study_evaluation where id=? and user_id=?",id,user);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();}
    private Map<String,Object> part(long user,long evaluation,int part) {var rows=jdbc.queryForList("select * from daily_career.study_evaluation_part where evaluation_id=? and user_id=? and part_no=? for update",evaluation,user,part);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();}
    private Map<String,Object> job(Object id) {return id==null?null:jdbc.queryForMap("select * from daily_career.ai_job where id=?",id);}
    private JsonNode read(Object value) {try{return json.readTree(value.toString());}catch(Exception e){throw new IllegalStateException(e);}}
    private static long id(Map<String,Object> row){return numberLong(row,"id");}
    private static long numberLong(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static int number(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}
    private static LocalDate date(Object value){return value instanceof java.sql.Date d?d.toLocalDate():(LocalDate)value;}
    private static Instant instant(Object value){return value instanceof java.sql.Timestamp t?t.toInstant():((OffsetDateTime)value).toInstant();}
}
