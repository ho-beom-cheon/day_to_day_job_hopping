package dev.dailycareer.learning;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.learning.LearningCommandService.parseDate;
import static dev.dailycareer.learning.LearningQueryService.*;

@Service
public class ScheduleCommandService {
    enum Action { REST_ADD, REST_REMOVE, POLICY_APPLY, SHIFT_BACKLOG }
    enum RestReason { PERSONAL, VACATION, HEALTH, WORK, ETC }
    public record PreviewRequest(@NotNull String strategy,@NotNull Action action,
                                 @NotNull @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String baseDate,
                                 @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String date,
                                 RestReason reason,@Valid LearningCommandService.Policy studyPolicy) {}
    public record ApplyPreview(@NotNull @JsonId Long previewId) {}
    public record RestDayRequest(@NotNull @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String date,
                                 @NotNull RestReason reason,@NotNull @JsonId Long previewId) {}
    public record MovedDay(@JsonId Long learningDayId,LocalDate previousDate,LocalDate newDate) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReschedulePreview(@JsonId Long previewId,@JsonId Long curriculumId,Integer scheduleRevision,
                                    String strategy,String action,LocalDate baseDate,LocalDate targetDate,String reason,
                                    LearningViews.StudyPolicy studyPolicy,Integer affectedLearningDayCount,
                                    LocalDate previousEndDate,LocalDate newEndDate,List<MovedDay> moves,
                                    OffsetDateTime expiresAt,List<String> warnings) {}
    public record ScheduleChangeResult(@JsonId Long curriculumId,@JsonId Long previewId,Integer scheduleRevision,
                                       Integer affectedLearningDayCount,LocalDate previousEndDate,LocalDate newEndDate,
                                       OffsetDateTime appliedAt) {}
    public record RestChangeResult(@JsonId Long curriculumId,@JsonId Long previewId,Integer scheduleRevision,
                                   Integer affectedLearningDayCount,LocalDate previousEndDate,LocalDate newEndDate,
                                   OffsetDateTime appliedAt,LocalDate date,Boolean isRestDay) {}

    private final JdbcTemplate jdbc; private final LearningQueryService queries; private final UserAccountService users;
    private final IdempotencyService idempotency; private final ObjectMapper json; private final Clock clock;
    public ScheduleCommandService(JdbcTemplate jdbc,LearningQueryService queries,UserAccountService users,
                                  IdempotencyService idempotency,ObjectMapper json,Clock clock) {
        this.jdbc=jdbc;this.queries=queries;this.users=users;this.idempotency=idempotency;this.json=json;this.clock=clock;
    }

    @Transactional
    public StoredReply preview(long user,long course,PreviewRequest input,int expected,UUID key) {
        queries.curriculum(user,course);validate(input);
        JsonNode fingerprint=json.valueToTree(Map.of("curriculumId",Long.toString(course),"expected",expected,"body",input));
        return idempotency.execute(user,"CURR-009",key,fingerprint,()->{
            Course locked=lock(user,course);Revisions.requireMatch(expected,locked.revision());
            Plan plan=plan(user,locked,input);
            OffsetDateTime expires=OffsetDateTime.now(clock).plusMinutes(10);
            ReschedulePreview provisional=plan.view(null,course,locked.revision(),expires);
            long previewId=Objects.requireNonNull(jdbc.queryForObject("""
                    insert into daily_career.schedule_preview(user_id,user_curriculum_id,schedule_revision,action,request_snapshot,preview_snapshot)
                    values (?,?,?,?,?::jsonb,?::jsonb) returning id
                    """,Long.class,user,course,locked.revision(),input.action().name(),json.valueToTree(input).toString(),json.valueToTree(provisional).toString()));
            OffsetDateTime storedExpiry=jdbc.queryForObject("select expires_at from daily_career.schedule_preview where id=?",OffsetDateTime.class,previewId);
            ReschedulePreview result=plan.view(previewId,course,locked.revision(),storedExpiry);
            jdbc.update("update daily_career.schedule_preview set preview_snapshot=?::jsonb where id=?",json.valueToTree(result).toString(),previewId);
            return new StoredReply(201,json.valueToTree(result),null,null);
        });
    }

