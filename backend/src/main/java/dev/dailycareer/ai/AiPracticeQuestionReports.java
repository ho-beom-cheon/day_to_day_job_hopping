package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.common.api.ApiException;
import dev.dailycareer.user.UserAccountService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.dailycareer.common.api.ApiErrorCode.*;

@Service
public class AiPracticeQuestionReports {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserAccountService users;

    AiPracticeQuestionReports(JdbcTemplate jdbc, ObjectMapper json, UserAccountService users) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
    }

    @Transactional
    public Map<String, Object> report(long user, long jobId, int questionNo, String reason) {
        users.current(user);
        if (reason == null || reason.isBlank() || reason.strip().length() > 1000)
            throw new ApiException(VALIDATION_FAILED);
        var rows = jdbc.queryForList("""
                select result_payload from daily_career.ai_job
                where id=? and user_id=? and job_type='PRACTICE_PROBLEMS' and status='SUCCEEDED'
                """, jobId, user);
        if (rows.isEmpty()) throw new ApiException(RESOURCE_NOT_FOUND);
        try {
            JsonNode questions = json.readTree(rows.getFirst().get("result_payload").toString()).path("questions");
            if (!questions.isArray() || questionNo < 1 || questionNo > questions.size())
                throw new ApiException(VALIDATION_FAILED);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid stored AI questions", e);
        }
        jdbc.update("""
                insert into daily_career.ai_practice_question_report(user_id,ai_job_id,question_no,reason)
                values (?,?,?,?) on conflict (user_id,ai_job_id,question_no) do nothing
                """, user, jobId, questionNo, reason.strip());
        return Map.of("jobId", Long.toString(jobId), "questionNo", questionNo, "reported", true);
    }

    @Transactional(readOnly = true)
    public Set<Integer> reported(long user, long jobId) {
        return new HashSet<>(jdbc.queryForList("""
                select question_no from daily_career.ai_practice_question_report
                where user_id=? and ai_job_id=?
                """, Integer.class, user, jobId));
    }
}
