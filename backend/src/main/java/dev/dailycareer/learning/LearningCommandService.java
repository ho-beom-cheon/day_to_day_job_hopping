package dev.dailycareer.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.*;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.learning.LearningCatalog.*;
import static dev.dailycareer.learning.LearningQueryService.date;
import static dev.dailycareer.learning.LearningViews.*;

@Service
public class LearningCommandService {
    public record Policy(@NotNull @Min(15) @Max(480) Integer dailyStudyMinutes,
                         @NotNull @Min(0) @Max(6) Integer restDaysPerWeek,
                         @NotNull @Size(max=6) List<@NotNull DayOfWeek> restWeekdays) {}
    public record Create(@NotNull @JsonId Long templateId,@NotNull @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String startDate,
                         @NotNull @Valid Policy studyPolicy) {}
    public record Complete(@NotNull @Min(1) @Max(480) Integer actualMinutes) {}
    private final JdbcTemplate jdbc; private final UserAccountService users; private final LearningCatalog catalog;
    private final LearningQueryService queries; private final IdempotencyService idempotency; private final ObjectMapper json;
    public LearningCommandService(JdbcTemplate jdbc,UserAccountService users,LearningCatalog catalog,LearningQueryService queries,IdempotencyService idempotency,ObjectMapper json) {
        this.jdbc=jdbc;this.users=users;this.catalog=catalog;this.queries=queries;this.idempotency=idempotency;this.json=json;
    }

