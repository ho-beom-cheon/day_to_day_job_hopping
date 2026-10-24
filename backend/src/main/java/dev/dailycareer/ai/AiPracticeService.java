package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
public class AiPracticeService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountService users;
    private final AiPracticeQuestionReports reports;

    AiPracticeService(JdbcTemplate jdbc, ObjectMapper json, UserAccountService users,
                      AiPracticeQuestionReports reports) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> latest(long user, long contentId, Integer difficulty) {
        users.current(user);
        var duration = jdbc.queryForList("""
                select s.estimated_minutes,v.body_markdown,c.status curriculum_status from daily_career.assigned_content a
                join daily_career.assigned_session_detail s on s.learning_day_item_id=a.learning_day_item_id
                  and s.user_id=a.user_id
                join daily_career.learning_content_version v on v.id=a.content_version_id
                join daily_career.learning_day_item i on i.id=a.learning_day_item_id and i.user_id=a.user_id
                join daily_career.user_curriculum c on c.id=i.user_curriculum_id and c.user_id=i.user_id
                where a.id=? and a.user_id=?
                """,contentId,user);
        if (duration.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        var rows = jdbc.queryForList("""
                select id,status,result_payload,error_code,input_snapshot->>'targetDifficulty' target_difficulty
                from daily_career.ai_job
                where user_id=? and job_type='PRACTICE_PROBLEMS' and input_snapshot->>'contentId'=?
                order by id desc limit 1
                """, user, Long.toString(contentId));
        int target = difficulty == null
                ? rows.isEmpty() ? 4 : Integer.parseInt(
                        java.util.Objects.toString(rows.getFirst().get("target_difficulty"), "4"))
                : difficulty;
        if (target < 3 || target > 5) throw new ApiException(VALIDATION_FAILED);
        if (difficulty != null) rows = jdbc.queryForList("""
                select id,status,result_payload,error_code,input_snapshot->>'targetDifficulty' target_difficulty
                from daily_career.ai_job
                where user_id=? and job_type='PRACTICE_PROBLEMS' and input_snapshot->>'contentId'=?
                  and coalesce(input_snapshot->>'targetDifficulty','4')=?
                order by id desc limit 1
                """,user,Long.toString(contentId),Integer.toString(target));
        int estimatedMinutes = ((Number)duration.getFirst().get("estimated_minutes")).intValue();
        String body = duration.getFirst().get("body_markdown").toString();
        AiPracticePlan plan = AiPracticePlan.forMinutes(estimatedMinutes, target);
        List<Integer> availableDifficulties = List.of("PLANNED", "ACTIVE", "PAUSED", "COMPLETED")
                .contains(duration.getFirst().get("curriculum_status"))
                ? AiPracticePlan.availableDifficulties(estimatedMinutes, body) : List.of();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("questionCount",plan.questionCount());
        result.put("estimatedMinutes",plan.estimatedMinutes());
        result.put("targetDifficulty",plan.targetDifficulty());
        result.put("difficultyLevels",plan.difficulties());
        result.put("generationAvailable",availableDifficulties.contains(target));
        result.put("availableDifficulties",availableDifficulties);
        result.put("rejectedQuestionCount",0);
        if (rows.isEmpty()) {
            result.put("status","NONE");
            result.put("latestQuestionCount",null);
            return result;
        }
        var row = rows.getFirst();
        result.put("jobId",row.get("id").toString());
        result.put("status",status(row.get("status").toString()));
        result.put("latestQuestionCount",row.get("result_payload")==null?null:
                result(row).path("questions").size());
        result.put("qualityRejected","AI_PRACTICE_QUALITY_REJECTED".equals(row.get("error_code")));
        if ("SUCCEEDED".equals(row.get("status")))
            result.put("rejectedQuestionCount",result(row).path("rejectedQuestionCount").asInt(0));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> saved(long user) {
        users.current(user);
        return jdbc.queryForList("""
                select id,input_snapshot->>'contentId' content_id,
                       coalesce(input_snapshot->>'title','AI 확인 문제') title,
                       jsonb_array_length(result_payload->'questions') -
                         (select count(*)::int from daily_career.ai_practice_question_report q
                          where q.user_id=j.user_id and q.ai_job_id=j.id) question_count,
                       coalesce((input_snapshot->>'generationSequence')::int,1) generation_sequence
                from daily_career.ai_job j
                where j.user_id=? and j.job_type='PRACTICE_PROBLEMS' and j.status='SUCCEEDED'
                  and jsonb_array_length(result_payload->'questions') >
                    (select count(*) from daily_career.ai_practice_question_report q
                     where q.user_id=j.user_id and q.ai_job_id=j.id)
                  and exists (select 1 from daily_career.assigned_content a
                    join daily_career.learning_day_item i on i.id=a.learning_day_item_id
                    join daily_career.user_curriculum c on c.id=i.user_curriculum_id
                    where a.id::text=j.input_snapshot->>'contentId' and a.user_id=j.user_id
                      and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED','CANCELED'))
                order by j.id desc
                """, user).stream().map(row -> Map.<String,Object>of(
                "jobId", row.get("id").toString(),
                "contentId", row.get("content_id").toString(),
                "title", row.get("title").toString(),
                "generationSequence",((Number)row.get("generation_sequence")).intValue(),
                "questionCount",((Number)row.get("question_count")).intValue())).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> set(long user, long jobId) {
        var job = job(user, jobId);
        JsonNode result = result(job);
        var answers = attempts(user, jobId);
        var reported = reports.reported(user, jobId);
        List<Map<String, Object>> questions = new ArrayList<>();
        int number = 0;
        for (JsonNode item : result.path("questions")) {
            number++;
            questions.add(question(number, item, answers.get(number), reported.contains(number)));
        }
        return Map.of("jobId", Long.toString(jobId), "contentId", result.path("contentId").asText(),
                "sessionId", result.path("sessionId").asText(), "questions", questions);
    }

    @Transactional
    public Map<String, Object> submit(long user, long jobId, int questionNo, int answerNo) {
        var job = job(user, jobId);
        JsonNode result = result(job);
        JsonNode questions = result.path("questions");
        if (questionNo < 1 || questionNo > questions.size() || answerNo < 1 || answerNo > 4)
            throw new ApiException(VALIDATION_FAILED);
        JsonNode item = questions.get(questionNo - 1);
        int inserted = jdbc.update("""
                insert into daily_career.ai_practice_attempt(user_id,ai_job_id,question_no,answer_no,correct)
                values (?,?,?,?,?) on conflict (user_id,ai_job_id,question_no) do nothing
                """, user, jobId, questionNo, answerNo, answerNo == item.path("answer").asInt());
        boolean reported = reports.reported(user, jobId).contains(questionNo);
        if (inserted == 1 && !reported && answerNo != item.path("answer").asInt())
            AiPracticeWrongRecorder.record(jdbc, user, jobId, questionNo);
        var rows = jdbc.queryForList("""
                select question_no,answer_no,correct from daily_career.ai_practice_attempt
                where user_id=? and ai_job_id=? and question_no=?
                """, user, jobId, questionNo);
        return question(questionNo, item, rows.getFirst(), reported);
    }

    private Map<String, Object> job(long user, long jobId) {
        users.current(user);
        var rows = jdbc.queryForList("""
                select id,status,result_payload from daily_career.ai_job
                where id=? and user_id=? and job_type='PRACTICE_PROBLEMS'
                """, jobId, user);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private JsonNode result(Map<String, Object> job) {
        if (!"SUCCEEDED".equals(job.get("status")) || job.get("result_payload") == null)
            throw new ApiException(CONTENT_NOT_READY);
        try { return json.readTree(job.get("result_payload").toString()); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored AI questions", e); }
    }

    private Map<Integer, Map<String, Object>> attempts(long user, long jobId) {
        Map<Integer, Map<String, Object>> found = new LinkedHashMap<>();
        jdbc.queryForList("""
                select question_no,answer_no,correct from daily_career.ai_practice_attempt
                where user_id=? and ai_job_id=? order by question_no
                """, user, jobId).forEach(row -> found.put(((Number) row.get("question_no")).intValue(), row));
        return found;
    }

    private static Map<String, Object> question(int number, JsonNode item, Map<String, Object> submitted,
                                                boolean reported) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("number", number);
        if (item.has("difficulty")) view.put("difficulty",item.path("difficulty").asInt());
        view.put("prompt", item.path("prompt").asText());
        view.put("reported", reported);
        List<String> choices = new ArrayList<>();
        item.path("choices").forEach(choice -> choices.add(choice.asText()));
        view.put("choices", choices);
        view.put("submittedAnswer", submitted == null ? null : ((Number) submitted.get("answer_no")).intValue());
        view.put("correct", submitted == null ? null : submitted.get("correct"));
        view.put("correctAnswer", submitted == null ? null : item.path("answer").asInt());
        view.put("explanation", submitted == null ? null : item.path("explanation").asText());
        view.put("sourceExcerpt", submitted == null ? null : item.path("sourceExcerpt").asText(null));
        return view;
    }

    private static String status(String value) {
        return switch (value) {
            case "QUEUED" -> "READY";
            case "RUNNING" -> "PROCESSING";
            case "SUCCEEDED" -> "SUCCESS";
            case "UNKNOWN" -> "UNKNOWN";
            default -> "FAILED";
        };
    }
}
