package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.learning.ExtraLearningService;
import dev.dailycareer.learning.LearningQueryService;
import dev.dailycareer.user.UserAccountService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
class AiLessonCatalog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ExtraLearningService extra;
    private final LearningQueryService learning;
    private final UserAccountService users;
    AiLessonCatalog(JdbcTemplate jdbc,ObjectMapper json,ExtraLearningService extra,LearningQueryService learning,UserAccountService users){
        this.jdbc=jdbc;this.json=json;this.extra=extra;this.learning=learning;this.users=users;
    }
    ObjectNode snapshot(long user,JsonNode input) {
        validateRequest(input);
        Long course=learning.currentId(user);
        if(course==null)throw new ApiException(CONTENT_NOT_READY);
        var result=AiLessonContent.providerInput(json,input);
        result.put("topic",input.path("topic").asText().strip().replaceAll("\\s+"," "));
        result.put("curriculumId",course.toString()).put("promptVersion",AiLessonContent.VERSION);
        result.remove("base");
        if(input.hasNonNull("baseContentVersionId")) {
            long base;
            try{base=Long.parseLong(input.path("baseContentVersionId").asText());}catch(Exception e){throw new ApiException(VALIDATION_FAILED);}
            var topic=extra.topics(user,ExtraLearningService.Mode.EXPLORE).stream()
                    .filter(t->Objects.equals(t.contentVersionId(),base)).findFirst().orElseThrow(()->new ApiException(RESOURCE_NOT_FOUND));
            if(!topic.category().equals(input.path("subject").asText()))throw new ApiException(VALIDATION_FAILED);
            var source=jdbc.queryForMap("select body_markdown,source_references::text from daily_career.learning_content_version where id=? and status='PUBLISHED'",base);
            var context=json.createObjectNode().put("title",topic.title()).put("body",source.get("body_markdown").toString());
            context.set("sources",read(source.get("source_references").toString()));
            result.set("base",context);result.put("baseContentVersionId",Long.toString(base));
        }
        return result;
    }
    static void validateRequest(JsonNode input) {
        if(!input.isObject())throw new ApiException(VALIDATION_FAILED);
        Set<String> allowed=Set.of("topic","subject","stage","targetMinutes","baseContentVersionId");
        input.fieldNames().forEachRemaining(field->{if(!allowed.contains(field))throw new ApiException(VALIDATION_FAILED);});
        if(!input.path("topic").isTextual()||input.path("topic").asText().strip().length()<2||input.path("topic").asText().length()>300
                ||!AiLessonContent.SUBJECTS.contains(input.path("subject").asText())||!AiLessonContent.STAGES.contains(input.path("stage").asText())
                ||!input.path("targetMinutes").isInt()||!Set.of(30,60,90,120).contains(input.path("targetMinutes").asInt())
                ||(input.hasNonNull("baseContentVersionId")&&(!input.path("baseContentVersionId").isTextual()||!input.path("baseContentVersionId").asText().matches("[1-9][0-9]{0,18}"))))
            throw new ApiException(VALIDATION_FAILED);
    }

    /** Called within the worker's lease-guarded transaction, together with job success and usage. */
    ObjectNode publish(long user,long job,JsonNode input,JsonNode lesson) {
        jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);
        var duplicate=jdbc.queryForList("""
            select v.id from daily_career.learning_content_version v
            join daily_career.learning_content c on c.id=v.learning_content_id
            join daily_career.ai_learning_topic t on t.content_version_id=v.id and t.user_id=c.owner_user_id
            where c.owner_user_id=? and v.status='PUBLISHED' and c.enabled and v.content_digest=? limit 1
            """,Long.class,user,lesson.path("digest").asText());
        if(!duplicate.isEmpty())return receipt(duplicate.getFirst(),lesson,input,true);
        long subject=jdbc.queryForObject("select id from daily_career.subject where code=?",Long.class,input.path("subject").asText());
        long content=insert("insert into daily_career.learning_content(owner_user_id,subject_id,title) values (?,?,?) returning id",user,subject,lesson.path("title").asText());
        long version=insert("""
            insert into daily_career.learning_content_version(learning_content_id,version_no,status,body_markdown,source_references,content_digest,ai_job_id)
            values (?,1,'DRAFT',?,?::jsonb,?,?) returning id
            """,content,lesson.path("body").asText(),lesson.path("sources").toString(),lesson.path("digest").asText(),job);
        jdbc.update("""
            insert into daily_career.learning_content_detail(content_version_id,title,learning_objective,key_points,interview_points)
            values (?,?,?,?::jsonb,'[]'::jsonb)
            """,version,lesson.path("title").asText(),lesson.path("objective").asText(),lesson.path("keyPoints").toString());
        int sequence=0;
        for(JsonNode question:lesson.path("questions")) {
            sequence++;
            long problem=insert("insert into daily_career.problem(owner_user_id,subject_id,title) values (?,?,?) returning id",user,subject,lesson.path("title").asText()+" · 확인 "+sequence);
            var choices=json.createArrayNode();int answerNo=0;
            for(JsonNode choice:question.path("choices"))choices.addObject().put("id",Integer.toString(++answerNo)).put("text",choice.asText());
            long pv=insert("""
                insert into daily_career.problem_version(problem_id,version_no,status,question_type,difficulty,prompt_markdown,choices,answer_key,rubric,explanation_markdown,grading_rule_version,ai_job_id,published_at)
                values (?,1,'PUBLISHED','SINGLE_CHOICE',?,?,?::jsonb,?::jsonb,'{}'::jsonb,?,'rule-choice-v1',?,clock_timestamp()) returning id
                """,problem,switch(input.path("stage").asText()){case "기초"->3;case "실무"->4;default->5;},
                    lesson.path("body").asText()+"\n\n---\n\n"+question.path("prompt").asText(),choices.toString(),
                    json.createObjectNode().put("correct",question.path("answer").asText()).toString(),question.path("explanation").asText(),job);
            jdbc.update("insert into daily_career.learning_content_problem(content_version_id,problem_version_id,sequence) values (?,?,?)",version,pv,sequence);
        }
        jdbc.update("""
            insert into daily_career.ai_learning_topic(content_version_id,user_id,ai_job_id,base_content_version_id,stage,target_minutes)
            values (?,?,?,?,?,?)
            """,version,user,job,input.hasNonNull("baseContentVersionId")?Long.valueOf(input.path("baseContentVersionId").asText()):null,input.path("stage").asText(),input.path("targetMinutes").asInt());
        jdbc.update("update daily_career.learning_content_version set status='PUBLISHED',published_at=clock_timestamp(),updated_at=clock_timestamp(),revision=revision+1 where id=?",version);
        return receipt(version,lesson,input,false);
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> list(long user) {
        users.current(user);
        return jdbc.queryForList("""
            select id::text as "jobId",input_snapshot->>'topic' as topic,input_snapshot->>'subject' as subject,
                input_snapshot->>'stage' as stage,(input_snapshot->>'targetMinutes')::int as "targetMinutes",
                status,"error_code" as "errorCode",created_at as "createdAt",
                case when status='SUCCEEDED' then result_payload->>'contentVersionId' else null end as "contentVersionId",
                case when status='SUCCEEDED' then result_payload->>'title' else null end as title
            from daily_career.ai_job where user_id=? and job_type='LEARNING_CONTENT'
            order by created_at desc,id desc limit 20
            """,user);
    }
    private ObjectNode receipt(long id,JsonNode lesson,JsonNode input,boolean reused){return json.createObjectNode().put("type",AiLessonContent.PURPOSE).put("contentVersionId",Long.toString(id)).put("title",lesson.path("title").asText()).put("subject",input.path("subject").asText()).put("stage",input.path("stage").asText()).put("targetMinutes",input.path("targetMinutes").asInt()).put("reused",reused);}
    private long insert(String sql,Object... args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
