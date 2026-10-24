package dev.dailycareer.learning;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.learning.CatalogManuscript.*;

/** Offline operator workflow. Every mutation is atomic and never touches learner assignments. */
@Service
public class CatalogImportService {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final LearningCatalog catalog;
    public CatalogImportService(JdbcTemplate jdbc,ObjectMapper json,LearningCatalog catalog) {this.jdbc=jdbc;this.json=json;this.catalog=catalog;}
    public record Result(String templateId,String digest,String status,boolean reused) {}

    @Transactional
    public Result importDraft(String source) {
        Bundle b=parse(json,source);
        String canonical=canonical(json,json.valueToTree(b)).toString(),digest=digest(canonical),key=b.code()+":"+b.version();
        // One writer per curriculum serializes version allocation, including concurrent first import.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",b.code());
        var prior=jdbc.queryForList("select * from daily_career.catalog_import where release_key=?",key);
        if(!prior.isEmpty()) {
            var row=prior.getFirst();require(digest.equals(row.get("source_digest")),"Existing release differs; use a new version");
            long id=((Number)row.get("curriculum_version_id")).longValue();validateImported(id,digest);
            return result(id,digest,true);
        }
        jdbc.update("insert into daily_career.subject(code,name) values ('JAVA','Java') on conflict(code) do nothing");
        long subject=insert("select id from daily_career.subject where code='JAVA'");
        jdbc.update("insert into daily_career.curriculum(code,title) values (?,?) on conflict(code) do nothing",b.code(),b.title());
        long course=insert("select id from daily_career.curriculum where code=? for update",b.code());
        int version=jdbc.queryForObject("select coalesce(max(version_no),0)+1 from daily_career.curriculum_version where curriculum_id=?",Integer.class,course);
        long template=insert("insert into daily_career.curriculum_version(curriculum_id,version_no,duration_months,description) values (?,?,?,?) returning id",course,version,b.durationMonths(),b.description());
        jdbc.update("insert into daily_career.curriculum_release values (?,?,?)",template,b.version(),b.title());
        ObjectNode ids=json.createObjectNode();ObjectNode contentIds=ids.putObject("contents"),problemIds=ids.putObject("problems"),testIds=ids.putObject("tests");
        Map<String,Long> problems=new HashMap<>();Map<Integer,Long> days=new HashMap<>();
        long month=unit(template,null,"MONTH",1,b.title(),null,null,null,null,null);
        detail(month,b.description(),List.of(),null,null,null);
        Map<Integer,Long> weeks=new HashMap<>();
        for(var w:b.weeks()) {long id=unit(template,month,"WEEK",w.number(),w.title(),null,null,null,null,null);weeks.put(w.number(),id);detail(id,w.title(),w.goals(),null,null,null);}
        for(var d:b.days()) {
            for(var q:d.questions()) {
                long problem=insert("insert into daily_career.problem(subject_id,title) values (?,?) returning id",subject,q.title());
                var choices=json.createArrayNode();for(int i=0;i<q.choices().size();i++)choices.addObject().put("id",Integer.toString(i+1)).put("text",q.choices().get(i));
                long pv=insert("""
                        insert into daily_career.problem_version(problem_id,version_no,question_type,difficulty,prompt_markdown,choices,answer_key,rubric,explanation_markdown,grading_rule_version)
                        values (?,1,'SINGLE_CHOICE',?,?,?::jsonb,?::jsonb,'{}',?,'rule-choice-v1') returning id
                        """,problem,q.difficulty(),q.prompt(),choices.toString(),json.createObjectNode().put("correct",Integer.toString(q.answer())).toString(),q.explanation());
                problems.put(q.id(),pv);problemIds.put(q.id(),Long.toString(pv));
            }
            long content=insert("insert into daily_career.learning_content(subject_id,title) values (?,?) returning id",subject,d.title());
            String body=body(d);
            long cv=insert("insert into daily_career.learning_content_version(learning_content_id,version_no,body_markdown,source_references,content_digest) values (?,1,?,?::jsonb,?) returning id",content,body,json.valueToTree(d.sources()).toString(),digest(body));
            contentIds.put("day-"+d.number(),Long.toString(cv));
            jdbc.update("insert into daily_career.learning_content_detail values (?,?,?,?::jsonb,'[]')",cv,d.title(),d.objective(),json.valueToTree(d.keyPoints()).toString());
            int sequence=1;for(var q:d.questions()) if(q.use().equals("PRACTICE"))jdbc.update("insert into daily_career.learning_content_problem values (?,?,?)",cv,problems.get(q.id()),sequence++);
            long day=unit(template,weeks.get(d.week()),"DAY",(d.number()-1)%5+1,d.title(),null,null,null,null,null);
            days.put(d.number(),day);detail(day,d.objective(),List.of(),1,d.week(),d.number());
            long item=unit(template,day,"ITEM",1,d.title()+" 학습",subject,"LEARN",d.minutes(),cv,null);
            detail(item,d.objective(),List.of(),null,null,null);
            jdbc.update("insert into daily_career.curriculum_item_content values (?,1,?,true,'PRACTICE','ALL_PROBLEMS_ATTEMPTED')",item,cv);
        }
        Map<Integer,Integer> sequences=new HashMap<>();
        for(var a:b.assessments()) {
            long test=insert("insert into daily_career.test(title,test_type) values (?,?) returning id",a.title(),a.type());
            long tv=insert("insert into daily_career.test_version(test_id,version_no,time_limit_seconds,attempt_limit,pass_percent,rule_version) values (?,1,?,3,60,'rule-choice-v1') returning id",test,a.seconds());
            testIds.put(a.id(),Long.toString(tv));int sequence=1;
            for(String q:a.questions())jdbc.update("insert into daily_career.test_item(test_version_id,problem_id,problem_version_id,item_no,max_score) select ?,problem_id,id,?,10 from daily_career.problem_version where id=?",tv,sequence++,problems.get(q));
            long item=unit(template,days.get(a.day()),"ITEM",sequences.merge(a.day(),2,(oldValue,ignored)->oldValue+1),a.title(),subject,"TEST",a.seconds()/60,null,tv);
            detail(item,"학습한 내용을 확인하고 결과에서 해설을 복습해요.",List.of(),null,null,null);
        }
        catalog.plan(template,false,true);
        String databaseDigest=databaseDigest(template,ids);
        jdbc.update("insert into daily_career.catalog_import(curriculum_version_id,release_key,source_digest,source_json,imported_ids,database_digest) values (?,?,?,?::jsonb,?::jsonb,?)",template,key,digest,canonical,ids.toString(),databaseDigest);
        return result(template,digest,false);
    }

