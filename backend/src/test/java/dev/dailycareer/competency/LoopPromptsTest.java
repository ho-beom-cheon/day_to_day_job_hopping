package dev.dailycareer.competency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoopPromptsTest {
    private final LoopPrompts prompts = new LoopPrompts(new ObjectMapper());
    private final ObjectMapper json = new ObjectMapper();

    @Test void eachRoleCarriesItsReasoningEffortAndRoomForReasoning() {
        var input = json.createObjectNode();
        assertThat(prompts.request(LoopPrompts.Role.TASK_GENERATE, input).reasoningEffort()).isEqualTo("xhigh");
        assertThat(prompts.request(LoopPrompts.Role.CLAIM_REVIEW, input).reasoningEffort()).isEqualTo("xhigh");
        assertThat(prompts.request(LoopPrompts.Role.GRADING, input).reasoningEffort()).isEqualTo("high");
        assertThat(prompts.request(LoopPrompts.Role.FOLLOWUP_TURN, input).reasoningEffort()).isEqualTo("medium");
        assertThat(prompts.request(LoopPrompts.Role.TASK_GENERATE, input).maxOutputTokens()).isGreaterThanOrEqualTo(20_000);
        assertThat(prompts.request(LoopPrompts.Role.GRADING, input).schema().path("required").toString()).contains("criteria");
    }
}
