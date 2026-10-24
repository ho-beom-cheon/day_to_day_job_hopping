package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OpenRouterCompletionTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void preservesTrustedIdentityAndDropsUnrequestedModelFields() throws Exception {
        var answer = json.readTree("""
                {"summary":"오늘 기록이 없습니다.","strengths":[],"weaknesses":[],"nextStudyFocus":["학습 시작"],"curriculumId":"999","secret":"unwanted"}
                """);
        var input = json.readTree("{\"curriculumId\":\"9007199254740995\",\"date\":\"2026-09-24\"}");
        var result = OpenRouterCompletion.normalize(json, answer, input);
        assertThat(result.path("curriculumId").asText()).isEqualTo("9007199254740995");
        assertThat(result.has("secret")).isFalse();
        assertThat(result.path("type").asText()).isEqualTo("DAILY_FEEDBACK");
    }
    @Test void rejectsInvalidOrEnglishOnlyResults() throws Exception {
        var input = json.createObjectNode();
        for (String value : new String[]{"{}", "{\"summary\":\"English only\"}", "{\"summary\":\"요약\",\"strengths\":[4],\"weaknesses\":[],\"nextStudyFocus\":[]}"}) {
            var result = json.readTree(value);
            assertThatThrownBy(() -> OpenRouterCompletion.normalize(json, result, input)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void selectedTopicsCannotBecomeInventedLearnerStrengths() throws Exception {
        var input=json.readTree("{\"studyFocus\":[\"SQL 조인\"]}");
        var answer=json.readTree("{\"summary\":\"학습 안내입니다.\",\"strengths\":[\"조인을 잘합니다.\"],\"weaknesses\":[],\"nextStudyFocus\":[]}");
        assertThatThrownBy(()->OpenRouterCompletion.normalize(json,answer,input)).isInstanceOf(IllegalArgumentException.class);
    }
}