    @Transactional
    public Result validateImported(long template,String expectedDigest) {
        var row=locked(template);check(row,template,expectedDigest);catalog.plan(template,false,true);
        return result(template,expectedDigest,true);
    }

    @Transactional
    public Result publishReviewed(long template,String expectedDigest,String reviewNote) {
        require(reviewNote!=null && reviewNote.strip().length()>=20 && reviewNote.length()<=2000,"Review note must identify checks and evidence (20-2000 chars)");
        var row=locked(template);JsonNode ids=check(row,template,expectedDigest);
        String status=status(template);
        if(status.equals("PUBLISHED")) {require(row.get("reviewed_at")!=null,"missing review");return result(template,expectedDigest,true);}
        require(status.equals("DRAFT"),"Only DRAFT imports may be published");
        catalog.plan(template,false,true);
        for(var entry:Map.of("problems","problem_version","contents","learning_content_version","tests","test_version").entrySet())
            for(var value:ids.get(entry.getKey()))jdbc.update("update daily_career."+entry.getValue()+" set status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=? and status='DRAFT'",Long.parseLong(value.asText()));
        jdbc.update("update daily_career.catalog_import set reviewed_at=clock_timestamp(),review_note=? where curriculum_version_id=?",reviewNote.strip(),template);
        catalog.publish(template);
        return result(template,expectedDigest,false);
    }