    @Transactional
    public StoredReply apply(long user,long course,long previewId,int expected,UUID key) {
        queries.curriculum(user,course);
        return confirm(user,course,previewId,expected,key,"CURR-007",EnumSet.of(Action.POLICY_APPLY,Action.SHIFT_BACKLOG),null,null);
    }
    @Transactional
    public StoredReply addRest(long user,RestDayRequest input,int expected,UUID key) {
        var current=queries.current(user);if(current==null)throw new ApiException(RESOURCE_NOT_FOUND);
        return confirm(user,current.curriculumId(),input.previewId(),expected,key,"REST-001",EnumSet.of(Action.REST_ADD),parseDate(input.date()),input.reason());
    }
    @Transactional
    public StoredReply removeRest(long user,LocalDate target,long previewId,int expected,UUID key) {
        var current=queries.current(user);if(current==null)throw new ApiException(RESOURCE_NOT_FOUND);
        return confirm(user,current.curriculumId(),previewId,expected,key,"REST-002",EnumSet.of(Action.REST_REMOVE),target,null);
    }

    private StoredReply confirm(long user,long course,long previewId,int expected,UUID key,String operation,Set<Action> allowed,LocalDate target,RestReason reason) {
        JsonNode fingerprint=json.valueToTree(Map.of("curriculumId",Long.toString(course),"previewId",Long.toString(previewId),"expected",expected,
                "target",target==null?"":target.toString(),"reason",reason==null?"":reason.name()));
        return idempotency.execute(user,operation,key,fingerprint,()->{
            Course locked=lock(user,course);Revisions.requireMatch(expected,locked.revision());
            PreviewRow stored=previewRow(user,course,previewId);
            if(stored.consumed())throw new ApiException(RESCHEDULE_PREVIEW_CONSUMED);
            if(stored.expired())throw new ApiException(RESCHEDULE_PREVIEW_EXPIRED);
            if(stored.revision()!=locked.revision() || !allowed.contains(stored.action()))throw new ApiException(RESCHEDULE_PREVIEW_MISMATCH);
            ReschedulePreview preview=read(stored.snapshot(),ReschedulePreview.class);
            if(target!=null && (!target.equals(preview.targetDate()) || (reason!=null && !reason.name().equals(preview.reason()))))
                throw new ApiException(RESCHEDULE_PREVIEW_MISMATCH);
            PreviewRequest request=read(stored.request(),PreviewRequest.class);
            Plan recalculated=plan(user,locked,request);
            if(!samePlan(preview,recalculated))throw new ApiException(RESCHEDULE_PREVIEW_MISMATCH);
            applyPlan(user,locked,recalculated,request);
            if(jdbc.update("update daily_career.schedule_preview set consumed_at=clock_timestamp() where id=? and consumed_at is null and expires_at>clock_timestamp()",previewId)!=1)
                throw new ApiException(RESCHEDULE_PREVIEW_CONSUMED);
            int next=locked.revision()+1;
            jdbc.update("""
                    insert into daily_career.schedule_change(user_id,user_curriculum_id,from_revision,to_revision,reason_code,before_snapshot,after_snapshot)
                    values (?,?,?,?,?,?::jsonb,?::jsonb)
                    """,user,course,locked.revision(),next,request.action().name(),stored.snapshot(),json.valueToTree(recalculated.view(previewId,course,next,preview.expiresAt())).toString());
            if(jdbc.update("update daily_career.user_curriculum set projected_end_date=?,schedule_revision=?,revision=revision+1,updated_at=clock_timestamp() where id=? and schedule_revision=? and schedule_revision<2147483647",
                    recalculated.newEnd(),next,course,locked.revision())!=1)throw new ApiException(PRECONDITION_FAILED);
            OffsetDateTime applied=OffsetDateTime.now(clock);
            Object result=target==null?new ScheduleChangeResult(course,previewId,next,recalculated.moves().size(),recalculated.previousEnd(),recalculated.newEnd(),applied):
                    new RestChangeResult(course,previewId,next,recalculated.moves().size(),recalculated.previousEnd(),recalculated.newEnd(),applied,target,request.action()==Action.REST_ADD);
            return new StoredReply(200,json.valueToTree(result),Revisions.etag(next),null);
        });
    }

