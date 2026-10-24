package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
public class AiPracticeReuseService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountService users;
    private final AiPracticeQuestionReports reports;

    AiPracticeReuseService(JdbcTemplate jdbc, ObjectMapper json, UserAccountService users,
                           AiPracticeQuestionReports reports) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
        this.reports = reports;
    }

    @Transactional
    public Map<String, Object> start(long user, long jobId, String modeText) {
        users.current(user);
        String mode = mode(modeText);
        var jobs = jdbc.queryForList("""
                select id,result_payload from daily_career.ai_job
                where id=? and user_id=? and job_type='PRACTICE_PROBLEMS' and status='SUCCEEDED'
                for update
                """, jobId, user);
        if (jobs.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        JsonNode result = stored(jobs.getFirst().get("result_payload"));
        Set<Integer> reported = reports.reported(user, jobId);
        List<Integer> numbers = new ArrayList<>();
        if ("REVIEW".equals(mode)) {
            jdbc.queryForList("""
                    select question_no from daily_career.ai_practice_wrong_note
                    where user_id=? and ai_job_id=? and status='OPEN' order by question_no
                    """, Integer.class, user, jobId).forEach(numbers::add);
            numbers.removeIf(reported::contains);
        } else {
            for (int number = 1; number <= result.path("questions").size(); number++)
                if (!reported.contains(number)) numbers.add(number);
        }
        if (numbers.isEmpty()) throw new ApiException(AI_INPUT_NOT_ELIGIBLE);
        var active = jdbc.queryForList("""
                select id from daily_career.ai_practice_run
                where user_id=? and ai_job_id=? and mode=? and status='IN_PROGRESS'
                order by id desc limit 1
                """, Long.class, user, jobId, mode);
        if (!active.isEmpty()) return run(user, active.getFirst());
        Long runId = jdbc.queryForObject("""
                insert into daily_career.ai_practice_run(user_id,ai_job_id,mode,question_numbers)
                values (?,?,?,?::jsonb) returning id
                """, Long.class, user, jobId, mode, json.valueToTree(numbers).toString());
        return run(user, Objects.requireNonNull(runId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> run(long user, long runId) {
        users.current(user);
        return view(row(user, runId, false));
    }

    @Transactional
    public Map<String, Object> submit(long user, long runId, int questionNo, int answerNo) {
        users.current(user);
        var row = row(user, runId, true);
        if ("COMPLETED".equals(row.get("status"))) return view(row);
        JsonNode numbers = stored(row.get("question_numbers"));
        if (answerNo < 1 || answerNo > 4 || !contains(numbers, questionNo))
            throw new ApiException(VALIDATION_FAILED);
        JsonNode result = stored(row.get("result_payload"));
        JsonNode item = result.path("questions").get(questionNo - 1);
        if (item == null) throw new ApiException(CONTENT_NOT_READY);
        long jobId = number(row, "ai_job_id");
        Set<Integer> reported = reports.reported(user, jobId);
        boolean correct = answerNo == item.path("answer").asInt();
        int inserted = jdbc.update("""
                insert into daily_career.ai_practice_run_answer(user_id,run_id,question_no,answer_no,correct)
                values (?,?,?,?,?) on conflict (run_id,question_no) do nothing
                """, user, runId, questionNo, answerNo, correct);
        String mode = row.get("mode").toString();
        if (inserted == 1 && !reported.contains(questionNo) && !"EXAM".equals(mode)) {
            if (!correct) AiPracticeWrongRecorder.record(jdbc, user, jobId, questionNo);
            else if ("REVIEW".equals(mode)) AiPracticeWrongRecorder.resolve(jdbc, user, jobId, questionNo);
        }
        Integer answered = jdbc.queryForObject("""
                select count(*)::int from daily_career.ai_practice_run_answer where run_id=? and user_id=?
                """, Integer.class, runId, user);
        if (answered != null && answered == numbers.size()) {
            jdbc.update("""
                    update daily_career.ai_practice_run
                    set status='COMPLETED',completed_at=clock_timestamp()
                    where id=? and user_id=? and status='IN_PROGRESS'
                    """, runId, user);
            if ("EXAM".equals(mode)) {
                jdbc.queryForList("""
                        select question_no from daily_career.ai_practice_run_answer
                        where run_id=? and user_id=? and correct=false
                        """, Integer.class, runId, user).forEach(wrong -> {
                    if (!reported.contains(wrong)) AiPracticeWrongRecorder.record(jdbc, user, jobId, wrong);
                });
            }
        }
        return run(user, runId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> exams(long user) {
        users.current(user);
        return jdbc.queryForList("""
                select r.id,r.ai_job_id,r.status,r.question_numbers::text,j.input_snapshot->>'title' title,
                       (select count(*)::int from daily_career.ai_practice_run_answer a
                        where a.run_id=r.id and a.correct
                          and not exists (select 1 from daily_career.ai_practice_question_report q
                                          where q.user_id=a.user_id and q.ai_job_id=r.ai_job_id
                                            and q.question_no=a.question_no)) correct_count
                from daily_career.ai_practice_run r
                join daily_career.ai_job j on j.id=r.ai_job_id and j.user_id=r.user_id
                where r.user_id=? and r.mode='EXAM'
                  and exists (select 1 from daily_career.assigned_content a
                    join daily_career.learning_day_item i on i.id=a.learning_day_item_id
                    join daily_career.user_curriculum c on c.id=i.user_curriculum_id
                    where a.id::text=j.input_snapshot->>'contentId' and a.user_id=j.user_id
                      and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED','CANCELED'))
                order by r.id desc limit 50
                """, user).stream().map(row -> {
            Map<String, Object> exam = new LinkedHashMap<>();
            exam.put("runId", row.get("id").toString());
            exam.put("jobId", row.get("ai_job_id").toString());
            exam.put("title", Objects.toString(row.get("title"), "AI 연습 시험"));
            exam.put("status", row.get("status"));
            Set<Integer> reported = reports.reported(user, Long.parseLong(row.get("ai_job_id").toString()));
            int total = 0;
            for (JsonNode value : stored(row.get("question_numbers")))
                if (!reported.contains(value.asInt())) total++;
            exam.put("totalCount", total);
            exam.put("correctCount", "COMPLETED".equals(row.get("status"))
                    ? ((Number) row.get("correct_count")).intValue() : null);
            return exam;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> wrongs(long user, String status) {
        users.current(user);
        if (status != null && !List.of("OPEN", "RESOLVED").contains(status))
            throw new ApiException(VALIDATION_FAILED);
        var rows = status == null ? jdbc.queryForList("""
                select w.*,j.result_payload from daily_career.ai_practice_wrong_note w
                join daily_career.ai_job j on j.id=w.ai_job_id and j.user_id=w.user_id
                where w.user_id=? and not exists (select 1 from daily_career.ai_practice_question_report q
                    where q.user_id=w.user_id and q.ai_job_id=w.ai_job_id and q.question_no=w.question_no)
                  and exists (select 1 from daily_career.assigned_content a
                    join daily_career.learning_day_item i on i.id=a.learning_day_item_id
                    join daily_career.user_curriculum c on c.id=i.user_curriculum_id
                    where a.id::text=j.input_snapshot->>'contentId' and a.user_id=j.user_id
                      and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED','CANCELED'))
                order by w.last_wrong_at desc
                """, user) : jdbc.queryForList("""
                select w.*,j.result_payload from daily_career.ai_practice_wrong_note w
                join daily_career.ai_job j on j.id=w.ai_job_id and j.user_id=w.user_id
                where w.user_id=? and w.status=?
                  and not exists (select 1 from daily_career.ai_practice_question_report q
                    where q.user_id=w.user_id and q.ai_job_id=w.ai_job_id and q.question_no=w.question_no)
                  and exists (select 1 from daily_career.assigned_content a
                    join daily_career.learning_day_item i on i.id=a.learning_day_item_id
                    join daily_career.user_curriculum c on c.id=i.user_curriculum_id
                    where a.id::text=j.input_snapshot->>'contentId' and a.user_id=j.user_id
                      and c.status in ('PLANNED','ACTIVE','PAUSED','COMPLETED','CANCELED'))
                order by w.last_wrong_at desc
                """, user, status);
        return rows.stream().map(this::wrongSummary).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> wrong(long user, long jobId, int questionNo) {
        users.current(user);
        var rows = jdbc.queryForList("""
                select w.*,j.result_payload from daily_career.ai_practice_wrong_note w
                join daily_career.ai_job j on j.id=w.ai_job_id and j.user_id=w.user_id
                where w.user_id=? and w.ai_job_id=? and w.question_no=?
                  and not exists (select 1 from daily_career.ai_practice_question_report q
                    where q.user_id=w.user_id and q.ai_job_id=w.ai_job_id and q.question_no=w.question_no)
                """, user, jobId, questionNo);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        var row = rows.getFirst();
        JsonNode item = stored(row.get("result_payload")).path("questions").get(questionNo - 1);
        if (item == null) throw new ApiException(CONTENT_NOT_READY);
        Map<String, Object> detail = new LinkedHashMap<>(wrongSummary(row));
        detail.put("choices", strings(item.path("choices")));
        detail.put("correctAnswer", item.path("answer").asInt());
        detail.put("explanation", item.path("explanation").asText());
        detail.put("note", row.get("note_text"));
        return detail;
    }

    @Transactional
    public Map<String, Object> updateWrong(long user, long jobId, int questionNo, String note, String status) {
        var current = wrong(user, jobId, questionNo);
        if (note == null && status == null) throw new ApiException(VALIDATION_FAILED);
        if (note != null && note.length() > 10000) throw new ApiException(VALIDATION_FAILED);
        if (status != null && !List.of("OPEN", "RESOLVED").contains(status))
            throw new ApiException(VALIDATION_FAILED);
        jdbc.update("""
                update daily_career.ai_practice_wrong_note
                set note_text=?,status=?,updated_at=clock_timestamp()
                where user_id=? and ai_job_id=? and question_no=?
                """, note == null ? current.get("note") : note,
                status == null ? current.get("status") : status, user, jobId, questionNo);
        return wrong(user, jobId, questionNo);
    }

    private Map<String, Object> row(long user, long runId, boolean lock) {
        String sql = """
                select r.*,j.result_payload from daily_career.ai_practice_run r
                join daily_career.ai_job j on j.id=r.ai_job_id and j.user_id=r.user_id
                where r.id=? and r.user_id=? and j.job_type='PRACTICE_PROBLEMS' and j.status='SUCCEEDED'
                """ + (lock ? " for update of r" : "");
        var rows = jdbc.queryForList(sql, runId, user);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private Map<String, Object> view(Map<String, Object> row) {
        long runId = number(row, "id");
        JsonNode result = stored(row.get("result_payload"));
        JsonNode numbers = stored(row.get("question_numbers"));
        Map<Integer, Map<String, Object>> answers = new LinkedHashMap<>();
        jdbc.queryForList("""
                select question_no,answer_no,correct from daily_career.ai_practice_run_answer
                where run_id=? and user_id=?
                """, runId, row.get("user_id")).forEach(answer ->
                answers.put(((Number) answer.get("question_no")).intValue(), answer));
        boolean completed = "COMPLETED".equals(row.get("status"));
        boolean reveal = completed || !"EXAM".equals(row.get("mode"));
        Set<Integer> reported = reports.reported(number(row, "user_id"), number(row, "ai_job_id"));
        List<Map<String, Object>> questions = new ArrayList<>();
        for (JsonNode value : numbers) {
            int number = value.asInt();
            JsonNode item = result.path("questions").get(number - 1);
            if (item == null) throw new ApiException(CONTENT_NOT_READY);
            var answer = answers.get(number);
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("number", number);
            if (item.has("difficulty")) question.put("difficulty",item.path("difficulty").asInt());
            question.put("prompt", item.path("prompt").asText());
            question.put("reported", reported.contains(number));
            question.put("choices", strings(item.path("choices")));
            question.put("submittedAnswer", answer == null ? null : ((Number) answer.get("answer_no")).intValue());
            question.put("correct", answer == null || !reveal ? null : answer.get("correct"));
            question.put("correctAnswer", answer == null || !reveal ? null : item.path("answer").asInt());
            question.put("explanation", answer == null || !reveal ? null : item.path("explanation").asText());
            question.put("sourceExcerpt", answer == null || !reveal ? null : item.path("sourceExcerpt").asText(null));
            questions.add(question);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runId", Long.toString(runId));
        response.put("jobId", row.get("ai_job_id").toString());
        response.put("contentId", result.path("contentId").asText());
        response.put("sessionId", result.path("sessionId").asText());
        response.put("mode", row.get("mode"));
        response.put("status", row.get("status"));
        response.put("answeredCount", answers.size());
        response.put("totalCount", numbers.size());
        response.put("scoredCount", numbers.size() - reported.stream().filter(value -> contains(numbers, value)).count());
        response.put("correctCount", completed ? answers.entrySet().stream()
                .filter(a -> !reported.contains(a.getKey()) && Boolean.TRUE.equals(a.getValue().get("correct")))
                .count() : null);
        response.put("questions", questions);
        return response;
    }

    private Map<String, Object> wrongSummary(Map<String, Object> row) {
        int number = ((Number) row.get("question_no")).intValue();
        JsonNode item = stored(row.get("result_payload")).path("questions").get(number - 1);
        if (item == null) throw new ApiException(CONTENT_NOT_READY);
        return Map.of("jobId", row.get("ai_job_id").toString(), "questionNo", number,
                "prompt", item.path("prompt").asText(), "status", row.get("status"),
                "wrongCount", ((Number) row.get("wrong_count")).intValue(),
                "lastWrongAt", row.get("last_wrong_at").toString());
    }

    private JsonNode stored(Object raw) {
        try { return json.readTree(raw.toString()); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored AI practice data", e); }
    }

    private static List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static boolean contains(JsonNode values, int number) {
        for (JsonNode value : values) if (value.asInt() == number) return true;
        return false;
    }

    private static long number(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).longValue();
    }

    private static String mode(String value) {
        if (!List.of("STUDY", "EXAM", "REVIEW").contains(value))
            throw new ApiException(VALIDATION_FAILED);
        return value;
    }
}