    @Transactional
    public StoredReply create(long user,Create input,UUID key) {
        users.current(user);
        LocalDate start=parseDate(input.startDate());
        Policy p=input.studyPolicy();
        if(p.restWeekdays().size()!=p.restDaysPerWeek() || new HashSet<>(p.restWeekdays()).size()!=p.restWeekdays().size()) throw new ApiException(VALIDATION_FAILED);
        return idempotency.execute(user,"CURR-008",key,json.valueToTree(input),()->{
            lockUser(user);
            if(start.isBefore(queries.today()) || start.isAfter(queries.today().plusDays(90))) throw new ApiException(VALIDATION_FAILED);
            if(jdbc.queryForObject("select count(*) from daily_career.user_curriculum where user_id=? and status in ('PLANNED','ACTIVE','PAUSED')",Integer.class,user)>0)
                throw new ApiException(CURRICULUM_ALREADY_ACTIVE);
            jdbc.queryForList("select id from daily_career.curriculum_version where id=? for update",input.templateId());
            Plan plan=catalog.plan(input.templateId(),true);
            var schedule=new LinkedHashMap<LocalDate,Day>();LocalDate date=start;
            for(Day d:plan.days()) {
                while(p.restWeekdays().contains(date.getDayOfWeek())) date=date.plusDays(1);
                if(!date.isBefore(start.plusDays(366))) throw new ApiException(RESCHEDULE_OUT_OF_RANGE);
                schedule.put(date,d);date=date.plusDays(1);
            }
            LocalDate end=date.minusDays(1);
            long course=insert("""
                    insert into daily_career.user_curriculum(user_id,curriculum_version_id,start_date,target_end_date,projected_end_date,status,schedule_revision)
                    values (?,?,?,?,?,?,1) returning id
                    """,user,plan.id(),start,start.plusMonths(6).minusDays(1),end,start.isAfter(queries.today())?"PLANNED":"ACTIVE");
            long policy=insert("""
                    insert into daily_career.schedule_policy(user_id,user_curriculum_id,effective_from,weekly_rest_count,study_minutes)
                    values (?,?,?,?,?) returning id
                    """,user,course,start,p.restDaysPerWeek(),p.dailyStudyMinutes());
            for(DayOfWeek rest:p.restWeekdays()) jdbc.update("insert into daily_career.schedule_rest_weekday(schedule_policy_id,iso_weekday) values (?,?)",policy,rest.getValue());
            for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)) {
                Day definition=schedule.get(day);
                long assignedDay=insert("""
                        insert into daily_career.learning_day(user_id,user_curriculum_id,local_date,day_type,source,schedule_policy_id,planned_minutes)
                        values (?,?,?,?,'POLICY',?,?) returning id
                        """,user,course,day,definition==null?"REST":"STUDY",policy,definition==null?0:p.dailyStudyMinutes());
                if(definition==null)continue;
                jdbc.update("""
                        insert into daily_career.assigned_learning_day(learning_day_id,user_id,curriculum_unit_id,day_no,month_no,week_no,title,description)
                        values (?,?,?,?,?,?,?,?)
                        """,assignedDay,user,definition.id(),definition.number(),definition.month(),definition.week(),definition.title(),definition.description());
                for(Item item:definition.items()) {
                    Long content=item.contents().isEmpty()?null:item.contents().getFirst().versionId();
                    long session=insert("""
                            insert into daily_career.learning_day_item(user_id,user_curriculum_id,curriculum_version_id,curriculum_unit_id,learning_day_id,
                              order_no,activity_type,content_version_id,test_version_id,content_status,required)
                            values (?,?,?,?,?,?,?,?,?,'READY',?) returning id
                            """,user,course,plan.id(),item.id(),assignedDay,item.sequence(),item.activity(),content,item.testVersionId(),item.required());
                    // TEST assignments are pinned here; test-taking endpoints belong to stage 9.
                    if(item.testVersionId()!=null)continue;
                    jdbc.update("""
                            insert into daily_career.assigned_session_detail(learning_day_item_id,user_id,title,description,category,estimated_minutes)
                            values (?,?,?,?,?,?)
                            """,session,user,item.title(),item.description(),item.category(),item.minutes());
                    for(Content c:item.contents()) jdbc.update("""
                            insert into daily_career.assigned_content(user_id,learning_day_item_id,content_version_id,sequence,required,content_type,completion_rule)
                            values (?,?,?,?,?,?,?)
                            """,user,session,c.versionId(),c.sequence(),c.required(),c.type(),c.rule());
                }
            }
            event(user,"CURRICULUM_ASSIGNED",course,1,Map.of("curriculumId",Long.toString(course)));
            var result=queries.curriculum(user,course);
            return new StoredReply(201,json.valueToTree(result),Revisions.etag(result.scheduleRevision()),"/api/v1/curriculums/"+course);
        });
    }

    @Transactional
    public Session start(long user,long session) {
        var row=lockSession(user,session);
        if("COMPLETED".equals(row.get("status")))throw new ApiException(SESSION_ALREADY_COMPLETED);
        if(queries.today().isBefore(date(row,"local_date")))throw new ApiException(LEARNING_NOT_AVAILABLE);
        if(!"READY".equals(row.get("content_status")))throw new ApiException(CONTENT_NOT_READY);
        if("NOT_STARTED".equals(row.get("status"))) {
            bumpSession(session);
            jdbc.update("update daily_career.learning_day_item set status='IN_PROGRESS',started_at=clock_timestamp() where id=?",session);
            bumpCourse(id(row,"user_curriculum_id"));
        }
        return queries.session(user,session);
    }

    @Transactional
    public ContentCompleteResult completeContent(long user,long content) {
        var before=queries.content(user,content); var row=lockSession(user,before.sessionId());
        var current=queries.content(user,content);
        if(current.completedAt()==null) {
            if(!"IN_PROGRESS".equals(row.get("status")))throw new ApiException(SESSION_NOT_STARTED);
            if(current.completionRule().equals("ALL_PROBLEMS_ATTEMPTED")) {
                int missing=jdbc.queryForObject("""
                        select count(*) from daily_career.learning_content_problem cp where cp.content_version_id=? and not exists (
                            select 1 from daily_career.problem_attempt a where a.problem_version_id=cp.problem_version_id
                            and a.user_id=? and a.user_curriculum_id=? and a.status='SUBMITTED')
                        """,Integer.class,current.contentVersionId(),user,id(row,"user_curriculum_id"));
                if(missing>0)throw new ApiException(CONTENT_PROBLEMS_INCOMPLETE);
            }
            jdbc.update("update daily_career.assigned_content set completed_at=clock_timestamp() where id=? and user_id=? and completed_at is null",content,user);
            bumpSession(before.sessionId());
        }
        var result=queries.content(user,content);var session=queries.session(user,before.sessionId());
        return new ContentCompleteResult(content,"COMPLETED",result.completedAt(),session.requiredContentCount(),session.completedRequiredContentCount());
    }

    @Transactional
    public StoredReply complete(long user,long session,Complete input,UUID key) {
        queries.session(user,session); // Replays also require current account status and resource ownership.
        return idempotency.execute(user,"SESSION-003",key,json.valueToTree(Map.of("sessionId",Long.toString(session),"body",input)),()->{
            var row=lockSession(user,session);var before=queries.session(user,session);
            if(before.status().equals("COMPLETED")) {
                if(!before.actualMinutes().equals(input.actualMinutes()))throw new ApiException(SESSION_ALREADY_COMPLETED);
                return completedReply(user,session);
            }
            if(!before.status().equals("IN_PROGRESS"))throw new ApiException(SESSION_NOT_STARTED);
            if(!before.requiredContentCount().equals(before.completedRequiredContentCount()))throw new ApiException(SESSION_CONTENT_INCOMPLETE);
            int dayMinutes=jdbc.queryForObject("""
                    select coalesce(sum(s.actual_minutes),0) from daily_career.assigned_session_detail s
                    join daily_career.learning_day_item i on i.id=s.learning_day_item_id where i.learning_day_id=?
                    """,Integer.class,before.learningDayId());
            if(dayMinutes+input.actualMinutes()>10000)throw new ApiException(VALIDATION_FAILED);
            bumpSession(session);
            jdbc.update("update daily_career.learning_day_item set status='COMPLETED',completed_at=clock_timestamp() where id=?",session);
            jdbc.update("update daily_career.assigned_session_detail set actual_minutes=? where learning_day_item_id=?",input.actualMinutes(),session);
            long course=id(row,"user_curriculum_id");bumpCourse(course);
            var curriculum=queries.curriculum(user,course);
            if(curriculum.status().equals("COMPLETED")) jdbc.update("update daily_career.user_curriculum set status='COMPLETED',completed_at=coalesce(completed_at,clock_timestamp()) where id=?",course);
            var after=queries.session(user,session);
            event(user,"SESSION_COMPLETED",session,after.revision(),Map.of("sessionId",Long.toString(session),"curriculumId",Long.toString(course),"actualMinutes",input.actualMinutes()));
            var day=queries.day(user,course,after.scheduledDate());
            if(day.status().equals("COMPLETED"))event(user,"LEARNING_DAY_COMPLETED",day.learningDayId(),curriculum.scheduleRevision(),Map.of("learningDayId",day.learningDayId().toString()));
            if(curriculum.status().equals("COMPLETED"))event(user,"CURRICULUM_COMPLETED",course,curriculum.scheduleRevision(),Map.of("curriculumId",Long.toString(course)));
            return completedReply(user,session);
        });
    }
    private StoredReply completedReply(long user,long session) {
        var s=queries.session(user,session);var d=queries.day(user,s.curriculumId(),s.scheduledDate());
        var result=new SessionCompleteResult(s,new DayProgress(d.learningDayId(),d.progressRate(),d.completedSessionCount(),d.totalSessionCount(),d.status().equals("COMPLETED")));
        return new StoredReply(200,json.valueToTree(result),null,null);
    }
    private Map<String,Object> lockSession(long user,long session) {
        lockUser(user);var row=queries.sessionRow(user,session);
        jdbc.queryForList("select id from daily_career.user_curriculum where id=? and user_id=? for update",id(row,"user_curriculum_id"),user);
        jdbc.queryForList("select id from daily_career.learning_day_item where id=? and user_id=? for update",session,user);
        return queries.sessionRow(user,session);
    }
    private void lockUser(long user) {
        // Idempotency inserts hold FK KEY SHARE locks on this row. NO KEY UPDATE serializes
        // user operations without a mutual lock-upgrade deadlock between different keys.
        jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);users.current(user);
    }
    private void bumpSession(long session) {
        if(jdbc.update("update daily_career.learning_day_item set revision=revision+1,updated_at=clock_timestamp() where id=? and revision<2147483647",session)!=1)
            throw new ApiException(PRECONDITION_FAILED);
    }
    private void bumpCourse(long course) {
        if(jdbc.update("update daily_career.user_curriculum set schedule_revision=schedule_revision+1,revision=revision+1,updated_at=clock_timestamp() where id=? and schedule_revision<2147483647",course)!=1)
            throw new ApiException(PRECONDITION_FAILED);
    }
    private long insert(String sql,Object...args) {return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private void event(long user,String type,long aggregate,int revision,Map<String,Object> payload) {
        jdbc.update("""
                insert into daily_career.outbox_event(user_id,event_key,event_type,aggregate_type,aggregate_id,aggregate_revision,payload,available_at)
                values (?,?,?,?,?,?,?::jsonb,clock_timestamp()) on conflict(event_key) do nothing
                """,user,type+":"+aggregate,type,type.replace("_COMPLETED","").replace("_ASSIGNED",""),aggregate,revision,json.valueToTree(payload).toString());
    }
    public static LocalDate parseDate(String text) {
        try {if(text==null || !text.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();return LocalDate.parse(text);}
        catch(RuntimeException e){throw new ApiException(VALIDATION_FAILED);}
    }
}