    private Plan plan(long user,Course course,PreviewRequest request) {
        LocalDate base=parseDate(request.baseDate());LocalDate target=request.date()==null?null:parseDate(request.date());
        if(base.isBefore(queries.today()) || (target!=null && !target.equals(base)))throw new ApiException(VALIDATION_FAILED);
        List<DayRow> all=jdbc.query("""
                select d.id,d.local_date,a.day_no,d.source,d.day_type,
                  exists(select 1 from daily_career.learning_day_item i where i.learning_day_id=d.id and
                    (i.status<>'NOT_STARTED' or (i.test_version_id is not null and exists(select 1 from daily_career.test_attempt t where t.user_id=i.user_id and t.user_curriculum_id=i.user_curriculum_id and t.test_version_id=i.test_version_id)))) locked
                from daily_career.learning_day d join daily_career.assigned_learning_day a on a.learning_day_id=d.id
                where d.user_id=? and d.user_curriculum_id=? order by a.day_no
                """,(r,n)->new DayRow(r.getLong("id"),r.getObject("local_date",LocalDate.class),r.getInt("day_no"),r.getString("source"),r.getBoolean("locked")),user,course.id());
        if(all.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);
        if(target!=null && request.action()==Action.REST_ADD && all.stream().anyMatch(d->d.date().equals(target)&&d.locked()))throw new ApiException(RESCHEDULE_LOCKED_DAY);
        Set<LocalDate> manualRest=new HashSet<>(jdbc.queryForList("select local_date from daily_career.learning_day where user_id=? and user_curriculum_id=? and source='MANUAL' and day_type='REST'",LocalDate.class,user,course.id()));
        Set<LocalDate> manualStudy=new HashSet<>(jdbc.queryForList("select local_date from daily_career.learning_day where user_id=? and user_curriculum_id=? and source='MANUAL' and day_type='STUDY'",LocalDate.class,user,course.id()));
        if(request.action()==Action.REST_ADD) {manualRest.add(target);manualStudy.remove(target);}
        if(request.action()==Action.REST_REMOVE) {manualRest.remove(target);manualStudy.add(target);}
        if(target!=null && request.action()==Action.REST_REMOVE) {
            boolean rest=jdbc.queryForObject("select count(*)>0 from daily_career.learning_day where user_id=? and user_curriculum_id=? and local_date=? and day_type='REST'",Boolean.class,user,course.id(),target);
            if(!rest)throw new ApiException(VALIDATION_FAILED);
        }
        LearningCommandService.Policy policy=request.action()==Action.POLICY_APPLY?request.studyPolicy():course.policy();
        Set<DayOfWeek> restWeekdays=new HashSet<>(policy.restWeekdays());
        Set<LocalDate> occupied=new HashSet<>();
        for(DayRow d:all)if(d.date().isBefore(base)||d.locked())occupied.add(d.date());
        List<DayRow> movable=all.stream().filter(d->!d.date().isBefore(base)&&!d.locked()).toList();
        List<MovedDay> moves=new ArrayList<>();LocalDate cursor=base;
        for(DayRow d:movable) {
            while(occupied.contains(cursor)||manualRest.contains(cursor)||(!manualStudy.contains(cursor)&&restWeekdays.contains(cursor.getDayOfWeek())))cursor=cursor.plusDays(1);
            if(!cursor.isBefore(course.start().plusDays(366)))throw new ApiException(RESCHEDULE_OUT_OF_RANGE);
            if(!d.date().equals(cursor))moves.add(new MovedDay(d.id(),d.date(),cursor));
            occupied.add(cursor);cursor=cursor.plusDays(1);
        }
        Map<Long,LocalDate> destinations=new HashMap<>();for(DayRow d:all)destinations.put(d.id(),d.date());for(MovedDay m:moves)destinations.put(m.learningDayId(),m.newDate());
        LocalDate newEnd=destinations.values().stream().max(LocalDate::compareTo).orElse(course.end());
        return new Plan(request,policy,manualRest,manualStudy,all,destinations,course.end(),newEnd,moves);
    }

