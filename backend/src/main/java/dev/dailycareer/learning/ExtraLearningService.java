package dev.dailycareer.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.*;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.learning.LearningCatalog.*;

@Service
public class ExtraLearningService {
    public enum Mode { DEEPEN, EXPLORE, REVIEW, AHEAD }
    public record Start(@NotNull Mode mode,@JsonId Long contentVersionId,@JsonId Long sourceSessionId,@NotNull Integer targetMinutes) {}
    public record Topic(@JsonId Long contentVersionId,@JsonId Long sourceSessionId,String title,String category,int problemCount,int wrongCount,
                        String stage,Integer recommendedWeek,String learningObjective,boolean generated) {}
    public record Entry(@JsonId Long sessionId,String mode,String title,String category,String status,String courseStatus,int targetMinutes,int recordedMinutes,int todayMinutes,LocalDate scheduledDate) {}
    private final JdbcTemplate jdbc; private final LearningQueryService queries; private final LearningCommandService commands;
    private final UserAccountService users; private final IdempotencyService idempotency; private final ObjectMapper json;
    public ExtraLearningService(JdbcTemplate jdbc,LearningQueryService queries,LearningCommandService commands,UserAccountService users,IdempotencyService idempotency,ObjectMapper json) {
        this.jdbc=jdbc;this.queries=queries;this.commands=commands;this.users=users;this.idempotency=idempotency;this.json=json;
    }

    @Transactional(readOnly=true)
    public List<Topic> topics(long user,Mode mode) {
        Long course=queries.currentId(user);if(course==null)return List.of();
        if(mode==Mode.AHEAD) return jdbc.query("""
            select a.content_version_id,i.id,s.title,s.category,
              (select count(*) from daily_career.learning_content_problem p where p.content_version_id=a.content_version_id) problems
            from daily_career.learning_day_item i join daily_career.learning_day d on d.id=i.learning_day_id
            join daily_career.assigned_learning_day day on day.learning_day_id=d.id
            join daily_career.assigned_session_detail s on s.learning_day_item_id=i.id
            join daily_career.assigned_content a on a.learning_day_item_id=i.id
            where i.user_id=? and i.user_curriculum_id=? and i.required and i.status in ('NOT_STARTED','IN_PROGRESS')
              and d.local_date>? and i.content_status='READY'
            order by day.day_no,i.order_no,a.sequence limit 1
            """,(rs,n)->new Topic(rs.getLong("content_version_id"),rs.getLong("id"),rs.getString("title"),rs.getString("category"),rs.getInt("problems"),0,null,null,null,false),user,course,queries.today());
        String eligible=switch(mode) {
            case DEEPEN -> """
                and exists(select 1 from daily_career.assigned_content a join daily_career.learning_day_item i on i.id=a.learning_day_item_id
                  join daily_career.learning_day d on d.id=i.learning_day_id
                  where a.content_version_id=v.id and a.user_id=? and i.user_curriculum_id=? and i.required and d.local_date=?)
                """;
            case REVIEW -> """
                and exists(select 1 from daily_career.assigned_content a join daily_career.learning_day_item i on i.id=a.learning_day_item_id
                  where a.content_version_id=v.id and a.user_id=? and i.started_at is not null)
                """;
            default -> "";
        };
        var args=new ArrayList<Object>();args.add(user);args.add(user);
        if(mode==Mode.DEEPEN){args.add(user);args.add(course);args.add(queries.today());}
        else if(mode==Mode.REVIEW)args.add(user);
        return jdbc.query("""
            select v.id,d.title,s.code,d.learning_objective,coalesce(t.stage,personal.stage) stage,t.recommended_week,
              (personal.content_version_id is not null) generated,
              (select count(*) from daily_career.learning_content_problem p where p.content_version_id=v.id) problems,
              (select count(*) from daily_career.learning_content_problem cp join daily_career.problem_version pv on pv.id=cp.problem_version_id
                join daily_career.wrong_answer w on w.problem_id=pv.problem_id where cp.content_version_id=v.id and w.user_id=? and w.status<>'MASTERED') wrongs
            from daily_career.learning_content_version v join daily_career.learning_content c on c.id=v.learning_content_id
            join daily_career.study_source_readiness evidence on evidence.content_version_id=v.id and evidence.ready
            join daily_career.learning_content_detail d on d.content_version_id=v.id join daily_career.subject s on s.id=c.subject_id
            left join daily_career.study_topic_detail t on t.content_version_id=v.id
            left join daily_career.ai_learning_topic personal on personal.content_version_id=v.id and personal.user_id=c.owner_user_id
            where v.status='PUBLISHED' and c.enabled
              and ((c.owner_user_id is null and (exists(select 1 from daily_career.extra_learning_catalog catalog where catalog.content_version_id=v.id)
                or exists(select 1 from daily_career.curriculum_item_content ic join daily_career.curriculum_unit u on u.id=ic.curriculum_unit_id
                join daily_career.curriculum_version cv on cv.id=u.curriculum_version_id join daily_career.curriculum cur on cur.id=cv.curriculum_id
                where ic.content_version_id=v.id and cv.status='PUBLISHED' and cur.enabled
                  and cur.code<>'BACKEND-FOUNDATIONS-6M')))
                or (c.owner_user_id=? and personal.content_version_id is not null))
            """+eligible+" order by wrongs desc,s.code,t.learning_order nulls first,d.title,v.id",(rs,n)->new Topic(rs.getLong("id"),null,rs.getString("title"),rs.getString("code"),rs.getInt("problems"),rs.getInt("wrongs"),rs.getString("stage"),rs.getObject("recommended_week",Integer.class),rs.getString("learning_objective"),rs.getBoolean("generated")),args.toArray());
    }

