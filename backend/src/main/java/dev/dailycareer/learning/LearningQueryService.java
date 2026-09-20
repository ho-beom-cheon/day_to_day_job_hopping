package dev.dailycareer.learning;

import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.learning.LearningCatalog.*;
import static dev.dailycareer.learning.LearningViews.*;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class LearningQueryService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LearningQueryService.class);
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final LearningCatalog catalog;
    private final Clock clock;
    public LearningQueryService(JdbcTemplate jdbc,UserAccountService users,LearningCatalog catalog,Clock clock) {
        this.jdbc=jdbc;this.users=users;this.catalog=catalog;this.clock=clock;
    }
    public LocalDate today() {return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));}
    public Long currentId(long user) {
        users.current(user);
        var ids=jdbc.queryForList("""
                select c.id from daily_career.user_curriculum c
                join daily_career.curriculum_release r on r.curriculum_version_id=c.curriculum_version_id
                where c.user_id=? and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED')
                order by (c.status in ('PLANNED','ACTIVE','PAUSED')) desc,c.created_at desc,c.id desc limit 1
                """,Long.class,user);
        return ids.isEmpty()?null:ids.getFirst();
    }
    public Curriculum current(long user) {Long id=currentId(user);return id==null?null:curriculum(user,id);}
    public Curriculum curriculum(long user,long course) {return load(user,course).view();}
    public List<CurriculumMonth> months(long user,long course) {
        Course c=load(user,course);var result=new ArrayList<CurriculumMonth>();
        for(var m:modules(c,"MONTH")) {
            int n=number(m,"order_no");var days=c.days.stream().filter(d->number(d,"month_no")==n).toList();
            Stats s=c.stats(days);
            result.add(new CurriculumMonth(n,(String)m.get("title"),minDate(days),maxDate(days),s.total,s.done,rate(s.done,s.total),
                    days.stream().map(d->number(d,"week_no")).distinct().sorted().toList()));
        }return result;
    }
    public CurriculumMonth month(long user,long course,int number) {
        return months(user,course).stream().filter(m->m.monthNo()==number).findFirst().orElseThrow(()->new ApiException(RESOURCE_NOT_FOUND));
    }
    public List<WeekSummary> weeks(long user,long course) {
        Course c=load(user,course);return modules(c,"WEEK").stream().map(m->weekSummary(c,m)).toList();
    }
    public WeekDetail week(long user,long course,int week) {
        Course c=load(user,course);
        // Do not disguise an operational mismatch as an empty week or change learning state on GET.
        c.items.stream().filter(i->!"READY".equals(i.get("content_status"))).forEach(i->
                log.warn("LEARNING_CONTENT_MISMATCH curriculumId={} sessionId={}",course,id(i,"id")));
        var module=modules(c,"WEEK").stream().filter(m->number(m,"order_no")==week).findFirst().orElseThrow(()->new ApiException(RESOURCE_NOT_FOUND));
        var summary=weekSummary(c,module);
        return new WeekDetail(summary.weekNo(),summary.monthNo(),summary.title(),summary.startDate(),summary.endDate(),
                summary.requiredSessionCount(),summary.completedSessionCount(),summary.progressRate(),catalog.strings((String)module.get("learning_goals"),1,30,300),
                c.days.stream().filter(d->number(d,"week_no")==week).map(d->c.day(date(d,"local_date"))).toList());
    }
    private WeekSummary weekSummary(Course c,Map<String,Object> m) {
        int n=number(m,"order_no");var days=c.days.stream().filter(d->number(d,"week_no")==n).toList();
        if(days.isEmpty())throw new ApiException(CONTENT_NOT_READY);
        Stats s=c.stats(days);
        return new WeekSummary(n,number(days.getFirst(),"month_no"),(String)m.get("title"),minDate(days),maxDate(days),s.total,s.done,rate(s.done,s.total));
    }
    private List<Map<String,Object>> modules(Course c,String type) {
        return jdbc.queryForList("""
                select u.order_no,u.title,d.learning_goals::text from daily_career.curriculum_unit u
                join daily_career.curriculum_unit_detail d on d.curriculum_unit_id=u.id
                where u.curriculum_version_id=? and u.unit_type=? order by u.order_no
                """,id(c.row,"curriculum_version_id"),type);
    }
    public LearningDay day(long user,Long course,LocalDate date) {
        Long chosen=course==null?currentId(user):course;
        return chosen==null?null:load(user,chosen).day(date);
    }
    public List<LearningDay> days(long user,Long course,LocalDate from,LocalDate to) {
        if(to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>=93) throw new ApiException(VALIDATION_FAILED);
        Long chosen=course==null?currentId(user):course;
        if(chosen==null)return List.of(); Course c=load(user,chosen);
        return from.datesUntil(to.plusDays(1)).map(c::day).toList();
    }
    public List<SessionSummary> daySessions(long user,Long course,LocalDate date) {
        var day=day(user,course,date);return day==null?List.of():day.sessions();
    }
    public Session session(long user,long session) {
        var r=sessionRow(user,session);
        var counts=jdbc.queryForMap("select count(*) filter(where required) as total,count(*) filter(where required and completed_at is not null) as done from daily_career.assigned_content where learning_day_item_id=? and user_id=?",session,user);
        return new Session(session,id(r,"learning_day_id"),id(r,"user_curriculum_id"),date(r,"local_date"),(String)r.get("category"),
                (String)r.get("title"),(String)r.get("description"),number(r,"order_no"),(boolean)r.get("required"),(String)r.get("status"),
                number(r,"estimated_minutes"),number(r,"actual_minutes"),"SELF_REPORTED",time(r,"started_at"),time(r,"completed_at"),
                number(counts,"total"),number(counts,"done"),revision(r,"revision"));
    }
    Map<String,Object> sessionRow(long user,long session) {
        users.current(user);
        var rows=jdbc.queryForList("""
                select i.*,d.local_date,s.title,s.description,s.category,s.estimated_minutes,s.actual_minutes
                from daily_career.learning_day_item i join daily_career.assigned_session_detail s on s.learning_day_item_id=i.id and s.user_id=i.user_id
                join daily_career.learning_day d on d.id=i.learning_day_id and d.user_id=i.user_id where i.id=? and i.user_id=?
                """,session,user);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }
    public List<ContentSummary> contents(long user,long session) {
        sessionRow(user,session);
        return contentRows(user,session).stream().map(this::summary).toList();
    }
    private ContentSummary summary(Map<String,Object> r) {
        return new ContentSummary(id(r,"id"),id(r,"content_version_id"),(String)r.get("title"),(String)r.get("content_type"),number(r,"sequence"),
                (boolean)r.get("required"),r.get("completed_at")==null?"NOT_STARTED":"COMPLETED",(String)r.get("completion_rule"));
    }
    private List<Map<String,Object>> contentRows(long user,long session) {
        return jdbc.queryForList("""
                select a.*,d.title from daily_career.assigned_content a join daily_career.learning_content_detail d on d.content_version_id=a.content_version_id
                where a.user_id=? and a.learning_day_item_id=? order by a.sequence,a.id
                """,user,session);
    }
    public LearningContent content(long user,long content) {
        users.current(user);
        var rows=jdbc.queryForList("""
                select a.*,d.title,d.learning_objective,d.key_points::text,d.interview_points::text,v.body_markdown
                from daily_career.assigned_content a join daily_career.learning_content_version v on v.id=a.content_version_id
                join daily_career.learning_content_detail d on d.content_version_id=v.id where a.id=? and a.user_id=?
                """,content,user);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);
        var r=rows.getFirst();var s=summary(r);
        var problems=jdbc.query("""
                select p.id problem_id,p.title,v.question_type from daily_career.learning_content_problem cp join daily_career.problem_version v on v.id=cp.problem_version_id
                join daily_career.problem p on p.id=v.problem_id where cp.content_version_id=? order by cp.sequence
                """,(rs,n)->new ProblemSummary(rs.getLong("problem_id"),rs.getString("title"),
                        "TRUE_FALSE".equals(rs.getString("question_type"))?"TRUE_FALSE":"MULTIPLE_CHOICE"),s.contentVersionId());
        return new LearningContent(s.contentId(),s.contentVersionId(),s.title(),s.type(),s.sequence(),s.required(),s.status(),s.completionRule(),
                id(r,"learning_day_item_id"),(String)r.get("learning_objective"),(String)r.get("body_markdown"),
                catalog.strings((String)r.get("key_points"),0,30,1000),catalog.strings((String)r.get("interview_points"),0,30,1000),problems,time(r,"completed_at"));
    }
    Course load(long user,long course) {
        users.current(user);
        var rows=jdbc.queryForList("""
                select c.*,r.title,r.version_label from daily_career.user_curriculum c
                join daily_career.curriculum_release r on r.curriculum_version_id=c.curriculum_version_id where c.id=? and c.user_id=?
                """,course,user);
        if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);
        var days=jdbc.queryForList("""
                select d.*,a.day_no,a.month_no,a.week_no,a.title,a.description from daily_career.learning_day d
                left join daily_career.assigned_learning_day a on a.learning_day_id=d.id and a.user_id=d.user_id
                where d.user_id=? and d.user_curriculum_id=? order by a.day_no nulls last,d.local_date
                """,user,course);
        var items=jdbc.queryForList("""
                select i.*,s.title,s.category,s.estimated_minutes,s.actual_minutes from daily_career.learning_day_item i
                join daily_career.assigned_session_detail s on s.learning_day_item_id=i.id and s.user_id=i.user_id
                where i.user_id=? and i.user_curriculum_id=? order by i.order_no,i.id
                """,user,course);
        var policies=jdbc.queryForList("select * from daily_career.schedule_policy where user_id=? and user_curriculum_id=? order by effective_from desc limit 1",user,course);
        if(policies.isEmpty())throw new ApiException(CONTENT_NOT_READY);
        var p=policies.getFirst();
        List<String> rest=jdbc.queryForList("select iso_weekday from daily_career.schedule_rest_weekday where schedule_policy_id=? order by iso_weekday",Integer.class,id(p,"id"))
                .stream().map(n->DayOfWeek.of(n).name()).toList();
        return new Course(rows.getFirst(),days,items,new StudyPolicy(number(p,"study_minutes"),number(p,"weekly_rest_count"),rest));
    }
    class Course {
        final Map<String,Object> row; final List<Map<String,Object>> calendar,days,items; final StudyPolicy policy;
        final Map<Long,List<Map<String,Object>>> itemsByDay;
        Course(Map<String,Object> row,List<Map<String,Object>> calendar,List<Map<String,Object>> items,StudyPolicy policy) {
            this.row=row;this.calendar=calendar;this.days=calendar.stream().filter(d->d.get("day_no")!=null).toList();this.items=items;this.policy=policy;
            this.itemsByDay=items.stream().collect(Collectors.groupingBy(i->id(i,"learning_day_id")));
        }
        Stats stats(List<Map<String,Object>> ds) {
            var ids=ds.stream().map(d->id(d,"id")).collect(Collectors.toSet());
            var required=items.stream().filter(i->ids.contains(id(i,"learning_day_id")) && (boolean)i.get("required")).toList();
            return new Stats(required.size(),(int)required.stream().filter(i->"COMPLETED".equals(i.get("status"))).count());
        }
        Curriculum view() {
            Stats s=stats(days);
            var next=days.stream().filter(d->{var st=stats(List.of(d));return st.done<st.total;}).findFirst().orElse(null);
            String status=s.total>0 && s.done==s.total?"COMPLETED":today().isBefore(date(row,"start_date"))?"PLANNED":"IN_PROGRESS";
            return new Curriculum(id(row,"id"),id(row,"curriculum_version_id"),(String)row.get("version_label"),(String)row.get("title"),
                    date(row,"start_date"),date(row,"target_end_date"),date(row,"projected_end_date"),(int)days.stream().map(d->d.get("week_no")).distinct().count(),
                    days.size(),s.total,s.done,next==null?null:number(next,"week_no"),next==null?null:number(next,"day_no"),status,rate(s.done,s.total),policy,
                    revision(row,"schedule_revision"),time(row,"created_at"));
        }
        LearningDay day(LocalDate date) {
            var r=calendar.stream().filter(d->date.equals(date(d,"local_date"))).findFirst().orElse(null);
            int rev=revision(row,"schedule_revision");long course=id(row,"id");
            if(r==null || r.get("day_no")==null) {
                boolean rest=r!=null && "REST".equals(r.get("day_type"));String kind=rest?"REST":"NONE";
                return new LearningDay(null,course,date,null,null,null,kind,rest?"휴식일":"일정 없음","",kind,0,0,null,0,0,rev,List.of());
            }
            var sessions=itemsByDay.getOrDefault(id(r,"id"),List.of());Stats s=stats(List.of(r));
            String state=s.total>0 && s.done==s.total?"COMPLETED":sessions.stream().anyMatch(i->!"NOT_STARTED".equals(i.get("status")))?"IN_PROGRESS":"NOT_STARTED";
            return new LearningDay(id(r,"id"),course,date,number(r,"day_no"),number(r,"week_no"),number(r,"month_no"),"STUDY",(String)r.get("title"),
                    (String)r.get("description"),state,sessions.stream().mapToInt(i->number(i,"estimated_minutes")).sum(),sessions.stream().mapToInt(i->number(i,"actual_minutes")).sum(),
                    rate(s.done,s.total),s.done,s.total,rev,sessions.stream().map(i->new SessionSummary(id(i,"id"),(String)i.get("category"),(String)i.get("title"),
                    number(i,"order_no"),(boolean)i.get("required"),number(i,"estimated_minutes"),(String)i.get("status"))).toList());
        }
    }
    record Stats(int total,int done) {}
    static Double rate(int done,int total) {return total==0?null:Math.round(done*1000.0/total)/10.0;}
    static int revision(Map<String,Object> r,String key) {long n=id(r,key);if(n>Integer.MAX_VALUE)throw new ApiException(PRECONDITION_FAILED);return (int)n;}
    static LocalDate date(Map<String,Object> r,String key) {var v=r.get(key);return v instanceof java.sql.Date d?d.toLocalDate():(LocalDate)v;}
    static LocalDate minDate(List<Map<String,Object>> ds) {return ds.stream().map(d->date(d,"local_date")).min(Comparator.naturalOrder()).orElseThrow(()->new ApiException(CONTENT_NOT_READY));}
    static LocalDate maxDate(List<Map<String,Object>> ds) {return ds.stream().map(d->date(d,"local_date")).max(Comparator.naturalOrder()).orElseThrow(()->new ApiException(CONTENT_NOT_READY));}
}