    private void applyPlan(long user,Course course,Plan plan,PreviewRequest request) {
        long policyId=course.policyId();
        if(request.action()==Action.POLICY_APPLY)policyId=applyPolicy(user,course,plan,request.studyPolicy());
        LocalDate temporary=course.start().plusDays(500);
        for(DayRow d:plan.days())if(!Objects.equals(d.date(),plan.destinations().get(d.id())))
            jdbc.update("update daily_career.learning_day set local_date=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",temporary.plusDays(d.dayNo()),d.id());
        jdbc.update("delete from daily_career.learning_day d where d.user_id=? and d.user_curriculum_id=? and d.local_date>=? and d.day_type='REST' and not exists(select 1 from daily_career.learning_day_item i where i.learning_day_id=d.id)",user,course.id(),LocalDate.parse(request.baseDate()));
        LocalDate base=LocalDate.parse(request.baseDate());
        for(DayRow d:plan.days()) {
            if(d.date().isBefore(base)||d.locked())continue;
            LocalDate destination=plan.destinations().get(d.id());
            String source=plan.manualStudy().contains(destination)?"MANUAL":"POLICY";
            jdbc.update("update daily_career.learning_day set local_date=?,day_type='STUDY',source=?,schedule_policy_id=?,planned_minutes=?,rest_reason=null,updated_at=clock_timestamp(),revision=revision+1 where id=?",
                    destination,source,policyId,plan.policy().dailyStudyMinutes(),d.id());
        }
        Set<LocalDate> study=new HashSet<>(plan.destinations().values());
        for(LocalDate date=LocalDate.parse(request.baseDate());!date.isAfter(plan.newEnd());date=date.plusDays(1))if(!study.contains(date)) {
            boolean manual=plan.manualRest().contains(date);boolean policyRest=plan.policy().restWeekdays().contains(date.getDayOfWeek());
            if(manual || (policyRest&&!plan.manualStudy().contains(date)))jdbc.update("""
                    insert into daily_career.learning_day(user_id,user_curriculum_id,local_date,day_type,source,schedule_policy_id,planned_minutes,rest_reason)
                    values (?,?,?,'REST',?,?,0,?)
                    """,user,course.id(),date,manual?"MANUAL":"POLICY",policyId,manual&&request.date()!=null&&date.equals(parseDate(request.date()))?request.reason().name():null);
        }
    }

    private long applyPolicy(long user,Course course,Plan plan,LearningCommandService.Policy policy) {
        LocalDate base=parseDate(plan.request().baseDate());long id;
        if(course.policyFrom().equals(base)) {
            id=course.policyId();jdbc.update("update daily_career.schedule_policy set weekly_rest_count=?,study_minutes=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",policy.restDaysPerWeek(),policy.dailyStudyMinutes(),id);
            jdbc.update("delete from daily_career.schedule_rest_weekday where schedule_policy_id=?",id);
        } else {
            jdbc.update("update daily_career.schedule_policy set effective_to=?,updated_at=clock_timestamp(),revision=revision+1 where id=?",base,course.policyId());
            id=Objects.requireNonNull(jdbc.queryForObject("insert into daily_career.schedule_policy(user_id,user_curriculum_id,effective_from,weekly_rest_count,study_minutes) values (?,?,?,?,?) returning id",Long.class,user,course.id(),base,policy.restDaysPerWeek(),policy.dailyStudyMinutes()));
        }
        for(DayOfWeek day:policy.restWeekdays())jdbc.update("insert into daily_career.schedule_rest_weekday(schedule_policy_id,iso_weekday) values (?,?)",id,day.getValue());
        return id;
    }

