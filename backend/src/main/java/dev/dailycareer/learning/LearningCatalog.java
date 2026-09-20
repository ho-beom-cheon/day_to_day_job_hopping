package dev.dailycareer.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiErrorCode;
import dev.dailycareer.common.api.ApiException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

/** Validated, immutable curriculum releases. Publication is an operator service, not a public API. */
@Service
public class LearningCatalog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public LearningCatalog(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public record Content(long versionId, int sequence, boolean required, String type, String rule) {}
    public record Item(long id, String title, String description, String category, String activity,
                       int sequence, int minutes, boolean required, Long testVersionId, List<Content> contents) {}
    public record Day(long id, int number, int month, int week, String title, String description, List<Item> items) {}
    public record Module(int number, String title, List<String> goals) {}
    public record Plan(long id, String label, String title, String description, OffsetDateTime publishedAt,
                       List<Module> months, List<Module> weeks, List<Day> days) {
        int requiredSessions() { return (int)days.stream().flatMap(d->d.items.stream()).filter(i->i.testVersionId==null && i.required).count(); }
        LearningViews.CurriculumTemplate view() {
            return new LearningViews.CurriculumTemplate(id,label,title,description,6,weeks.size(),days.size(),requiredSessions(),publishedAt);
        }
    }

    @Transactional(readOnly=true)
    public List<LearningViews.CurriculumTemplate> templates() {
        var result=new ArrayList<LearningViews.CurriculumTemplate>();
        for (long id: jdbc.queryForList("""
                select v.id from daily_career.curriculum_version v join daily_career.curriculum c on c.id=v.curriculum_id
                join daily_career.curriculum_release r on r.curriculum_version_id=v.id
                where v.status='PUBLISHED' and c.enabled order by v.published_at desc,v.id desc
                """,Long.class)) result.add(plan(id,true).view());
        return result;
    }

    /** Caller must run in a transaction. The parent lock serializes publication and composition edits. */
    @Transactional
    public void publish(long id) {
        var state=jdbc.queryForList("select status from daily_career.curriculum_version where id=? for update",String.class,id);
        if (state.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        if (!state.getFirst().equals("DRAFT")) throw new ApiException(TEMPLATE_NOT_PUBLISHED);
        plan(id,false);
        jdbc.update("update daily_career.curriculum_version set status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?",id);
    }

    public Plan plan(long id, boolean published) {
        var rows=jdbc.queryForList("""
                select v.*,r.title,r.version_label,c.enabled from daily_career.curriculum_version v
                join daily_career.curriculum c on c.id=v.curriculum_id
                left join daily_career.curriculum_release r on r.curriculum_version_id=v.id where v.id=?
                """,id);
        if(rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        var v=rows.getFirst();
        if(published && (!"PUBLISHED".equals(v.get("status")) || !Boolean.TRUE.equals(v.get("enabled")))) throw new ApiException(TEMPLATE_NOT_PUBLISHED);
        ready(number(v,"duration_months")==6 && textValid(v.get("title"),1,120) && textValid(v.get("version_label"),1,40)
                && textValid(v.get("description"),1,1000));
        var units=jdbc.queryForList("""
                select u.*,d.description,d.learning_goals::text,d.month_no,d.week_no,d.day_no,s.code as category
                from daily_career.curriculum_unit u left join daily_career.curriculum_unit_detail d on d.curriculum_unit_id=u.id
                left join daily_career.subject s on s.id=u.subject_id where u.curriculum_version_id=? order by u.order_no,u.id
                """,id);
        var byId=new HashMap<Long,Map<String,Object>>(); units.forEach(u->byId.put(id(u,"id"),u));
        var months=new TreeMap<Integer,Module>(); var weeks=new TreeMap<Integer,Module>(); var days=new ArrayList<Day>();
        var dayNumbers=new HashSet<Integer>(); var seenItems=new HashSet<Long>();
        for(var u:units) {
            ready(textValid(u.get("title"),1,120) && textValid(u.get("description"),0,2000));
            String type=(String)u.get("unit_type");
            var parent=byId.get(u.get("parent_id"));
            int order=number(u,"order_no");
            switch(type) {
                case "MONTH" -> { ready(parent==null && order<=6 && !months.containsKey(order)); months.put(order,new Module(order,(String)u.get("title"),List.of())); }
                case "WEEK" -> {
                    ready(parent!=null && "MONTH".equals(parent.get("unit_type")) && order<=60 && !weeks.containsKey(order));
                    var goals=strings((String)u.get("learning_goals"),1,30,300);
                    weeks.put(order,new Module(order,(String)u.get("title"),goals));
                }
                case "DAY" -> ready(parent!=null && "WEEK".equals(parent.get("unit_type")));
                case "ITEM" -> ready(parent!=null && "DAY".equals(parent.get("unit_type")));
                default -> ready(false);
            }
        }
        ready(months.size()==6 && !weeks.isEmpty());
        consecutive(weeks.keySet());
        for(var u:units) if("DAY".equals(u.get("unit_type"))) {
            int day=number(u,"day_no"),month=number(u,"month_no"),week=number(u,"week_no");
            ready(day>0 && day<=366 && dayNumbers.add(day) && months.containsKey(month) && weeks.containsKey(week));
            ready(number(byId.get(u.get("parent_id")),"order_no")==week);
            var items=new ArrayList<Item>(); var sequences=new HashSet<Integer>();
            for(var i:units) if(Objects.equals(i.get("parent_id"),u.get("id"))) {
                ready("ITEM".equals(i.get("unit_type")) && seenItems.add(id(i,"id")));
                int sequence=number(i,"order_no"),minutes=number(i,"estimated_minutes");
                String category=(String)i.get("category");
                ready(sequence<=100 && sequences.add(sequence) && minutes>=1 && minutes<=480 && category!=null && category.matches("[A-Z][A-Z0-9_]{0,39}"));
                Long test=(Long)i.get("test_version_id");
                List<Content> contents;
                if("TEST".equals(i.get("activity_type"))) {
                    ready(test!=null && i.get("problem_version_id")==null && i.get("content_version_id")==null);
                    validateTest(test); contents=List.of();
                } else {
                    ready(test==null && i.get("problem_version_id")==null);
                    contents=contents(id(i,"id")); ready(!contents.isEmpty() && contents.size()<=100);
                    if(i.get("content_version_id")!=null) ready(contents.stream().anyMatch(c->c.versionId==id(i,"content_version_id")));
                }
                items.add(new Item(id(i,"id"),(String)i.get("title"),(String)i.get("description"),category,
                        (String)i.get("activity_type"),sequence,minutes,(boolean)i.get("required"),test,contents));
            }
            ready(!items.isEmpty() && items.size()<=100 && items.stream().anyMatch(i->i.testVersionId==null && i.required));
            ready(items.stream().filter(i->i.testVersionId==null).mapToInt(Item::minutes).sum()<=10000);
            days.add(new Day(id(u,"id"),day,month,week,(String)u.get("title"),(String)u.get("description"),List.copyOf(items)));
        }
        days.sort(Comparator.comparingInt(Day::number));
        consecutive(dayNumbers); ready(!days.isEmpty() && days.size()<=366);
        ready(units.stream().filter(u->"ITEM".equals(u.get("unit_type"))).count()==seenItems.size());
        for(int m:months.keySet()) ready(days.stream().anyMatch(d->d.month==m));
        for(int w:weeks.keySet()) { long count=days.stream().filter(d->d.week==w).count(); ready(count>=1 && count<=31); }
        // Module numbering never goes backwards, including a week spanning two month modules.
        for(int i=1;i<days.size();i++) ready(days.get(i).month>=days.get(i-1).month && days.get(i).week>=days.get(i-1).week);
        var plan=new Plan(id,(String)v.get("version_label"),(String)v.get("title"),(String)v.get("description"),time(v,"published_at"),
                List.copyOf(months.values()),List.copyOf(weeks.values()),List.copyOf(days));
        ready(plan.requiredSessions()>=1 && plan.requiredSessions()<=2000);
        return plan;
    }

    private List<Content> contents(long unit) {
        var result=new ArrayList<Content>();
        for(var r:jdbc.queryForList("select * from daily_career.curriculum_item_content where curriculum_unit_id=? order by sequence",unit)) {
            long version=id(r,"content_version_id"); validateContent(version);
            String rule=(String)r.get("completion_rule");
            if(rule.equals("ALL_PROBLEMS_ATTEMPTED")) ready(jdbc.queryForObject("select count(*) from daily_career.learning_content_problem where content_version_id=?",Integer.class,version)>0);
            result.add(new Content(version,number(r,"sequence"),(boolean)r.get("required"),(String)r.get("content_type"),rule));
        }
        return List.copyOf(result);
    }
    private void validateContent(long version) {
        var rows=jdbc.queryForList("""
                select v.*,d.title,d.learning_objective,d.key_points::text,d.interview_points::text,c.owner_user_id,c.enabled
                from daily_career.learning_content_version v join daily_career.learning_content c on c.id=v.learning_content_id
                left join daily_career.learning_content_detail d on d.content_version_id=v.id where v.id=?
                """,version);
        ready(rows.size()==1); var r=rows.getFirst();
        ready("PUBLISHED".equals(r.get("status")) && r.get("owner_user_id")==null && Boolean.TRUE.equals(r.get("enabled")));
        ready(textValid(r.get("title"),1,120) && textValid(r.get("learning_objective"),1,1000) && textValid(r.get("body_markdown"),1,100000));
        String body=(String)r.get("body_markdown");
        // Source validation complements the future renderer's raw-HTML-disabled policy.
        ready(!body.matches("(?is).*<(?:/?[a-z!]|\\?).*") && !body.matches("(?is).*(?:javascript|vbscript|data)\\s*:.*"));
        strings((String)r.get("key_points"),0,30,1000); strings((String)r.get("interview_points"),0,30,1000);
        var problems=jdbc.queryForList("select problem_version_id from daily_career.learning_content_problem where content_version_id=?",Long.class,version);
        ready(problems.size()<=100); problems.forEach(this::validateProblem);
    }
    private void validateProblem(long version) {
        var rows=jdbc.queryForList("""
                select v.status,v.question_type,p.owner_user_id,p.enabled,p.title from daily_career.problem_version v
                join daily_career.problem p on p.id=v.problem_id where v.id=?
                """,version);
        ready(rows.size()==1); var r=rows.getFirst();
        ready("PUBLISHED".equals(r.get("status")) && r.get("owner_user_id")==null && Boolean.TRUE.equals(r.get("enabled"))
                && Set.of("SINGLE_CHOICE","TRUE_FALSE").contains(r.get("question_type")) && textValid(r.get("title"),1,160));
    }
    private void validateTest(long version) {
        var rows=jdbc.queryForList("""
                select v.status,t.owner_user_id,t.enabled from daily_career.test_version v
                join daily_career.test t on t.id=v.test_id where v.id=?
                """,version);
        ready(rows.size()==1);var r=rows.getFirst();
        ready("PUBLISHED".equals(r.get("status")) && r.get("owner_user_id")==null && Boolean.TRUE.equals(r.get("enabled")));
        var problems=jdbc.queryForList("select problem_version_id from daily_career.test_item where test_version_id=?",Long.class,version);
        ready(!problems.isEmpty()); problems.forEach(this::validateProblem);
    }
    List<String> strings(String value,int min,int max,int length) {
        try {
            JsonNode node=json.readTree(value); ready(node.isArray() && node.size()>=min && node.size()<=max);
            var result=new ArrayList<String>();
            for(var item:node) { ready(item.isTextual() && textValid(item.asText(),1,length)); result.add(item.asText()); }
            return List.copyOf(result);
        } catch(ApiException e) {throw e;} catch(Exception e) {throw new ApiException(CONTENT_NOT_READY);}
    }
    private static boolean textValid(Object value,int min,int max) {
        if(!(value instanceof String s)) return false;
        return s.codePointCount(0,s.length())>=min && s.codePointCount(0,s.length())<=max && (min==0 || !s.isBlank());
    }
    private static void consecutive(Collection<Integer> values) {int i=1;for(int n:new TreeSet<>(values)) ready(n==i++);}
    static void ready(boolean condition) {if(!condition)throw new ApiException(CONTENT_NOT_READY);}
    static long id(Map<String,Object> r,String key) {return ((Number)r.get(key)).longValue();}
    static int number(Map<String,Object> r,String key) {return r.get(key)==null?0:((Number)r.get(key)).intValue();}
    static OffsetDateTime time(Map<String,Object> r,String key) {
        var value=r.get(key); if(value==null)return null;
        return (value instanceof java.sql.Timestamp t?t.toInstant().atOffset(ZoneOffset.UTC):(OffsetDateTime)value).withOffsetSameInstant(ZoneOffset.ofHours(9));
    }
}
