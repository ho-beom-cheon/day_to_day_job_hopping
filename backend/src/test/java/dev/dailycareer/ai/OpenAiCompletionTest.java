package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompletionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void readsTextAfterReasoningItems() throws Exception {
        var response = json.readTree("""
                {"output":[{"type":"reasoning","summary":[]},{"type":"message","content":[{"type":"output_text","text":"{\\"summary\\":\\"오늘 기록 없음\\",\\"strengths\\":[],\\"weaknesses\\":[],\\"nextStudyFocus\\":[]}"}]}]}
                """);
        assertThat(OpenAiCompletion.outputText(response)).contains("오늘 기록 없음");
    }

    @Test void rejectsMissingOutputText() throws Exception {
        var response = json.readTree("""
                {"output":[{"type":"reasoning"},{"type":"message","content":[{"type":"refusal","refusal":"No"}]}]}
                """);
        assertThatThrownBy(() -> OpenAiCompletion.outputText(response)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void distinguishesExhaustedCreditsFromOtherProviderFailures() throws Exception {
        var exhausted = json.readTree("{\"error\":{\"type\":\"insufficient_quota\",\"code\":\"credit_balance_exhausted\"}}");
        assertThat(OpenAiCompletion.providerError(429, exhausted))
                .isInstanceOf(OpenAiCompletion.QuotaExceededException.class);
        assertThat(OpenAiCompletion.providerError(429, json.readTree("{\"error\":{\"type\":\"rate_limit_exceeded\"}}")))
                .isNotInstanceOf(OpenAiCompletion.QuotaExceededException.class);
    }
}
