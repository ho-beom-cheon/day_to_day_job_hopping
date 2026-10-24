package dev.dailycareer.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dailycareer.ai.AiPracticePlan;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.common.concurrency.Revisions;
import dev.dailycareer.common.idempotency.IdempotencyService;
import dev.dailycareer.common.idempotency.StoredReply;
import dev.dailycareer.common.json.JsonId;
import dev.dailycareer.user.UserAccountService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
public class CurriculumRestartService {
    public record Restart(@NotNull @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String startDate) {}
    public record SwitchCourse(@NotNull @JsonId Long templateId,
                               @NotNull @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String startDate) {}

    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final LearningQueryService queries;
    private final LearningCommandService commands;
    private final LearningCatalog catalog;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    CurriculumRestartService(JdbcTemplate jdbc, UserAccountService users, LearningQueryService queries,
                             LearningCommandService commands, LearningCatalog catalog,
                             IdempotencyService idempotency, ObjectMapper json) {
        this.jdbc=jdbc; this.users=users; this.queries=queries; this.commands=commands;
        this.catalog=catalog; this.idempotency=idempotency; this.json=json;
    }

    @Transactional
    public StoredReply restart(long user, long courseId, Restart input, int expected, UUID key) {
        return replace(user,courseId,input.startDate(),null,expected,key);
    }

    @Transactional
    public StoredReply switchCourse(long user, long courseId, SwitchCourse input, int expected, UUID key) {
        return replace(user,courseId,input.startDate(),input.templateId(),expected,key);
    }

    private StoredReply replace(long user, long courseId, String startDate, Long targetTemplateId, int expected, UUID key) {
        users.current(user);
        LocalDate start=LearningCommandService.parseDate(startDate);
        if (start.isBefore(queries.today()) || start.isAfter(queries.today().plusDays(90)))
            throw new ApiException(VALIDATION_FAILED);
        // Authorization also applies to an idempotent replay.
        if (jdbc.queryForList("select id from daily_career.user_curriculum where id=? and user_id=?",Long.class,courseId,user).isEmpty())
            throw new ApiException(RESOURCE_NOT_FOUND);
        var request=json.createObjectNode().put("curriculumId",Long.toString(courseId))
                .put("startDate",startDate).put("revision",expected);
        if (targetTemplateId!=null) request.put("templateId",Long.toString(targetTemplateId));
        return idempotency.execute(user,targetTemplateId==null?"CURR-009":"CURR-010",key,request,()->{
            jdbc.queryForList("select id from daily_career.app_user where id=? for no key update",user);
            var rows=jdbc.queryForList("""
                    select id,schedule_revision,status from daily_career.user_curriculum
                    where id=? and user_id=? for update
                    """,courseId,user);
            if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
            var row=rows.getFirst();
            Revisions.requireMatch(expected,((Number)row.get("schedule_revision")).longValue());
            if (!Objects.equals(queries.currentId(user),courseId)) throw new ApiException(PRECONDITION_FAILED);
            if (!Set.of("PLANNED","ACTIVE","PAUSED","COMPLETED").contains(row.get("status")))
                throw new ApiException(PRECONDITION_FAILED);
            var previous=queries.curriculum(user,courseId);
            long chosenTemplate=targetTemplateId==null?previous.templateId():targetTemplateId;
            if (targetTemplateId!=null && chosenTemplate==previous.templateId()) throw new ApiException(VALIDATION_FAILED);
            catalog.plan(chosenTemplate,true);
            var policy=previous.studyPolicy();
            var rest=policy.restWeekdays().stream().map(java.time.DayOfWeek::valueOf).toList();
            jdbc.update("""
                    update daily_career.test_attempt set status='VOIDED',revision=revision+1,
                        updated_at=clock_timestamp()
                    where user_id=? and user_curriculum_id=? and status='IN_PROGRESS'
                    """,user,courseId);
            jdbc.update("""
                    update daily_career.user_curriculum
                    set status='CANCELED',schedule_revision=schedule_revision+1,revision=revision+1,
                        updated_at=clock_timestamp() where id=? and user_id=?
                    """,courseId,user);
            var created=commands.create(user,new LearningCommandService.Create(chosenTemplate,
                    startDate,new LearningCommandService.Policy(policy.dailyStudyMinutes(),
                    policy.restDaysPerWeek(),rest)),UUID.randomUUID());
            Long newCourse=queries.currentId(user);
            if (newCourse==null || newCourse==courseId) throw new IllegalStateException("Restart did not assign a course");
            if (targetTemplateId==null) reuseQuestions(user,newCourse);
            return created;
        });
    }