    @Transactional
    public StoredReply start(long user,Start input,UUID key) {
        users.current(user);
        if(input.targetMinutes()==null || !Set.of(15,30,60,90,120).contains(input.targetMinutes()))throw new ApiException(VALIDATION_FAILED);
        return idempotency.execute(user,"EXTRA-001",key,json.valueToTree(input),()->{
            lock(user);
            Long course=queries.currentId(user);if(course==null)throw new ApiException(CONTENT_NOT_READY);
            jdbc.queryForList("select id from daily_career.user_curriculum where id=? for update",course);
            if(input.mode()==Mode.AHEAD) {
                if(input.sourceSessionId()==null || input.contentVersionId()!=null)throw new ApiException(VALIDATION_FAILED);
                var available=topics(user,Mode.AHEAD);
                if(available.isEmpty() || !Objects.equals(available.getFirst().sourceSessionId(),input.sourceSessionId()))throw new ApiException(LEARNING_NOT_AVAILABLE);
                long session=input.sourceSessionId();
                jdbc.update("insert into daily_career.extra_learning(session_id,user_id,mode,target_minutes,problem_limit) values (?,?,'AHEAD',?,100) on conflict(session_id) do nothing",session,user,input.targetMinutes());
                commands.start(user,session);
                return reply(user,session);
            }
            if(input.contentVersionId()==null || input.sourceSessionId()!=null)throw new ApiException(VALIDATION_FAILED);
            Topic topic=topics(user,input.mode()).stream().filter(t->t.contentVersionId().equals(input.contentVersionId())).findFirst().orElseThrow(()->new ApiException(RESOURCE_NOT_FOUND));
            var existing=jdbc.queryForList("""
                select i.id from daily_career.extra_learning e join daily_career.learning_day_item i on i.id=e.session_id
                where e.user_id=? and i.user_curriculum_id=? and e.mode=? and i.content_version_id=? and e.target_minutes=? and i.status='IN_PROGRESS'
                order by i.id desc limit 1
                """,Long.class,user,course,input.mode().name(),topic.contentVersionId(),input.targetMinutes());
            if(!existing.isEmpty())return reply(user,existing.getFirst());
            // A manual day has no curriculum day number and does not add a required learning day.
            long day=insert("""
                insert into daily_career.learning_day(user_id,user_curriculum_id,local_date,day_type,source,planned_minutes)
                values (?,?,?,'STUDY','MANUAL',0) on conflict(user_curriculum_id,local_date) do update set local_date=excluded.local_date returning id
                """,user,course,queries.today());
            long version=jdbc.queryForObject("select curriculum_version_id from daily_career.user_curriculum where id=?",Long.class,course);
            int order=jdbc.queryForObject("select coalesce(max(order_no),0)+1 from daily_career.learning_day_item where learning_day_id=?",Integer.class,day);
            long session=insert("""
                insert into daily_career.learning_day_item(user_id,user_curriculum_id,curriculum_version_id,learning_day_id,order_no,activity_type,content_version_id,content_status,required)
                values (?,?,?,?,?,'LEARN',?,'READY',false) returning id
                """,user,course,version,day,order,topic.contentVersionId());
            int limit=input.targetMinutes()==15?3:input.targetMinutes()==30?6:10;
            jdbc.update("insert into daily_career.extra_learning(session_id,user_id,mode,target_minutes,problem_limit) values (?,?,?,?,?)",session,user,input.mode().name(),input.targetMinutes(),limit);
            jdbc.update("insert into daily_career.assigned_session_detail(learning_day_item_id,user_id,title,description,category,estimated_minutes) values (?,?,?,?,?,?)",session,user,topic.title(),"선택한 예상 시간으로 학습 세션을 만들었어요. 콘텐츠와 풀이를 마치면 완료 기록이 남고 기본 학습 진도에는 영향을 주지 않아요.",topic.category(),input.targetMinutes());
            jdbc.update("insert into daily_career.assigned_content(user_id,learning_day_item_id,content_version_id,sequence,required,content_type,completion_rule) values (?,?,?,1,true,'PRACTICE',?)",user,session,topic.contentVersionId(),topic.problemCount()>0?"ALL_PROBLEMS_ATTEMPTED":"READ_ACK");
            commands.start(user,session);
            return reply(user,session);
        });
    }

