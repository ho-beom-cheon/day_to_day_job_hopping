package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final List<String> CRITERIA = List.of("K1", "K2", "K3");

    private String task(String question, int points) {
        return """
                {"title":"이체 재시도","situation":"상황","material":"로그","question":"%s","estimatedMinutes":20,
                 "rubric":[{"criterionId":"R1","sheetCriteria":["K1"],"description":"원인","points":40},
                           {"criterionId":"R2","sheetCriteria":["K2"],"description":"수정","points":30},
                           {"criterionId":"R3","sheetCriteria":["K3"],"description":"검증","points":%d}],
                 "modelAnswerPoints":["a"],"reserveFollowups":[{"question":"왜요?","checkPoints":["근거"]}]}
                """.formatted(question, points);
    }

    @Test void acceptsAGroundedTaskAndDropsDuplicates() throws Exception {
        var result = json.readTree("{\"tasks\":[" + task("원인과 수정 방향을 설명하세요.", 30) + "," + task("원인과 수정 방향을 설명하세요.", 30) + "]}");
        assertThat(TaskValidation.tasks(json, result, "CASE", CRITERIA, Set.of())).hasSize(1);
    }

    @Test void rejectsCodeWritingRubricTotalsAndCriteriaOutsideTheSheet() throws Exception {
        assertThatThrownBy(() -> TaskValidation.task(json, json.readTree(task("재시도 로직 코드를 작성하세요.", 30)), "CASE", CRITERIA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskValidation.task(json, json.readTree(task("설명하세요.", 20)), "CASE", CRITERIA))
                .hasMessageContaining("100");
        assertThatThrownBy(() -> TaskValidation.task(json, json.readTree(task("설명하세요.", 30)), "CASE", List.of("K1", "K2")))
                .hasMessageContaining("outside the sheet");
        assertThatThrownBy(() -> TaskValidation.tasks(json, json.readTree("{\"tasks\":[]}"), "CASE", CRITERIA, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void gradingMustCoverEveryCriterionWithinItsPoints() throws Exception {
        var rubric = json.readTree(task("설명하세요.", 30)).path("rubric");
        var good = json.readTree("""
                {"status":"GRADED","criteria":[{"criterionId":"R1","earnedPoints":30,"reason":"a"},
                 {"criterionId":"R2","earnedPoints":10,"reason":"b"},{"criterionId":"R3","earnedPoints":0,"reason":"c"}],"holdReason":""}
                """);
        assertThat(TaskValidation.grading(json, good, rubric).score()).isEqualTo(40);
        var over = json.readTree("""
                {"status":"GRADED","criteria":[{"criterionId":"R1","earnedPoints":41,"reason":"a"},
                 {"criterionId":"R2","earnedPoints":10,"reason":"b"},{"criterionId":"R3","earnedPoints":0,"reason":"c"}]}
                """);
        assertThatThrownBy(() -> TaskValidation.grading(json, over, rubric)).isInstanceOf(IllegalArgumentException.class);
        var missing = json.readTree("{\"status\":\"GRADED\",\"criteria\":[{\"criterionId\":\"R1\",\"earnedPoints\":1,\"reason\":\"a\"}]}");
        assertThatThrownBy(() -> TaskValidation.grading(json, missing, rubric)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TaskValidation.grading(json, json.readTree("{\"status\":\"HOLD\",\"holdReason\":\"무관한 답\"}"), rubric).status()).isEqualTo("HOLD");
    }

    @Test void followUpCorrectionsStayWithinFifteenPointsInTotal() {
        assertThat(TaskValidation.delta(20, 0)).isEqualTo(15);
        assertThat(TaskValidation.delta(10, 10)).isEqualTo(5);
        assertThat(TaskValidation.delta(-15, -5)).isEqualTo(-10);
    }

    @Test void reviewIntervalsFollowTheScore() {
        assertThat(CompetencyProgress.interval(55, null)).isEqualTo(2);
        assertThat(CompetencyProgress.interval(70, null)).isEqualTo(5);
        assertThat(CompetencyProgress.interval(85, 60)).isEqualTo(12);
        assertThat(CompetencyProgress.interval(85, 90)).isEqualTo(24);
    }

    @Test void estimateMapsDifficultyAndScoreAndNeedsTwoResultsAboveLevelTwo() {
        var top = new CompetencyProgress.Evidence(100, 4, false);
        assertThat(CompetencyProgress.estimate(List.of(), 0)).isNull();
        assertThat(CompetencyProgress.estimate(List.of(top), 1)).isEqualByComparingTo("2.00");
        assertThat(CompetencyProgress.estimate(List.of(top, new CompetencyProgress.Evidence(100, 4, true)), 2)).isEqualByComparingTo("4.00");
        assertThat(CompetencyProgress.estimate(List.of(new CompetencyProgress.Evidence(70, 3, false), top), 2)).isEqualByComparingTo("2.80");
    }
}
