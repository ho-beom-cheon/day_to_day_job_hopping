package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dailycareer.user.UserAccountService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPracticeReuseServiceTest {
    private static final String QUESTIONS = """
            {"contentId":"2","sessionId":"3","questions":[
              {"prompt":"첫 문제","choices":["A","B","C","D"],"answer":2,"explanation":"해설 1"},
              {"prompt":"둘째 문제","choices":["A","B","C","D"],"answer":3,"explanation":"해설 2"}]}
            """;

    @Test
    void examHidesAllFeedbackUntilEveryAnswerIsSubmitted() {
        var inProgress = view("EXAM", "IN_PROGRESS");
        assertThat(inProgress.get("correctCount")).isNull();
        var questions = (List<Map<String, Object>>) inProgress.get("questions");
        assertThat(questions.getFirst()).containsEntry("submittedAnswer", 1)
                .containsEntry("correct", null).containsEntry("correctAnswer", null)
                .containsEntry("explanation", null);
        assertThat(questions.get(1)).containsEntry("submittedAnswer", null)
                .containsEntry("correctAnswer", null);

        var completed = view("EXAM", "COMPLETED");
        assertThat(completed.get("correctCount")).isEqualTo(0L);
        var revealed = (List<Map<String, Object>>) completed.get("questions");
        assertThat(revealed.getFirst()).containsEntry("correct", false)
                .containsEntry("correctAnswer", 2).containsEntry("explanation", "해설 1");
    }

    @Test
    void examHistoryHidesScoreWhileInProgress() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 4L, "ai_job_id", 8L, "title", "개인 시험",
                        "status", "IN_PROGRESS", "question_numbers", "[1,2]", "correct_count", 1),
                Map.of("id", 3L, "ai_job_id", 8L, "title", "개인 시험",
                        "status", "COMPLETED", "question_numbers", "[1,2]", "correct_count", 2)));
        var reports = mock(AiPracticeQuestionReports.class);
        when(reports.reported(7L, 8L)).thenReturn(Set.of());
        var history = new AiPracticeReuseService(jdbc, new ObjectMapper(), mock(UserAccountService.class), reports).exams(7L);

        assertThat(history.getFirst().get("correctCount")).isNull();
        assertThat(history.get(1).get("correctCount")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportedQuestionIsExcludedFromPracticeScore() {
        var completed = view("EXAM", "COMPLETED", Set.of(1));
        assertThat(completed.get("scoredCount")).isEqualTo(1L);
        var questions = (List<Map<String, Object>>) completed.get("questions");
        assertThat(questions.getFirst()).containsEntry("reported", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> view(String mode, String status) {
        return view(mode, status, Set.of());
    }

    private Map<String, Object> view(String mode, String status, Set<Integer> reported) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("from daily_career.ai_practice_run r")) {
                return List.of(Map.of("id", 4L, "user_id", 7L, "ai_job_id", 8L,
                        "mode", mode, "status", status, "question_numbers", "[1,2]",
                        "result_payload", QUESTIONS));
            }
            return List.of(Map.of("question_no", 1, "answer_no", 1, "correct", false));
        });
        var reports = mock(AiPracticeQuestionReports.class);
        when(reports.reported(7L, 8L)).thenReturn(reported);
        return new AiPracticeReuseService(jdbc, new ObjectMapper(), mock(UserAccountService.class), reports).run(7L, 4L);
    }
}