    @Transactional(readOnly=true)
    public List<Entry> history(long user) {
        users.current(user);
        return entries(user,null);
    }
    @Transactional(readOnly=true)
    public Entry detail(long user,long session) {
        queries.session(user,session);
        var entries=entries(user,session);return entries.isEmpty()?null:entries.getFirst();
    }
    @Transactional
    public StoredReply recordTime(long user,long session,LearningCommandService.Complete input,UUID key) {
        queries.session(user,session);
        return idempotency.execute(user,"EXTRA-002",key,json.valueToTree(Map.of("sessionId",Long.toString(session),"body",input)),()->{
            lock(user);var s=queries.session(user,session);
            jdbc.queryForList("select id from daily_career.user_curriculum where id=? for update",s.curriculumId());
            jdbc.queryForList("select id from daily_career.learning_day_item where id=? for update",session);
            if(!s.status().equals("IN_PROGRESS") || detail(user,session)==null)throw new ApiException(SESSION_NOT_STARTED);
            if(input.actualMinutes()==null)throw new ApiException(VALIDATION_FAILED);
            addTime(jdbc,user,session,queries.today(),input.actualMinutes());
            return reply(user,session);
        });
    }
    static void addTime(JdbcTemplate jdbc,long user,long session,LocalDate date,int minutes) {
        if(minutes<1 || minutes>480)throw new ApiException(VALIDATION_FAILED);
        int total=jdbc.queryForObject("select actual_minutes from daily_career.assigned_session_detail where learning_day_item_id=? and user_id=?",Integer.class,session,user);
        if(total+minutes>480)throw new ApiException(VALIDATION_FAILED);
        jdbc.update("insert into daily_career.extra_learning_time(session_id,activity_date,minutes) values (?,?,?) on conflict(session_id,activity_date) do update set minutes=extra_learning_time.minutes+excluded.minutes",session,date,minutes);
        jdbc.update("update daily_career.assigned_session_detail set actual_minutes=actual_minutes+?,time_source='SELF_REPORTED' where learning_day_item_id=? and user_id=?",minutes,session,user);
    }
    private List<Entry> entries(long user,Long session) {
        var args=new ArrayList<Object>();args.add(queries.today());args.add(user);if(session!=null)args.add(session);
        return jdbc.query("""
            select e.*,s.title,s.category,s.actual_minutes,i.status,c.status course_status,d.local_date,
              coalesce((select t.minutes from daily_career.extra_learning_time t where t.session_id=e.session_id and t.activity_date=?),0) today_minutes
            from daily_career.extra_learning e join daily_career.learning_day_item i on i.id=e.session_id
            join daily_career.user_curriculum c on c.id=i.user_curriculum_id
            join daily_career.assigned_session_detail s on s.learning_day_item_id=i.id join daily_career.learning_day d on d.id=i.learning_day_id
            where e.user_id=?
            """+(session==null?"":" and e.session_id=?")+" order by (i.status='IN_PROGRESS') desc,e.created_at desc,e.session_id desc",
            (rs,n)->new Entry(rs.getLong("session_id"),rs.getString("mode"),rs.getString("title"),rs.getString("category"),rs.getString("status"),rs.getString("course_status"),rs.getInt("target_minutes"),rs.getInt("actual_minutes"),rs.getInt("today_minutes"),rs.getDate("local_date").toLocalDate()),args.toArray());
    }
    private StoredReply reply(long user,long session){return new StoredReply(200,json.valueToTree(detail(user,session)),null,null);}
    private void lock(long user){jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);users.current(user);}
    private long insert(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
}