    private void reuseQuestions(long user,long course) {
        var contents=jdbc.queryForList("""
                select a.id content_id,a.content_version_id,a.learning_day_item_id session_id,
                       s.estimated_minutes
                from daily_career.assigned_content a
                join daily_career.assigned_session_detail s
                  on s.learning_day_item_id=a.learning_day_item_id and s.user_id=a.user_id
                where a.user_id=? and s.user_id=? and a.completion_rule='ALL_PROBLEMS_ATTEMPTED'
                  and exists (select 1 from daily_career.learning_day_item i
                              where i.id=a.learning_day_item_id and i.user_curriculum_id=?)
                """,user,user,course);
        for (var content:contents) {
            var sources=jdbc.queryForList("""
                    select j.* from daily_career.ai_job j
                    join daily_career.assigned_content a
                      on a.id::text=j.input_snapshot->>'contentId' and a.user_id=j.user_id
                    where j.user_id=? and j.job_type='PRACTICE_PROBLEMS' and j.status='SUCCEEDED'
                      and j.reused_from_job_id is null and a.content_version_id=?
                    order by j.id desc
                    """,user,content.get("content_version_id"));
            Set<Integer> selected=new HashSet<>();
            for (var source:sources) {
                JsonNode snapshot=parse(source.get("input_snapshot"));
                JsonNode result=parse(source.get("result_payload"));
                int difficulty=snapshot.path("targetDifficulty").asInt(0);
                if (difficulty<3 || difficulty>5 || selected.contains(difficulty)) continue;
                AiPracticePlan plan=AiPracticePlan.forMinutes(((Number)content.get("estimated_minutes")).intValue(),difficulty);
                if (snapshot.path("questionCount").asInt()!=plan.questionCount()
                        || !snapshot.path("difficultyLevels").equals(json.valueToTree(plan.difficulties()))
                        || result.path("questions").size()!=plan.questionCount()) continue;
                ObjectNode nextSnapshot=((ObjectNode)snapshot).deepCopy();
                nextSnapshot.put("contentId",content.get("content_id").toString());
                nextSnapshot.put("sessionId",content.get("session_id").toString());
                ObjectNode nextResult=((ObjectNode)result).deepCopy();
                nextResult.put("contentId",content.get("content_id").toString());
                nextResult.put("sessionId",content.get("session_id").toString());
                jdbc.update("""
                        insert into daily_career.ai_job(user_id,budget_id,job_type,dedupe_key,status,
                          provider,model,prompt_version,input_snapshot,result_payload,finished_at,reused_from_job_id)
                        values (?,?,'PRACTICE_PROBLEMS',?,'SUCCEEDED',?,?,?,?::jsonb,?::jsonb,clock_timestamp(),?)
                        """,user,source.get("budget_id"),"reused:"+UUID.randomUUID(),
                        source.get("provider"),source.get("model"),source.get("prompt_version"),
                        nextSnapshot.toString(),nextResult.toString(),source.get("id"));
                selected.add(difficulty);
            }
        }
    }

    private JsonNode parse(Object value) {
        try { return json.readTree(value.toString()); }
        catch (Exception e) { throw new IllegalStateException("Invalid saved AI practice set",e); }
    }
}
