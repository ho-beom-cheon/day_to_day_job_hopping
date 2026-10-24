package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotionSnapshotFactoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NotionSnapshotFactory factory = new NotionSnapshotFactory(jdbc, new ObjectMapper());

    @Test void learningSnapshotIncludesStudiedContentInsteadOfOnlyMetadata() {
        Map<String, Object> learning = row(7L, "Java 핵심 다지기", "완료 · 2026-09-26", LocalDate.of(2026, 9, 26));
        learning.put("completed_at", Timestamp.from(Instant.parse("2026-09-26T10:00:00Z")));
        learning.put("body_markdown", "# 컬렉션\nList와 Map의 차이를 학습한다.");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(learning));

        var snapshots = factory.create(1, 1, List.of("LEARNING_RESULT"),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26));

        assertThat(snapshots).hasSize(1);
        long oldFormatRevision = 7_000_000_000_000_000L
                + Instant.parse("2026-09-26T10:00:00Z").toEpochMilli() * 1000 + 1;
        assertThat(snapshots.getFirst().sourceRevision()).isGreaterThan(oldFormatRevision)
                .isLessThan(9_007_199_254_740_992L);
        assertThat(snapshots.getFirst().payload().path("title").asText()).isEqualTo("Java 핵심 다지기");
        assertThat(snapshots.getFirst().payload().path("details").asText())
                .contains("List와 Map의 차이", "완료 시각");
    }

    @Test void weeklySnapshotShowsEachScheduledItemAndItsStatus() {
        Map<String, Object> weekly = row(3L, "2026-09-21 주간 리포트", "완료 2개 / 전체 9개",
                LocalDate.of(2026, 9, 21));
        Map<String, Object> completed = new HashMap<>();
        completed.put("local_date", LocalDate.of(2026, 9, 21));
        completed.put("day_type", "STUDY");
        completed.put("activity_type", "LEARN");
        completed.put("status", "COMPLETED");
        completed.put("item_title", "Java 핵심 다지기");
        Map<String, Object> pending = new HashMap<>(completed);
        pending.put("activity_type", "PRACTICE");
        pending.put("status", "NOT_STARTED");
        pending.put("item_title", "확인 문제");
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(weekly), List.of(completed, pending));

        var snapshots = factory.create(1, 1, List.of("WEEKLY_REPORT"),
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 26));

        assertThat(snapshots.getFirst().payload().path("details").asText())
                .contains("2026-09-21 · 학습일", "Java 핵심 다지기 · 완료", "확인 문제 · 미시작",
                        "이번 주 완료한 학습", "다음에 이어서 할 학습");
    }

    @Test void submittedButUngratedProblemDoesNotExposeAnswerKeyOrExplanation() {
        Map<String, Object> problem = row(9L, "컬렉션 문제", "채점 대기", LocalDate.of(2026, 9, 26));
        problem.put("prompt_markdown", "List의 순서를 보존하는 구현은?");
        problem.put("choices", "[{\"key\":\"1\",\"text\":\"ArrayList\"}]");
        problem.put("answer_key", "{\"correct\":\"1\"}");
        problem.put("submitted_answer", "1");
        problem.put("explanation_markdown", "서버 전용 해설");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(problem));

        var snapshot = factory.create(1, 1, List.of("PROBLEM"),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26)).getFirst();

        assertThat(snapshot.payload().path("details").asText())
                .contains("보기\n1번 · ArrayList", "내 답\n1번 · ArrayList", "채점\n채점 대기")
                .doesNotContain("정답\n", "서버 전용 해설");
    }

    @Test void submittedNumberAndStoredLetterUseSameChoiceLabel() {
        Map<String, Object> problem = row(8L, "결제 멱등성", "문제 풀이 제출 · 1/1", LocalDate.of(2026, 9, 26));
        problem.put("prompt_markdown", "같은 요청을 한 번만 처리하는 성질은?");
        problem.put("choices", "[{\"id\":\"A\",\"text\":\"멱등성\"},{\"id\":\"B\",\"text\":\"캐시\"}]");
        problem.put("answer_key", "{\"correct\":\"A\"}");
        problem.put("submitted_answer", "1");
        problem.put("earned_score", BigDecimal.ONE);
        problem.put("max_score", BigDecimal.ONE);
        problem.put("is_correct", true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(problem));

        var snapshot = factory.create(1, 1, List.of("PROBLEM"),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26)).getFirst();
        String details = snapshot.payload().path("details").asText();

        assertThat(details).contains("보기\n1번 · 멱등성\n2번 · 캐시",
                "내 답\n1번 · 멱등성", "정답\n1번 · 멱등성", "채점\n정답");
        assertThat(snapshot.payload().path("summary").asText()).isEqualTo("문제 풀이 · 1/1점");
    }

    @Test void testQuestionAlsoResolvesStoredChoiceIdToSubmittedNumber() {
        Map<String, Object> test = row(3L, "1일차 확인 시험", "DAILY 시험", LocalDate.of(2026, 9, 26));
        test.put("score", "10.000/10.000");
        test.put("test_type", "DAILY");
        test.put("attempt_no", 1);
        test.put("submit_reason", "USER");
        Map<String, Object> item = new HashMap<>();
        item.put("display_order", 1);
        item.put("question_snapshot", "{\"question\":\"값 비교 방법은?\"}");
        item.put("grading_snapshot", "{\"choices\":[{\"id\":\"A\",\"text\":\"==\"},{\"id\":\"B\",\"text\":\"equals\"}],\"answerKey\":{\"correct\":\"B\"}}");
        item.put("submitted_answer", "2");
        item.put("earned_score", BigDecimal.TEN);
        item.put("max_score", BigDecimal.TEN);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(test), List.of(item));

        var snapshot = factory.create(1, 1, List.of("TEST_RESULT"),
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26)).getFirst();
        String details = snapshot.payload().path("details").asText();

        assertThat(details).contains("보기\n1번 · ==\n2번 · equals",
                "내 답\n2번 · equals", "정답\n2번 · equals", "제출 방식\n직접 제출", "점수\n10/10점");
        assertThat(snapshot.payload().path("summary").asText()).isEqualTo("일간 시험 1회차 · 제출 완료");
    }

    @Test void fencedChoiceKeepsItsNumberAndCodeBlockThroughRendering() throws Exception {
        ObjectMapper json = new ObjectMapper();
        Map<String, Object> problem = row(8L, "코드 선택", "문제 풀이", LocalDate.of(2026, 9, 26));
        problem.put("prompt_markdown", "옳은 코드는?");
        problem.put("choices", json.writeValueAsString(List.of(Map.of("id", "A", "text", "```java\nreturn true;\n```"))));
        problem.put("answer_key", "{\"correct\":\"A\"}");
        problem.put("submitted_answer", "1");
        problem.put("earned_score", BigDecimal.ONE);
        problem.put("is_correct", true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(problem));

        var snapshot = factory.create(1, 1, List.of("PROBLEM"), LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26)).getFirst();
        var rendered = new NotionPageTemplate(json).render("PROBLEM", snapshot.payload());
        long codeCount = java.util.stream.StreamSupport.stream(rendered.path("paragraph").path("children").spliterator(), false)
                .filter(block -> block.path("type").asText().equals("code")).count();

        assertThat(codeCount).isEqualTo(3); // Choice, submitted answer, and correct answer.
        assertThat(rendered.toString()).contains("1번", "return true;").doesNotContain("```");
    }

    private Map<String, Object> row(long id, String title, String summary, LocalDate date) {
        Map<String, Object> row = new HashMap<>();
        row.put("source_id", id);
        row.put("source_revision", 1L);
        row.put("source_updated_at", Timestamp.from(Instant.parse("2026-09-26T10:00:00Z")));
        row.put("title", title);
        row.put("summary", summary);
        row.put("occurred_at", date);
        return row;
    }
}
