package dev.dailycareer.progress;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.user.UserAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;
import static dev.dailycareer.progress.ProgressViews.*;

@Service
public class ProgressService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc; private final UserAccountService users; private final IdempotencyService idempotency; private final ObjectMapper json; private final Clock clock;
    public ProgressService(JdbcTemplate jdbc,UserAccountService users,IdempotencyService idempotency,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.users=users;this.idempotency=idempotency;this.json=json;this.clock=clock;}

    @Transactional(readOnly=true)
    public Overall overall(long user,Long requested){long course=course(user,requested,false);if(course==0)return null;var c=counts(user,course,null,null);
        var dates=jdbc.queryForList("select d.local_date, bool_and(i.activity_type='TEST' or not i.required or i.status='COMPLETED') done from daily_career.learning_day d left join daily_career.learning_day_item i on i.learning_day_id=d.id where d.user_id=? and d.user_curriculum_id=? and d.day_type='STUDY' and exists(select 1 from daily_career.learning_day_item required_item where required_item.learning_day_id=d.id and required_item.required and required_item.activity_type<>'TEST') group by d.id,d.local_date order by d.local_date",user,course);
        int completed=(int)dates.stream().filter(r->Boolean.TRUE.equals(r.get("done"))).count();int[] streak=streaks(dates);
        var extra=jdbc.queryForMap("""
            select count(*)::int total,count(*) filter(where i.status='COMPLETED')::int completed
            from daily_career.extra_learning e join daily_career.learning_day_item i on i.id=e.session_id
            where e.user_id=?
            """,user);
        var ai=jdbc.queryForMap("select count(*)::int total,count(*) filter(where correct)::int correct from daily_career.ai_practice_attempt where user_id=?",user);
        var extraPractice=jdbc.queryForMap("""
            select count(*)::int total,count(*) filter(where result.is_correct)::int correct
            from daily_career.problem_attempt attempt
            join daily_career.assigned_content content on content.id::text=attempt.answer_payload->>'contentId' and content.user_id=attempt.user_id
            join daily_career.extra_learning extra on extra.session_id=content.learning_day_item_id and extra.user_id=attempt.user_id
            join daily_career.grading_run run on run.problem_attempt_id=attempt.id and run.is_current
            join daily_career.grading_result result on result.grading_run_id=run.id and result.problem_attempt_id=attempt.id
            where attempt.user_id=? and attempt.status='SUBMITTED'
            """,user);
        int estimated=jdbc.queryForObject("""
            select coalesce(sum(s.estimated_minutes),0)::int
            from daily_career.learning_day_item i
            join daily_career.assigned_session_detail s on s.learning_day_item_id=i.id
            where i.user_id=? and i.user_curriculum_id=? and i.status='COMPLETED' and i.activity_type<>'TEST'
            """,Integer.class,user,course);
        return new Overall(course,dates.size(),completed,c.total,c.done,rate(c.done,c.total),c.adherence(),null,c.minutes,estimated,c.first,c.correct,rate(c.correct,c.first),streak[0],streak[1],num(extra,"total"),num(extra,"completed"),num(extraPractice,"total"),num(extraPractice,"correct"),num(ai,"total"),num(ai,"correct"),now());}

    @Transactional(readOnly=true)
    public List<Category> categories(long user,Long requested){long course=course(user,requested,true);if(course==0)return List.of();return categoryCounts(user,course,null,null);}

    @Transactional(readOnly=true)
    public DailyProgress daily(long user,Long requested,LocalDate date){long course=course(user,requested,true);return course==0?null:dailyForCourse(user,course,date);}

    @Transactional(readOnly=true)
    public PeriodProgress weekly(long user,Long requested,String week){LocalDate from=isoWeekStart(week);return periodProgress(user,requested,from,from.plusDays(6));}

    @Transactional(readOnly=true)
    public PeriodProgress monthly(long user,Long requested,int year,int month){if(year<2000||year>2100||month<1||month>12)throw new ApiException(VALIDATION_FAILED);YearMonth value=YearMonth.of(year,month);return periodProgress(user,requested,value.atDay(1),value.atEndOfMonth());}

    @Transactional(readOnly=true)
    public PeriodProgress periodProgress(long user,Long requested,LocalDate from,LocalDate to){if(to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>=366)throw new ApiException(VALIDATION_FAILED);long course=course(user,requested,true);if(course==0)return null;List<DailyProgress> days=from.datesUntil(to.plusDays(1)).map(date->dailyForCourse(user,course,date)).toList();Counts counts=counts(user,course,from,to);return new PeriodProgress(course,from,to,days.stream().mapToInt(DailyProgress::plannedMinutes).sum(),counts.minutes,days.stream().mapToInt(DailyProgress::totalSessionCount).sum(),days.stream().mapToInt(DailyProgress::completedSessionCount).sum(),rate(counts.done,counts.total),counts.adherence(),null,counts.first,counts.correct,rate(counts.correct,counts.first),(int)days.stream().filter(day->!Set.of("REST","NONE").contains(day.status())).count(),(int)days.stream().filter(day->"REST".equals(day.status())).count(),days,now());}

    @Transactional(readOnly=true)
    public LearningCalendar calendar(long user,Long requested,int year,int month){if(year<2000||year>2100||month<1||month>12)throw new ApiException(VALIDATION_FAILED);long course=course(user,requested,true);if(course==0)return null;YearMonth value=YearMonth.of(year,month);LocalDate from=value.atDay(1),to=value.atEndOfMonth();int revision=Objects.requireNonNull(jdbc.queryForObject("select schedule_revision::int from daily_career.user_curriculum where id=? and user_id=?",Integer.class,course,user));
        Map<LocalDate,Map<String,Object>> rows=new HashMap<>();jdbc.queryForList("""
                select d.id,d.local_date,d.day_type,d.rest_reason,a.day_no,a.title,
                  count(i.id) filter(where i.activity_type<>'TEST' and i.required)::int total,
                  count(i.id) filter(where i.activity_type<>'TEST' and i.required and i.status='COMPLETED')::int done,
                  count(i.id) filter(where i.activity_type<>'TEST' and i.required and i.status<>'NOT_STARTED')::int started
                from daily_career.learning_day d left join daily_career.assigned_learning_day a on a.learning_day_id=d.id and a.user_id=d.user_id
                left join daily_career.learning_day_item i on i.learning_day_id=d.id and i.user_id=d.user_id
                where d.user_id=? and d.user_curriculum_id=? and d.local_date between ? and ?
                group by d.id,d.local_date,d.day_type,d.rest_reason,a.day_no,a.title
                """,user,course,from,to).forEach(row->rows.put(date(row.get("local_date")),row));
        Map<LocalDate,List<String>> tests=new HashMap<>();jdbc.queryForList("""
                select d.local_date,t.id from daily_career.learning_day d join daily_career.learning_day_item i on i.learning_day_id=d.id
                join daily_career.test_version v on v.id=i.test_version_id join daily_career.test t on t.id=v.test_id
                where d.user_id=? and d.user_curriculum_id=? and d.local_date between ? and ? order by d.local_date,t.id
                """,user,course,from,to).forEach(row->tests.computeIfAbsent(date(row.get("local_date")),ignored->new ArrayList<>()).add(Long.toString(id(row,"id"))));
        LocalDate today=LocalDate.now(SEOUL);List<CalendarDay> days=from.datesUntil(to.plusDays(1)).map(day->{Map<String,Object> row=rows.get(day);if(row==null)return new CalendarDay(day,null,null,"NONE","NONE","일정 없음",null,null,List.of(),false);if(row.get("day_no")==null && !"REST".equals(row.get("day_type")))return new CalendarDay(day,null,null,"NONE","NONE","일정 없음",null,null,List.of(),false);boolean rest="REST".equals(row.get("day_type"))||row.get("day_no")==null;if(rest)return new CalendarDay(day,id(row,"id"),null,"REST","REST","휴식일",null,restReason(row.get("rest_reason")),tests.getOrDefault(day,List.of()),false);int total=num(row,"total"),done=num(row,"done"),started=num(row,"started");String status=total==0?"NONE":done==total?"COMPLETED":started>0?"IN_PROGRESS":"NOT_STARTED";return new CalendarDay(day,id(row,"id"),num(row,"day_no"),"STUDY",status,(String)row.get("title"),rate(done,total),null,tests.getOrDefault(day,List.of()),day.isBefore(today)&&!"COMPLETED".equals(status));}).toList();return new LearningCalendar(course,year,month,revision,days);}

    @Transactional(readOnly=true)
    public EvaluationPage evaluations(long user,Long requested,Integer month,int page,int size,String sort){if(page<0||size<1||size>100)throw new ApiException(VALIDATION_FAILED);long course=course(user,requested,true);if(course==0)return new EvaluationPage(List.of(),page,size,0,0);
        if(month!=null&&(month<1||month>6))throw new ApiException(VALIDATION_FAILED);String order=switch(sort==null?"createdAt,desc":sort){case "createdAt,desc"->"created_at desc,id";case "createdAt,asc"->"created_at,id";default->throw new ApiException(INVALID_SORT_FIELD);};
        var rows=month==null
                ?jdbc.queryForList("select * from daily_career.monthly_evaluation where user_id=? and user_curriculum_id=? order by "+order,user,course)
                :jdbc.queryForList("select * from daily_career.monthly_evaluation where user_id=? and user_curriculum_id=? and (metrics_snapshot->>'monthNo')::int=? order by "+order,user,course,month);
        int total=rows.size(),from=Math.min(page*size,total),to=Math.min(from+size,total);return new EvaluationPage(rows.subList(from,to).stream().map(this::evaluation).toList(),page,size,total,total==0?0:(total+size-1)/size);}

    @Transactional(readOnly=true)
    public Evaluation evaluation(long user,long id){users.current(user);var rows=jdbc.queryForList("select * from daily_career.monthly_evaluation where id=? and user_id=?",id,user);if(rows.isEmpty())throw new ApiException(RESOURCE_NOT_FOUND);return evaluation(rows.getFirst());}

    @Transactional
    public StoredReply create(long user,EvaluationCreate input,UUID key){users.current(user);if(input.monthNo()==null||input.monthNo()<1||input.monthNo()>6)throw new ApiException(VALIDATION_FAILED);long course=course(user,input.curriculumId(),true);
        JsonNode fingerprint=json.valueToTree(input);return idempotency.execute(user,"EVAL-003",key,fingerprint,()->createSnapshot(user,course,input.monthNo()));}

    private StoredReply createSnapshot(long user,long course,int month){var period=period(user,course,month);if(!LocalDate.now(SEOUL).isAfter(period.to))throw new ApiException(EVALUATION_PERIOD_OPEN);
        var c=counts(user,course,period.from,period.to);var categories=categoryCounts(user,course,period.from,period.to);Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("monthNo",month);snapshot.put("from",period.from.toString());snapshot.put("to",period.to.toString());snapshot.put("requiredSessionCount",c.total);snapshot.put("completedSessionCount",c.done);snapshot.put("progressRate",rate(c.done,c.total));snapshot.put("adherenceRate",c.adherence());snapshot.put("masteryRate",null);snapshot.put("studyMinutes",c.minutes);snapshot.put("firstAttemptProblemCount",c.first);snapshot.put("firstAttemptCorrectCount",c.correct);snapshot.put("firstAttemptAccuracy",rate(c.correct,c.first));snapshot.put("monthlyTestScore",null);snapshot.put("assessmentStatus","INSUFFICIENT_DATA");snapshot.put("categoryProgress",categories);
        String payload=write(snapshot),version=sha(payload);var existing=jdbc.queryForList("select * from daily_career.monthly_evaluation where user_id=? and user_curriculum_id=? and metrics_snapshot->>'dataVersion'=?",user,course,version);if(!existing.isEmpty()){var value=evaluation(existing.getFirst());return new StoredReply(201,json.valueToTree(value),null,"/api/v1/evaluations/"+value.evaluationId());}
        int report=Objects.requireNonNull(jdbc.queryForObject("select coalesce(max(report_version),0)+1 from daily_career.monthly_evaluation where user_curriculum_id=? and (metrics_snapshot->>'monthNo')::int=?",Integer.class,course,month));snapshot.put("dataVersion",version);payload=write(snapshot);
        long id=Objects.requireNonNull(jdbc.queryForObject("insert into daily_career.monthly_evaluation(user_id,user_curriculum_id,month_start,report_version,source_cutoff_at,calculation_version,status,metrics_snapshot,report_payload,published_at) values (?,?,date_trunc('month',?::date)::date,?,clock_timestamp(),'progress-v1','READY',?::jsonb,'{}'::jsonb,clock_timestamp()) returning id",Long.class,user,course,period.from.toString(),report,payload));
        var value=evaluation(user,id);return new StoredReply(201,json.valueToTree(value),null,"/api/v1/evaluations/"+id);}

    private long course(long user,Long requested,boolean emptyAllowed){users.current(user);List<Long> ids;if(requested==null)ids=jdbc.query("select id from daily_career.user_curriculum where user_id=? and status in ('PLANNED','ACTIVE','PAUSED','COMPLETED') order by (status in ('PLANNED','ACTIVE','PAUSED')) desc,created_at desc,id desc limit 1",(rs,n)->rs.getLong(1),user);else ids=jdbc.query("select id from daily_career.user_curriculum where id=? and user_id=?",(rs,n)->rs.getLong(1),requested,user);if(ids.isEmpty()){if(requested!=null)throw new ApiException(RESOURCE_NOT_FOUND);return 0;}return ids.getFirst();}
    private Counts counts(long user,long course,LocalDate from,LocalDate to){String range=from==null?"":" and d.local_date between ? and ?";Object[] args=from==null?new Object[]{user,course}:new Object[]{user,course,from,to};var r=jdbc.queryForMap("""
        select count(*) filter(where i.activity_type<>'TEST' and i.required)::int total,count(*) filter(where i.activity_type<>'TEST' and i.required and i.status='COMPLETED')::int done,
        count(*) filter(where i.activity_type<>'TEST' and i.required and d.local_date<=current_date)::int due,
        count(*) filter(where i.activity_type<>'TEST' and i.required and i.completed_at is not null and (i.completed_at at time zone 'Asia/Seoul')::date<=d.local_date)::int on_time
        from daily_career.learning_day_item i join daily_career.learning_day d on d.id=i.learning_day_id where i.user_id=? and i.user_curriculum_id=?"""+range,args);
        Integer minutes=studyMinutes(user,course,from,to);
        var first=firstAttempts(user,course,from,to);return new Counts(num(r,"total"),num(r,"done"),num(r,"due"),num(r,"on_time"),minutes==null?0:minutes,first[0],first[1]);}
    private int studyMinutes(long user,long course,LocalDate from,LocalDate to){
        String range=from==null?"":" where activity_date between ? and ?";
        Object[] args=from==null?new Object[]{user,course,user,course,user,course}:new Object[]{user,course,user,course,user,course,from,to};
        return Objects.requireNonNull(jdbc.queryForObject("""
            select coalesce(floor(sum(seconds)/60),0)::int from (
              select d.local_date activity_date,a.actual_minutes*60 seconds
              from daily_career.assigned_session_detail a
              join daily_career.learning_day_item i on i.id=a.learning_day_item_id
              join daily_career.learning_day d on d.id=i.learning_day_id
              where i.user_id=? and i.user_curriculum_id=? and i.activity_type<>'TEST' and i.status='COMPLETED'
                and not exists(select 1 from daily_career.extra_learning e where e.session_id=i.id)
              union all
              select s.activity_date,s.active_seconds seconds from daily_career.learning_session s
              join daily_career.learning_day_item i on i.id=s.learning_day_item_id
              where s.user_id=? and i.user_curriculum_id=? and i.activity_type<>'TEST'
                and not exists(select 1 from daily_career.assigned_session_detail a where a.learning_day_item_id=i.id)
              union all
              select t.activity_date,t.minutes*60 seconds from daily_career.extra_learning_time t
              join daily_career.learning_day_item i on i.id=t.session_id
              where i.user_id=? and i.user_curriculum_id=?
            ) study
            """+range,Integer.class,args));
    }
    private int[] firstAttempts(long user,long course,LocalDate from,LocalDate to){String range=from==null?"":" and (x.submitted_at at time zone 'Asia/Seoul')::date between ? and ?";Object[] args=from==null?new Object[]{user,course}:new Object[]{user,course,from,to};var r=jdbc.queryForMap("with ranked as (select a.id,a.submitted_at,row_number() over(partition by a.problem_version_id order by a.submitted_at,a.id) rn from daily_career.problem_attempt a where a.user_id=? and a.user_curriculum_id=? and a.context_type='PRACTICE' and a.status='SUBMITTED') select count(*)::int total,count(*) filter(where gr.is_correct)::int correct from ranked x join daily_career.grading_run g on g.problem_attempt_id=x.id and g.is_current join daily_career.grading_result gr on gr.grading_run_id=g.id and gr.problem_attempt_id=x.id where x.rn=1"+range,args);return new int[]{num(r,"total"),num(r,"correct")};}
    private List<Category> categoryCounts(long user,long course,LocalDate from,LocalDate to){String range=from==null?"":" and d.local_date between ? and ?";Object[] args=from==null?new Object[]{user,course}:new Object[]{user,course,from,to};return jdbc.query("select coalesce(s.code,'ETC') category,count(*) filter(where i.activity_type<>'TEST' and i.required)::int total,count(*) filter(where i.activity_type<>'TEST' and i.required and i.status='COMPLETED')::int done from daily_career.learning_day_item i join daily_career.learning_day d on d.id=i.learning_day_id left join daily_career.curriculum_unit u on u.id=i.curriculum_unit_id left join daily_career.subject s on s.id=u.subject_id where i.user_id=? and i.user_curriculum_id=? and not exists(select 1 from daily_career.extra_learning e where e.session_id=i.id and e.mode<>'AHEAD')"+range+" group by coalesce(s.code,'ETC') order by category",(rs,n)->new Category(rs.getString("category"),rs.getInt("total"),rs.getInt("done"),rate(rs.getInt("done"),rs.getInt("total")),null,0,0,null),args);}
    private Period period(long user,long course,int month){var rows=jdbc.queryForList("""
        select min(d.local_date) from_date,max(d.local_date) to_date from daily_career.learning_day d join daily_career.assigned_learning_day a on a.learning_day_id=d.id and a.user_id=d.user_id where d.user_id=? and d.user_curriculum_id=? and a.month_no=?
        """,user,course,month);if(rows.isEmpty()||rows.getFirst().get("from_date")==null)throw new ApiException(RESOURCE_NOT_FOUND);return new Period(date(rows.getFirst().get("from_date")),date(rows.getFirst().get("to_date")));}
    private Evaluation evaluation(Map<String,Object> row){try{JsonNode n=json.readTree(row.get("metrics_snapshot").toString());List<Category> cats=json.convertValue(n.path("categoryProgress"),new TypeReference<>(){});return new Evaluation(id(row,"id"),id(row,"user_curriculum_id"),n.path("monthNo").asInt(),num(row,"report_version"),LocalDate.parse(n.path("from").asText()),LocalDate.parse(n.path("to").asText()),n.path("dataVersion").asText(),n.path("requiredSessionCount").asInt(),n.path("completedSessionCount").asInt(),nullable(n,"progressRate"),nullable(n,"adherenceRate"),null,n.path("studyMinutes").asInt(),n.path("firstAttemptProblemCount").asInt(),n.path("firstAttemptCorrectCount").asInt(),nullable(n,"firstAttemptAccuracy"),nullable(n,"monthlyTestScore"),n.path("assessmentStatus").asText(),cats,time(row.get("created_at")));}catch(Exception e){throw new IllegalStateException("Invalid evaluation snapshot",e);}}
    private int[] streaks(List<Map<String,Object>> rows){int current=0,longest=0;LocalDate today=LocalDate.now(clock.withZone(SEOUL));for(var r:rows){LocalDate day=date(r.get("local_date"));if(day.isAfter(today))break;if(Boolean.TRUE.equals(r.get("done"))){current++;longest=Math.max(longest,current);}else if(day.isBefore(today))current=0;}return new int[]{current,longest};}
    private record Counts(int total,int done,int due,int onTime,int minutes,int first,int correct){Double adherence(){return rate(onTime,due);}}
    private record Period(LocalDate from,LocalDate to){}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String sha(String v){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static Double rate(int n,int d){return d==0?null:BigDecimal.valueOf(n*100.0/d).setScale(1,RoundingMode.HALF_UP).doubleValue();}
    private static int num(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}private static long id(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
    private static LocalDate date(Object v){return v instanceof java.sql.Date d?d.toLocalDate():(LocalDate)v;}private static Double nullable(JsonNode n,String k){return n.path(k).isNumber()?n.path(k).doubleValue():null;}
    private OffsetDateTime now(){return OffsetDateTime.now(clock).atZoneSameInstant(SEOUL).toOffsetDateTime();}private static OffsetDateTime time(Object v){return v instanceof java.sql.Timestamp t?t.toInstant().atZone(SEOUL).toOffsetDateTime():((OffsetDateTime)v).atZoneSameInstant(SEOUL).toOffsetDateTime();}

    private DailyProgress dailyForCourse(long user,long course,LocalDate day){var rows=jdbc.queryForList("""
            select d.day_type,d.planned_minutes,
              count(i.id) filter(where i.activity_type<>'TEST' and i.required)::int total,
              count(i.id) filter(where i.activity_type<>'TEST' and i.required and i.status='COMPLETED')::int done,
              count(i.id) filter(where i.activity_type<>'TEST' and i.required and i.status<>'NOT_STARTED')::int started
            from daily_career.learning_day d left join daily_career.learning_day_item i on i.learning_day_id=d.id and i.user_id=d.user_id
            where d.user_id=? and d.user_curriculum_id=? and d.local_date=? group by d.id,d.day_type,d.planned_minutes
            """,user,course,day);String status="NONE";int planned=0,total=0,done=0;if(!rows.isEmpty()){var row=rows.getFirst();if("REST".equals(row.get("day_type")))status="REST";else{total=num(row,"total");done=num(row,"done");status=total==0?"NONE":done==total?"COMPLETED":num(row,"started")>0?"IN_PROGRESS":"NOT_STARTED";planned=num(row,"planned_minutes");}}
        Integer actual=studyMinutes(user,course,day,day);int[] attempts=attemptCounts(user,course,day);int[] first=firstAttempts(user,course,day,day);return new DailyProgress(day,status,planned,actual==null?0:actual,done,total,rate(done,total),attempts[0],attempts[1],rate(attempts[1],attempts[0]),first[0],first[1],rate(first[1],first[0]));}
    private int[] attemptCounts(long user,long course,LocalDate day){var row=jdbc.queryForMap("""
            select count(*)::int total,count(*) filter(where result.is_correct)::int correct
            from daily_career.problem_attempt attempt join daily_career.grading_run run on run.problem_attempt_id=attempt.id and run.is_current
            join daily_career.grading_result result on result.grading_run_id=run.id and result.problem_attempt_id=attempt.id
            where attempt.user_id=? and attempt.user_curriculum_id=? and attempt.context_type='PRACTICE' and attempt.status='SUBMITTED'
              and (attempt.submitted_at at time zone 'Asia/Seoul')::date=?
            """,user,course,day);return new int[]{num(row,"total"),num(row,"correct")};}
    static LocalDate isoWeekStart(String value){if(value==null||!value.matches("\\d{4}-W(0[1-9]|[1-4][0-9]|5[0-3])"))throw new ApiException(VALIDATION_FAILED);int year=Integer.parseInt(value.substring(0,4)),week=Integer.parseInt(value.substring(6));var fields=java.time.temporal.WeekFields.ISO;LocalDate monday=LocalDate.of(year,1,4).with(fields.weekBasedYear(),year).with(fields.weekOfWeekBasedYear(),week).with(fields.dayOfWeek(),1);if(monday.get(fields.weekBasedYear())!=year||monday.get(fields.weekOfWeekBasedYear())!=week)throw new ApiException(VALIDATION_FAILED);return monday;}
    private static String restReason(Object value){if(value==null)return null;String reason=value.toString();return Set.of("PERSONAL","VACATION","HEALTH","WORK","ETC").contains(reason)?reason:"ETC";}
}
