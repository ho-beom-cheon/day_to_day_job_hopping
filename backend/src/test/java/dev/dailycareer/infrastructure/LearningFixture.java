package dev.dailycareer.infrastructure;

import dev.dailycareer.learning.LearningCatalog;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Deliberately tiny, synthetic six-module curriculum. Never packaged or seeded in the application. */
final class LearningFixture {
    private final JdbcTemplate jdbc;
    long template,readVersion,practiceVersion,problemVersion,testVersion,firstUnit;
    LearningFixture(JdbcTemplate jdbc,LearningCatalog catalog,boolean publish) {
        this(jdbc,catalog,publish,1);
    }
    LearningFixture(JdbcTemplate jdbc,LearningCatalog catalog,boolean publish,int daysPerMonth) {
        this.jdbc=jdbc;
        long subject=id("insert into daily_career.subject(code,name) values (?, 'test only') returning id","F"+UUID.randomUUID().toString().replace("-", "").toUpperCase());
        long problem=id("insert into daily_career.problem(subject_id,title) values (?,'fixture problem') returning id",subject);
        problemVersion=id("""
                insert into daily_career.problem_version(problem_id,version_no,question_type,difficulty,prompt_markdown,choices,answer_key,rubric,
                   explanation_markdown,grading_rule_version,status,published_at)
                values (?,1,'SINGLE_CHOICE',1,'fixture question','[{"id":"A","text":"A"},{"id":"B","text":"B"}]','{"correct":"A"}','{}','secret explanation','fixture','PUBLISHED',now()) returning id
                """,problem);
        readVersion=content(subject,"read",false);practiceVersion=content(subject,"practice",true);
        long test=id("insert into daily_career.test(title,test_type) values ('fixture final','FINAL') returning id");
        testVersion=id("insert into daily_career.test_version(test_id,version_no,time_limit_seconds,pass_percent,rule_version,status,published_at) values (?,1,600,60,'fixture','PUBLISHED',now()) returning id",test);
        jdbc.update("insert into daily_career.test_item(test_version_id,problem_id,problem_version_id,item_no,max_score) values (?,?,?,1,10)",testVersion,problem,problemVersion);
        long curriculum=id("insert into daily_career.curriculum(code,title) values (?,'fixture') returning id","fixture-"+UUID.randomUUID());
        template=id("insert into daily_career.curriculum_version(curriculum_id,version_no,description) values (?,1,'Synthetic test content only') returning id",curriculum);
        jdbc.update("insert into daily_career.curriculum_release values (?,'test.1','Test curriculum')",template);
        for(int n=1;n<=6;n++) {
            long month=unit(null,"MONTH",n,"Month "+n,null,null);
            long week=unit(month,"WEEK",n,"Week "+n,null,null);
            jdbc.update("update daily_career.curriculum_unit_detail set learning_goals='[\"Fixture objective\"]' where curriculum_unit_id=?",week);
            for(int part=0;part<daysPerMonth;part++) {
            int dayNo=(n-1)*daysPerMonth+part+1;
            long day=unit(week,"DAY",dayNo,"Day "+dayNo,null,null);
            jdbc.update("update daily_career.curriculum_unit_detail set month_no=?,week_no=?,day_no=? where curriculum_unit_id=?",n,n,dayNo,day);
            long item=unit(day,"ITEM",1,"Learn "+n,subject,"LEARN");
            if(dayNo==1)firstUnit=item;
            jdbc.update("insert into daily_career.curriculum_item_content values (?,1,?,true,'CONCEPT','READ_ACK')",item,readVersion);
            jdbc.update("insert into daily_career.curriculum_item_content values (?,2,?,true,'PRACTICE','ALL_PROBLEMS_ATTEMPTED')",item,practiceVersion);
            long optional=unit(day,"ITEM",2,"Optional "+n,subject,"REVIEW");
            jdbc.update("update daily_career.curriculum_unit set required=false where id=?",optional);
            jdbc.update("insert into daily_career.curriculum_item_content values (?,1,?,true,'REVIEW','READ_ACK')",optional,readVersion);
            if(n==6 && part==daysPerMonth-1) {
                long exam=unit(day,"ITEM",3,"Final fixture",subject,"TEST");
                jdbc.update("update daily_career.curriculum_unit set test_version_id=? where id=?",testVersion,exam);
            }
            }
        }
        if(publish)catalog.publish(template);
    }
    private long content(long subject,String label,boolean problems) {
        long content=id("insert into daily_career.learning_content(subject_id,title) values (?,?) returning id",subject,label);
        long version=id("insert into daily_career.learning_content_version(learning_content_id,version_no,body_markdown,content_digest) values (?,1,'# Fixture body',?) returning id",content,"a".repeat(64));
        jdbc.update("insert into daily_career.learning_content_detail values (?,?,'Fixture objective','[\"Fixture point\"]','[]')",version,label);
        if(problems)jdbc.update("insert into daily_career.learning_content_problem values (?,?,1)",version,problemVersion);
        jdbc.update("update daily_career.learning_content_version set status='PUBLISHED',published_at=now() where id=?",version);
        return version;
    }
    private long unit(Long parent,String type,int order,String title,Long subject,String activity) {
        long unit=id("""
                insert into daily_career.curriculum_unit(curriculum_version_id,parent_id,unit_type,order_no,title,subject_id,activity_type,estimated_minutes,completion_weight)
                values (?,?,?,?,?,?,?,?,?) returning id
                """,template,parent,type,order,title,subject,activity,activity==null?null:30,activity==null?null:1);
        jdbc.update("insert into daily_career.curriculum_unit_detail(curriculum_unit_id) values (?)",unit);return unit;
    }
    long id(String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
}