    private Map<String,Object> locked(long template) {
        // Lock parent before children, matching the existing publication/edit triggers.
        jdbc.queryForList("select id from daily_career.curriculum_version where id=? for update",template);
        var rows=jdbc.queryForList("select * from daily_career.catalog_import where curriculum_version_id=? for update",template);
        require(rows.size()==1,"No manuscript import for template");return rows.getFirst();
    }
    private JsonNode check(Map<String,Object> row,long template,String expectedDigest) {
        require(expectedDigest!=null && expectedDigest.equals(row.get("source_digest")),"Review digest does not match the imported manuscript");
        JsonNode source=read(row.get("source_json").toString());parse(json,source.toString());
        require(digest(canonical(json,source).toString()).equals(expectedDigest),"Stored source changed");
        JsonNode ids=read(row.get("imported_ids").toString());
        require(databaseDigest(template,ids).equals(row.get("database_digest")),"Imported DB content changed; import a new release");
        return ids;
    }
    private String databaseDigest(long template,JsonNode ids) {
        var snapshot=json.createObjectNode();
        // Payload plus association rows, including unexpected extra children. All fields except publication bookkeeping are hashed.
        rows(snapshot,"curriculum_version","id=?",template);
        rows(snapshot,"curriculum_release","curriculum_version_id=?",template);
        rows(snapshot,"curriculum_unit","curriculum_version_id=?",template);
        rows(snapshot,"curriculum_unit_detail","curriculum_unit_id in (select id from daily_career.curriculum_unit where curriculum_version_id=?)",template);
        rows(snapshot,"curriculum_item_content","curriculum_unit_id in (select id from daily_career.curriculum_unit where curriculum_version_id=?)",template);
        for(String type:List.of("contents","problems","tests")) {
            String table=switch(type){case "contents"->"learning_content";case "problems"->"problem";default->"test";};
            var values=new ArrayList<Long>();ids.get(type).forEach(n->values.add(Long.parseLong(n.asText())));
            String placeholders=String.join(",",Collections.nCopies(values.size(),"?"));Object[] params=values.toArray();
            // Operators may edit drafts; holding version locks prevents edits during validation/publication.
            jdbc.queryForList("select id from daily_career."+table+"_version where id in ("+placeholders+") order by id for update",params);
            rows(snapshot,table+"_version","id in ("+placeholders+")",params);
            rows(snapshot,table,"id in (select "+table+"_id from daily_career."+table+"_version where id in ("+placeholders+"))",params);
            if(type.equals("contents")) {
                rows(snapshot,"learning_content_detail","content_version_id in ("+placeholders+")",params);
                rows(snapshot,"learning_content_problem","content_version_id in ("+placeholders+")",params);
            }
            if(type.equals("tests"))rows(snapshot,"test_item","test_version_id in ("+placeholders+")",params);
        }
        return digest(canonical(json,snapshot).toString());
    }
    private void rows(ObjectNode snapshot,String table,String where,Object...args) {
        var array=snapshot.putArray(table);
        jdbc.queryForList("select (to_jsonb(t)-ARRAY['status','published_at','updated_at','revision'])::text from daily_career."+table+" t where "+where+" order by to_jsonb(t)::text",String.class,args)
                .stream().map(this::read).map(n->canonical(json,n)).sorted(Comparator.comparing(JsonNode::toString)).forEach(array::add);
    }
    private long unit(long template,Long parent,String type,int sequence,String title,Long subject,String activity,Integer minutes,Long content,Long test) {
        return insert("insert into daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title,subject_id,activity_type,estimated_minutes,completion_weight,content_version_id,test_version_id) values (?,?,?,?,?,?,?,?,?,?,?) returning id",template,parent,type,sequence,title,subject,activity,minutes,activity==null?null:1,content,test);
    }
    private void detail(long unit,String description,List<String> goals,Integer month,Integer week,Integer day) {
        jdbc.update("insert into daily_career.curriculum_unit_detail values (?,?,?::jsonb,?,?,?)",unit,description,json.valueToTree(goals).toString(),month,week,day);
    }
    private Result result(long template,String digest,boolean reused) {return new Result(Long.toString(template),digest,status(template),reused);}
    private String status(long template) {return jdbc.queryForObject("select status from daily_career.curriculum_version where id=?",String.class,template);}
    private long insert(String sql,Object...args) {return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private JsonNode read(String value) {try{return json.readTree(value);}catch(Exception e){throw new IllegalArgumentException("Invalid stored JSON",e);}}
}