    private Course lock(long user,long course) {
        jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);users.current(user);
        var rows=jdbc.queryForList("select * from daily_career.user_curriculum where id=? and user_id=? for update",course,user);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);var row=rows.getFirst();
        var policies=jdbc.queryForList("select * from daily_career.schedule_policy where user_id=? and user_curriculum_id=? and effective_to is null order by effective_from desc limit 1 for update",user,course);
        if(policies.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);var p=policies.getFirst();
        List<DayOfWeek> weekdays=jdbc.queryForList("select iso_weekday from daily_career.schedule_rest_weekday where schedule_policy_id=? order by iso_weekday",Integer.class,id(p,"id")).stream().map(DayOfWeek::of).toList();
        return new Course(course,date(row,"start_date"),date(row,"projected_end_date"),revision(row,"schedule_revision"),id(p,"id"),date(p,"effective_from"),new LearningCommandService.Policy(number(p,"study_minutes"),number(p,"weekly_rest_count"),weekdays));
    }
    private PreviewRow previewRow(long user,long course,long preview) {
        var rows=jdbc.query("""
                select action,schedule_revision,request_snapshot::text,preview_snapshot::text,consumed_at is not null consumed,expires_at<=clock_timestamp() expired
                from daily_career.schedule_preview where id=? and user_id=? and user_curriculum_id=? for update
                """,(r,n)->new PreviewRow(Action.valueOf(r.getString("action")),r.getInt("schedule_revision"),r.getString("request_snapshot"),r.getString("preview_snapshot"),r.getBoolean("consumed"),r.getBoolean("expired")),preview,user,course);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return rows.getFirst();
    }
    private boolean samePlan(ReschedulePreview stored,Plan calculated) {
        return stored.previousEndDate().equals(calculated.previousEnd())&&stored.newEndDate().equals(calculated.newEnd())&&stored.moves().equals(calculated.moves());
    }
    private void validate(PreviewRequest r) {
        if(!"SHIFT".equals(r.strategy()))throw new ApiException(VALIDATION_FAILED);
        boolean rest=r.action()==Action.REST_ADD||r.action()==Action.REST_REMOVE;
        if(rest!=(r.date()!=null) || (r.action()==Action.REST_ADD)!=(r.reason()!=null) || (r.action()==Action.POLICY_APPLY)!=(r.studyPolicy()!=null))throw new ApiException(VALIDATION_FAILED);
        if(r.studyPolicy()!=null&&(r.studyPolicy().restWeekdays().size()!=r.studyPolicy().restDaysPerWeek()||new HashSet<>(r.studyPolicy().restWeekdays()).size()!=r.studyPolicy().restWeekdays().size()))throw new ApiException(VALIDATION_FAILED);
    }
    private <T>T read(String value,Class<T> type) {try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Invalid stored schedule preview",e);}}
    private static long id(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
    private static int number(Map<String,Object> row,String key) {return ((Number)row.get(key)).intValue();}
    private record DayRow(long id,LocalDate date,int dayNo,String source,boolean locked) {}
    private record Course(long id,LocalDate start,LocalDate end,int revision,long policyId,LocalDate policyFrom,LearningCommandService.Policy policy) {}
    private record PreviewRow(Action action,int revision,String request,String snapshot,boolean consumed,boolean expired) {}
    private record Plan(PreviewRequest request,LearningCommandService.Policy policy,Set<LocalDate> manualRest,Set<LocalDate> manualStudy,List<DayRow> days,Map<Long,LocalDate> destinations,LocalDate previousEnd,LocalDate newEnd,List<MovedDay> moves) {
        ReschedulePreview view(Long preview,long course,int revision,OffsetDateTime expires) {
            var publicPolicy=new LearningViews.StudyPolicy(policy.dailyStudyMinutes(),policy.restDaysPerWeek(),policy.restWeekdays().stream().map(Enum::name).toList());
            return new ReschedulePreview(preview,course,revision,"SHIFT",request.action().name(),parseDate(request.baseDate()),request.date()==null?null:parseDate(request.date()),request.reason()==null?null:request.reason().name(),request.action()==Action.POLICY_APPLY?publicPolicy:null,moves.size(),previousEnd,newEnd,moves,expires,List.of());
        }
    }
}
