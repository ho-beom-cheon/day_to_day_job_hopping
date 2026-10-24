package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class NotionSnapshotFactory {
    record Snapshot(String entityType, long sourceId, long sourceRevision, JsonNode payload) {}
    private static final long CONTENT_FORMAT_REVISION = 7_000_000_000_000_000L;
    private static final long LEARNING_FORMAT_REVISION = 1_000_000_000_000L;
    private static final DateTimeFormatter KOREAN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'KST'");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    NotionSnapshotFactory(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    List<Snapshot> create(long user, long curriculum, List<String> entityTypes, LocalDate from, LocalDate to) {
        List<Snapshot> snapshots = new ArrayList<>();
        for (String entity : entityTypes) {
            List<Map<String, Object>> rows = switch (entity) {
                case "LEARNING_RESULT" -> learning(user, curriculum, from, to);
                case "PROBLEM" -> problems(user, curriculum, from, to);
                case "TEST_RESULT" -> tests(user, curriculum, from, to);
                case "WRONG_ANSWER" -> wrongAnswers(user, curriculum, from, to);
                case "WEEKLY_REPORT" -> weekly(user, curriculum, from, to);
                case "MONTHLY_REPORT" -> monthly(user, curriculum, from, to);
                default -> List.of();
            };
            for (Map<String, Object> row : rows) snapshots.add(snapshot(entity, row, user, curriculum, from, to));
            if (snapshots.size() > 1000) throw new IllegalArgumentException("Notion sync item limit exceeded");
        }
        return snapshots;
    }

    private List<Map<String, Object>> learning(long user, long curriculum, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select item.id source_id,item.revision+1 source_revision,item.updated_at source_updated_at,
                       coalesce(content.title,unit.title,concat(item.activity_type,' 학습 결과')) title,
                       concat('완료 · ',day.local_date) summary,day.local_date occurred_at,
                       item.completed_at,version.body_markdown
                from daily_career.learning_day_item item
                join daily_career.learning_day day on day.id=item.learning_day_id and day.user_id=item.user_id
                left join daily_career.curriculum_unit unit on unit.id=item.curriculum_unit_id
                left join daily_career.learning_content_version version on version.id=item.content_version_id
                left join daily_career.learning_content content on content.id=version.learning_content_id
                where item.user_id=? and item.user_curriculum_id=? and item.status='COMPLETED'
                  and day.local_date between ? and ? order by item.id
                """, user, curriculum, from, to);
    }

    private List<Map<String, Object>> problems(long user, long curriculum, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select attempt.id source_id,attempt.revision+1 source_revision,
                       greatest(attempt.updated_at,coalesce(result.updated_at,attempt.updated_at)) source_updated_at,problem.title,
                       concat('문제 풀이 제출 · ',coalesce(result.earned_score::text,'채점 대기'),'/',coalesce(result.max_score::text,'-')) summary,
                       (attempt.submitted_at at time zone 'Asia/Seoul')::date occurred_at,
                       version.prompt_markdown,version.choices::text choices,version.answer_key::text answer_key,
                       version.explanation_markdown,attempt.answer_payload->>'answer' submitted_answer,
                       result.earned_score,result.max_score,result.is_correct,result.feedback_markdown
                from daily_career.problem_attempt attempt
                join daily_career.problem_version version on version.id=attempt.problem_version_id
                join daily_career.problem problem on problem.id=version.problem_id
                left join lateral (
                    select grade.earned_score,grade.max_score,grade.is_correct,grade.feedback_markdown,run.updated_at
                    from daily_career.grading_result grade
                    join daily_career.grading_run run on run.id=grade.grading_run_id
                    where grade.problem_attempt_id=attempt.id and run.is_current and run.status='SUCCEEDED' limit 1
                ) result on true
                where attempt.user_id=? and attempt.user_curriculum_id=? and attempt.status='SUBMITTED'
                  and (attempt.submitted_at at time zone 'Asia/Seoul')::date between ? and ? order by attempt.id
                """, user, curriculum, from, to);
    }

    private List<Map<String, Object>> tests(long user, long curriculum, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select attempt.id source_id,attempt.revision+1 source_revision,
                       greatest(attempt.updated_at,coalesce(run.updated_at,attempt.updated_at),
                           coalesce((select max(item.updated_at) from daily_career.test_attempt_item item
                               where item.test_attempt_id=attempt.id),attempt.updated_at)) source_updated_at,test.title,
                       concat(test.test_type,' 시험 ',attempt.attempt_no,'회차 · ',attempt.status) summary,
                       (attempt.submitted_at at time zone 'Asia/Seoul')::date occurred_at,attempt.submit_reason,
                       test.test_type,attempt.attempt_no,
                       (select concat(sum(result.earned_score),'/',sum(result.max_score))
                        from daily_career.grading_result result where result.grading_run_id=run.id) score
                from daily_career.test_attempt attempt
                join daily_career.test_version version on version.id=attempt.test_version_id
                join daily_career.test test on test.id=version.test_id
                left join daily_career.grading_run run on run.test_attempt_id=attempt.id and run.is_current and run.status='SUCCEEDED'
                where attempt.user_id=? and attempt.user_curriculum_id=? and attempt.status='SUBMITTED'
                  and (attempt.submitted_at at time zone 'Asia/Seoul')::date between ? and ? order by attempt.id
                """, user, curriculum, from, to);
    }

    private List<Map<String, Object>> wrongAnswers(long user, long curriculum, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select distinct on (wrong.id) wrong.id source_id,wrong.revision+1 source_revision,
                       greatest(wrong.updated_at,occurrence.updated_at) source_updated_at,problem.title,
                       concat('오답 상태: ',wrong.status,coalesce(' · '||wrong.user_note,'')) summary,
                       (wrong.first_wrong_at at time zone 'Asia/Seoul')::date occurred_at,
                       wrong.status,wrong.user_note,occurrence.analysis_markdown,
                       version.prompt_markdown,version.choices::text choices,version.answer_key::text answer_key,
                       version.explanation_markdown,attempt.answer_payload->>'answer' submitted_answer,
                       grade.feedback_markdown
                from daily_career.wrong_answer wrong
                join daily_career.problem problem on problem.id=wrong.problem_id
                join daily_career.wrong_answer_occurrence occurrence on occurrence.wrong_answer_id=wrong.id
                join daily_career.grading_result grade on grade.id=occurrence.grading_result_id
                join daily_career.problem_attempt attempt on attempt.id=grade.problem_attempt_id
                join daily_career.problem_version version on version.id=attempt.problem_version_id
                where wrong.user_id=? and attempt.user_curriculum_id=?
                  and (wrong.first_wrong_at at time zone 'Asia/Seoul')::date between ? and ?
                order by wrong.id,occurrence.id desc
                """, user, curriculum, from, to);
    }

    private List<Map<String, Object>> weekly(long user, long curriculum, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select min(day.id) source_id,max(day.revision)+1 source_revision,
                       greatest(max(day.updated_at),max(item.updated_at)) source_updated_at,
                       concat(to_char(date_trunc('week',day.local_date),'YYYY-MM-DD'),' 주간 리포트') title,
                       concat('완료 ',count(*) filter (where item.status='COMPLETED'),'개 / 전체 ',count(item.id),'개') summary,
                       date_trunc('week',day.local_date)::date occurred_at
                from daily_career.learning_day day
                left join daily_career.learning_day_item item on item.learning_day_id=day.id and item.user_id=day.user_id
                where day.user_id=? and day.user_curriculum_id=? and day.local_date between ? and ?
                group by date_trunc('week',day.local_date) order by occurred_at
                """, user, curriculum, from, to);
    }

    private List<Map<String, Object>> monthly(long user, long curriculum, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select evaluation.id source_id,evaluation.revision+1 source_revision,evaluation.updated_at source_updated_at,
                       concat(to_char(evaluation.month_start,'YYYY-MM'),' 월간 리포트') title,
                       concat('보고서 버전 ',evaluation.report_version,' · ',evaluation.status) summary,
                       evaluation.month_start occurred_at,evaluation.metrics_snapshot::text metrics_snapshot,
                       evaluation.report_payload::text report_payload
                from daily_career.monthly_evaluation evaluation
                where evaluation.user_id=? and evaluation.user_curriculum_id=? and evaluation.status='READY'
                  and evaluation.month_start between date_trunc('month',?::date)::date and ? order by evaluation.id
                """, user, curriculum, from, to);
    }

    private Snapshot snapshot(String entity, Map<String, Object> row, long user, long curriculum,
                              LocalDate from, LocalDate to) {
        long source = ((Number) row.get("source_id")).longValue();
        long base = Math.max(1, ((Number) row.get("source_revision")).longValue());
        Object updated = row.get("source_updated_at");
        long millis = updated instanceof Timestamp stamp ? stamp.toInstant().toEpochMilli()
                : updated instanceof OffsetDateTime date ? date.toInstant().toEpochMilli() : 0;
        long revision = CONTENT_FORMAT_REVISION
                + ("LEARNING_RESULT".equals(entity) ? LEARNING_FORMAT_REVISION : 0)
                + millis * 1000 + Math.floorMod(base, 1000);
        String details = switch (entity) {
            case "LEARNING_RESULT" -> learningDetails(row);
            case "PROBLEM" -> problemDetails(row, false);
            case "TEST_RESULT" -> testDetails(row, user);
            case "WRONG_ANSWER" -> problemDetails(row, true);
            case "WEEKLY_REPORT" -> weeklyDetails(row, user, curriculum, from, to);
            case "MONTHLY_REPORT" -> monthlyDetails(row);
            default -> row.get("summary").toString();
        };
        if (details.length() > 30_000) details = details.substring(0, 29_900)
                + "\n\n내용이 길어 나머지는 Daily Leap 앱에서 확인하세요.";
        String summary = summary(entity, row);
        return new Snapshot(entity, source, revision, json.valueToTree(Map.of(
                "title", row.get("title").toString(),
                "summary", summary,
                "occurredAt", row.get("occurred_at").toString(),
                "details", details)));
    }

    private String learningDetails(Map<String, Object> row) {
        StringBuilder body = new StringBuilder("학습 주제: ").append(row.get("title"));
        Object completed = row.get("completed_at");
        if (completed instanceof Timestamp stamp) append(body, "완료 시각", KOREAN_TIME.format(stamp.toInstant().atZone(ZoneId.of("Asia/Seoul"))));
        else append(body, "완료 시각", completed);
        append(body, "학습 자료", row.get("body_markdown"));
        if (row.get("body_markdown") == null) append(body, "안내", "연결된 학습 본문이 없는 활동입니다.");
        return body.toString();
    }

    private String problemDetails(Map<String, Object> row, boolean wrong) {
        StringBuilder body = new StringBuilder(wrong ? "오답 복습" : "문제 풀이");
        append(body, "문제", row.get("prompt_markdown"));
        appendChoices(body, row.get("choices"));
        append(body, "내 답", answer(row.get("submitted_answer"), row.get("choices"), true));
        boolean graded = wrong || row.get("earned_score") != null;
        String correct = parse(row.get("answer_key")).path("correct").asText(null);
        if (graded && correct != null) append(body, "정답", answer(correct, row.get("choices"), false));
        if (wrong) {
            append(body, "오답 상태", row.get("status"));
            append(body, "내 메모", row.get("user_note"));
            append(body, "오답 분석", row.get("analysis_markdown"));
        } else append(body, "채점", row.get("is_correct") == null ? "채점 대기"
                : Boolean.TRUE.equals(row.get("is_correct")) ? "정답" : "오답");
        append(body, "피드백", row.get("feedback_markdown"));
        if (graded && row.get("feedback_markdown") == null) append(body, "해설", row.get("explanation_markdown"));
        return body.toString();
    }

    private String testDetails(Map<String, Object> row, long user) {
        StringBuilder body = new StringBuilder("시험 결과: ").append(row.get("title"));
        append(body, "점수", row.get("score") == null ? "채점 대기" : score(row.get("score")) + "점");
        append(body, "제출 방식", submitReason(row.get("submit_reason")));
        List<Map<String, Object>> items = jdbc.queryForList("""
                select item.display_order,item.question_snapshot::text question_snapshot,
                       item.grading_snapshot::text grading_snapshot,item.answer_payload->>'answer' submitted_answer,
                       result.earned_score,result.max_score,result.feedback_markdown
                from daily_career.test_attempt_item item
                left join daily_career.grading_run run on run.test_attempt_id=item.test_attempt_id
                    and run.is_current and run.status='SUCCEEDED'
                left join daily_career.grading_result result on result.grading_run_id=run.id
                    and result.test_attempt_item_id=item.id
                where item.test_attempt_id=? and item.user_id=? order by item.display_order
                """, row.get("source_id"), user);
        for (Map<String, Object> item : items) {
            JsonNode question = parse(item.get("question_snapshot"));
            JsonNode grading = parse(item.get("grading_snapshot"));
            body.append("\n\n문항 ").append(item.get("display_order"));
            append(body, "문제", question.path("question").asText());
            appendChoices(body, grading.path("choices"));
            append(body, "내 답", answer(item.get("submitted_answer"), grading.path("choices"), true));
            if (item.get("earned_score") != null) {
                String correct = grading.path("answerKey").path("correct").asText(null);
                if (correct != null) append(body, "정답", answer(correct, grading.path("choices"), false));
                append(body, "점수", score(item.get("earned_score")) + "/" + score(item.get("max_score")) + "점");
                append(body, "해설", item.get("feedback_markdown") == null
                        ? grading.path("explanation").asText() : item.get("feedback_markdown"));
            } else append(body, "채점", "대기 중");
        }
        return body.toString();
    }

    private String weeklyDetails(Map<String, Object> row, long user, long curriculum, LocalDate from, LocalDate to) {
        LocalDate start = LocalDate.parse(row.get("occurred_at").toString());
        List<Map<String, Object>> days = jdbc.queryForList("""
                select day.local_date,day.day_type,day.rest_reason,item.activity_type,item.status,
                       coalesce(content.title,problem.title,test.title,unit.title,item.activity_type) item_title
                from daily_career.learning_day day
                left join daily_career.learning_day_item item on item.learning_day_id=day.id and item.user_id=day.user_id
                left join daily_career.curriculum_unit unit on unit.id=item.curriculum_unit_id
                left join daily_career.learning_content_version content_version on content_version.id=item.content_version_id
                left join daily_career.learning_content content on content.id=content_version.learning_content_id
                left join daily_career.problem_version problem_version on problem_version.id=item.problem_version_id
                left join daily_career.problem problem on problem.id=problem_version.problem_id
                left join daily_career.test_version test_version on test_version.id=item.test_version_id
                left join daily_career.test test on test.id=test_version.test_id
                where day.user_id=? and day.user_curriculum_id=? and day.local_date between ? and ?
                order by day.local_date,item.order_no
                """, user, curriculum, start.isBefore(from) ? from : start,
                start.plusDays(6).isAfter(to) ? to : start.plusDays(6));
        StringBuilder body = new StringBuilder("주간 학습 내역\n").append(row.get("summary"));
        Object previous = null;
        List<String> completed = new ArrayList<>();
        List<String> remaining = new ArrayList<>();
        for (Map<String, Object> day : days) {
            if (!Objects.equals(previous, day.get("local_date"))) {
                previous = day.get("local_date");
                body.append("\n\n").append(previous).append(" · ")
                        .append("REST".equals(day.get("day_type")) ? "휴식일" : "학습일");
                append(body, "휴식 사유", day.get("rest_reason"));
            }
            if (day.get("activity_type") != null) {
                String title = Objects.toString(day.get("item_title"), "학습 항목");
                body.append("\n• ").append(title).append(" · ").append(status(day.get("status")));
                if ("COMPLETED".equals(day.get("status"))) completed.add(title);
                else if (!"SKIPPED".equals(day.get("status"))) remaining.add(title);
            }
        }
        if (!completed.isEmpty()) append(body, "이번 주 완료한 학습", String.join(" · ", completed));
        if (!remaining.isEmpty()) append(body, "다음에 이어서 할 학습", String.join(" · ", remaining));
        return body.toString();
    }

    private String monthlyDetails(Map<String, Object> row) {
        JsonNode metrics = parse(row.get("metrics_snapshot"));
        JsonNode report = parse(row.get("report_payload"));
        StringBuilder body = new StringBuilder("월간 학습 결과");
        append(body, "기간", metrics.path("from").asText() + " ~ " + metrics.path("to").asText());
        append(body, "필수 학습", metrics.path("completedSessionCount").asText()
                + "/" + metrics.path("requiredSessionCount").asText());
        append(body, "학습 시간", metrics.path("studyMinutes").asText() + "분");
        append(body, "첫 풀이 정답", metrics.path("firstAttemptCorrectCount").asText()
                + "/" + metrics.path("firstAttemptProblemCount").asText());
        if (metrics.path("progressRate").isNumber()) append(body, "진도율", metrics.path("progressRate").asText() + "%");
        if (report.isObject() && !report.isEmpty()) append(body, "평가", report.toPrettyString());
        return body.toString();
    }

    private JsonNode parse(Object value) {
        try { return value == null ? json.createObjectNode() : json.readTree(value.toString()); }
        catch (Exception failure) { return json.createObjectNode(); }
    }

    private void appendChoices(StringBuilder body, Object choices) {
        JsonNode options = parse(choices);
        if (!options.isArray() || options.isEmpty()) return;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            lines.add(option(i, options.get(i)));
        }
        append(body, "보기", String.join("\n", lines));
    }

    private String answer(Object value, Object choices, boolean submitted) {
        if (value == null || value.toString().isBlank()) return "미응답";
        String selected = value.toString();
        if ("TRUE".equalsIgnoreCase(selected)) return "참";
        if ("FALSE".equalsIgnoreCase(selected)) return "거짓";
        JsonNode options = parse(choices);
        if (options.isArray()) {
            if (submitted && selected.matches("[1-9][0-9]{0,2}")) {
                int index = Integer.parseInt(selected) - 1;
                if (index >= 0 && index < options.size()) return option(index, options.get(index));
            }
            for (int i = 0; i < options.size(); i++) {
                JsonNode choice = options.get(i);
                if (selected.equals(choice.path("id").asText())
                        || selected.equals(choice.path("key").asText())
                        || selected.equals(choice.path("optionNo").asText())) {
                    return option(i, choice);
                }
            }
            if (selected.matches("[1-9][0-9]{0,2}")) {
                int index = Integer.parseInt(selected) - 1;
                if (index >= 0 && index < options.size()) return option(index, options.get(index));
            }
        }
        return selected;
    }

    private static String option(int index, JsonNode choice) {
        String text = choice.path("text").asText();
        boolean startsWithCode = text.stripLeading().startsWith("```") || text.stripLeading().startsWith("~~~");
        return (index + 1) + (startsWithCode ? "번\n\n" : "번 · ") + text;
    }

    private String summary(String entity, Map<String, Object> row) {
        return switch (entity) {
            case "PROBLEM" -> row.get("earned_score") == null ? "문제 풀이 · 채점 대기"
                    : "문제 풀이 · " + score(row.get("earned_score")) + "/" + score(row.get("max_score")) + "점";
            case "TEST_RESULT" -> row.get("test_type") == null || row.get("attempt_no") == null
                    ? row.get("summary").toString()
                    : testType(row.get("test_type")) + " 시험 " + row.get("attempt_no") + "회차 · 제출 완료";
            case "WRONG_ANSWER" -> "오답 복습 · " + wrongStatus(row.get("status"))
                    + (row.get("user_note") == null || row.get("user_note").toString().isBlank()
                    ? "" : " · " + row.get("user_note"));
            default -> row.get("summary").toString();
        };
    }

    private static String score(Object value) {
        if (value == null) return "-";
        String raw = value.toString();
        if (raw.contains("/")) {
            String[] parts = raw.split("/", 2);
            return score(parts[0]) + "/" + score(parts[1]);
        }
        try { return new BigDecimal(raw).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException ignored) { return raw; }
    }

    private static String testType(Object value) {
        return switch (Objects.toString(value, "")) {
            case "DAILY" -> "일간";
            case "WEEKLY" -> "주간";
            case "MONTHLY" -> "월간";
            case "FINAL" -> "최종";
            default -> Objects.toString(value, "확인");
        };
    }

    private static String submitReason(Object value) {
        return switch (Objects.toString(value, "")) {
            case "USER" -> "직접 제출";
            case "TIMEOUT" -> "시간 종료로 제출";
            default -> Objects.toString(value, "제출 완료");
        };
    }

    private static String wrongStatus(Object value) {
        return switch (Objects.toString(value, "")) {
            case "OPEN" -> "복습 필요";
            case "REVIEWING" -> "복습 중";
            case "MASTERED" -> "복습 완료";
            default -> Objects.toString(value, "상태 없음");
        };
    }

    private static void append(StringBuilder body, String label, Object value) {
        if (value != null && !value.toString().isBlank()) body.append("\n\n").append(label).append("\n").append(value);
    }

    private static String status(Object value) {
        return switch (Objects.toString(value, "")) {
            case "COMPLETED" -> "완료";
            case "IN_PROGRESS" -> "진행 중";
            case "NOT_STARTED" -> "미시작";
            case "SKIPPED" -> "건너뜀";
            default -> Objects.toString(value, "상태 없음");
        };
    }
}
