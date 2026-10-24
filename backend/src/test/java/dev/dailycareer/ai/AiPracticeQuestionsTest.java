package dev.dailycareer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPracticeQuestionsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ObjectNode input = json.createObjectNode()
            .put("contentId", "42").put("sessionId", "13").put("questionCount", 5)
            .put("estimatedMinutes",45).put("generationSequence",2)
            .put("body","금액은 부동소수점 대신 명시적인 소수 정밀도를 사용합니다. 결제 명령은 멱등키와 감사 이력으로 중복을 다룹니다.");

    AiPracticeQuestionsTest() {
        input.set("difficultyLevels",json.valueToTree(List.of(3,3,4,4,5)));
        input.set("priorPrompts",json.valueToTree(List.of()));
    }

    @Test
    void acceptsSessionSizedQuestionsWithFixedDifficultyAndGrounding() {
        var result = AiPracticeQuestions.normalize(json, input, response());

        assertThat(result.path("questions").size()).isEqualTo(5);
        assertThat(result.path("estimatedMinutes").asInt()).isEqualTo(45);
        assertThat(result.path("questions").get(4).path("difficulty").asInt()).isEqualTo(5);
        assertThat(result.path("questions").get(0).path("answer").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsMissingAndRepeatedQuestionsAcrossSavedSets() {
        ObjectNode missing = response();
        ((ArrayNode) missing.path("questions")).remove(4);
        assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, missing))
                .isInstanceOf(IllegalArgumentException.class);

        input.set("priorPrompts",json.valueToTree(List.of(prompts().getFirst().replace("온라인", "모바일"))));
        assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, response()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesStructuredMarkdownInsideExistingStringContract() {
        ObjectNode response = response();
        ObjectNode first = (ObjectNode) response.path("questions").get(0);
        String prompt = "정산 결과가 서로 다릅니다. 다음 코드를 확인하세요.\n\n"
                + "```java\nBigDecimal amount = new BigDecimal(0.1);\n```\n\n"
                + "```output\n0.10000000000000000555\n```\n\n가장 적절한 개선은 무엇인가요?";
        String choice = "```java\nnew BigDecimal(\"0.1\");\n```";
        first.put("prompt", prompt);
        ((ArrayNode) first.path("choices")).set(0, json.getNodeFactory().textNode(choice));

        var result = AiPracticeQuestions.normalize(json, input, response).path("questions").get(0);
        assertThat(result.path("prompt").asText()).isEqualTo(prompt);
        assertThat(result.path("choices").get(0).asText()).isEqualTo(choice);
    }

    @Test
    void rejectsUnclosedBlocksInPromptsChoicesAndExplanations() {
        for (String field : List.of("prompt", "explanation", "choices")) {
            ObjectNode response = response();
            ObjectNode first = (ObjectNode) response.path("questions").get(0);
            if (field.equals("choices")) {
                ((ArrayNode) first.path(field)).set(0, json.getNodeFactory().textNode("```java\nreturn 1;"));
            } else {
                first.put(field, first.path(field).asText() + "\n\n```java\nreturn 1;");
            }
            assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, response))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unclosed code block");
        }
    }

    @Test
    void acceptsLongerClosingFencesAndBackticksInsideCode() {
        ObjectNode response = response();
        ObjectNode first = (ObjectNode) response.path("questions").get(0);
        first.put("prompt", first.path("prompt").asText()
                + "\n\n~~~~text\n```java\n내용\n```\n~~~~~\n\n위 예시를 확인하세요.");
        assertThat(AiPracticeQuestions.normalize(json, input, response).path("questions").size()).isEqualTo(5);
    }

    @Test
    void rejectsLowerDifficultyAndUnsupportedEvidence() {
        ObjectNode easy = response();
        ((ObjectNode)easy.path("questions").get(4)).put("difficulty",3);
        assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, easy))
                .isInstanceOf(IllegalArgumentException.class);

        ObjectNode invented = response();
        ((ObjectNode)invented.path("questions").get(0)).put("sourceExcerpt","학습 본문에 없는 내용입니다.");
        assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, invented))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAmbiguousChoicesAndConcentratedAnswers() {
        ObjectNode duplicateChoice = response();
        ((ArrayNode) duplicateChoice.path("questions").get(0).path("choices")).set(1,
                duplicateChoice.path("questions").get(0).path("choices").get(0));
        assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, duplicateChoice))
                .isInstanceOf(IllegalArgumentException.class);

        ObjectNode oneAnswerPosition = response();
        for (var question : oneAnswerPosition.path("questions")) ((ObjectNode)question).put("answer",1);
        assertThatThrownBy(() -> AiPracticeQuestions.normalize(json, input, oneAnswerPosition))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savesUsableQuestionsWhenOnePaidQuestionFailsValidation() {
        ObjectNode generated = response();
        ((ObjectNode)generated.path("questions").get(1)).put("sourceExcerpt", "학습에 없는 주장입니다.");

        var result = AiPracticeQuestions.normalizeUsable(json, input, generated);

        assertThat(result.path("questions").size()).isEqualTo(4);
        assertThat(result.path("requestedQuestionCount").asInt()).isEqualTo(5);
        assertThat(result.path("rejectedQuestionCount").asInt()).isEqualTo(1);
        assertThat(result.path("questions").get(1).path("prompt").asText()).isEqualTo(prompts().get(2));
    }

    @Test
    void keepsOneBadQuestionFromBlockingLaterValidQuestions() {
        ObjectNode generated = response();
        ((ArrayNode)generated.path("questions")).remove(4);
        ((ArrayNode)generated.path("questions").get(0).path("choices")).set(1,
                generated.path("questions").get(0).path("choices").get(0));

        var result = AiPracticeQuestions.normalizeUsable(json, input, generated);

        assertThat(result.path("questions").size()).isEqualTo(3);
        assertThat(result.path("rejectedQuestionCount").asInt()).isEqualTo(2);
    }

    @Test
    void refusesToExposeASetWithOnlyOneValidQuestion() {
        ObjectNode generated = response();
        while (generated.path("questions").size() > 1) ((ArrayNode)generated.path("questions")).remove(1);

        assertThatThrownBy(() -> AiPracticeQuestions.normalizeUsable(json, input, generated))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Fewer than two");
    }

    @Test
    void policyGrowsCountAndDifficultyWithSessionTime() {
        assertThat(AiPracticePlan.forMinutes(15).questionCount()).isEqualTo(4);
        assertThat(AiPracticePlan.forMinutes(45).difficulties()).containsExactly(3,3,3,3,4,4,4,4,5,5);
        assertThat(AiPracticePlan.forMinutes(90).questionCount()).isEqualTo(18);
        assertThat(AiPracticePlan.forMinutes(90).difficulties()).startsWith(3).endsWith(5);
    }

    @Test
    void harderQuestionsReduceCountAtTheSameSessionDuration() {
        assertThat(AiPracticePlan.forMinutes(45, 3).questionCount()).isEqualTo(12);
        assertThat(AiPracticePlan.forMinutes(45, 4).questionCount()).isEqualTo(10);
        assertThat(AiPracticePlan.forMinutes(45, 5).questionCount()).isEqualTo(8);
        assertThat(AiPracticePlan.forMinutes(90, 3).questionCount()).isEqualTo(20);
        assertThat(AiPracticePlan.forMinutes(90, 4).questionCount()).isEqualTo(18);
        assertThat(AiPracticePlan.forMinutes(90, 5).questionCount()).isEqualTo(14);
        assertThat(AiPracticePlan.forMinutes(45, 3).difficulties()).endsWith(4);
        assertThat(AiPracticePlan.forMinutes(45, 5).difficulties()).startsWith(4).endsWith(5);
        assertThatThrownBy(() -> AiPracticePlan.forMinutes(45, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(AiPracticePlan.forMinutes(45, 3).hasEnoughSource("값 비교와 불변 객체의 기본 원칙을 설명한다.")).isFalse();
        assertThat(AiPracticePlan.forMinutes(45, 3).hasEnoughSource("학습 본문 ".repeat(100))).isTrue();
        assertThat(AiPracticePlan.availableDifficulties(45, "짧은 본문")).isEmpty();
        assertThat(AiPracticePlan.availableDifficulties(45, "가".repeat(400))).containsExactly(5);
        assertThat(AiPracticePlan.availableDifficulties(45, "가".repeat(450))).containsExactly(4, 5);
    }

    private ObjectNode response() {
        ObjectNode root = json.createObjectNode();
        ArrayNode questions = root.putArray("questions");
        for (int i = 0; i < 5; i++) {
            ObjectNode question = questions.addObject()
                    .put("prompt", prompts().get(i))
                    .put("answer", i % 4 + 1)
                    .put("difficulty", List.of(3,3,4,4,5).get(i))
                    .put("sourceExcerpt", "명시적인 소수 정밀도")
                    .put("explanation", List.of(
                            "문자열에서 소수형으로 변환하면 금액의 십진수 정밀도를 그대로 유지할 수 있습니다.",
                            "각 계산 단계의 반올림 규칙을 분리해 적용해야 누적 오차가 생기지 않습니다.",
                            "멱등키와 금액 저장을 함께 묶어 중복 결제 요청의 결과를 안정적으로 재사용합니다.",
                            "정산 값과 감사 이력의 저장 경로를 비교해 차이가 시작된 지점을 찾습니다.",
                            "재시도와 환불 상태를 원자적으로 기록하고 금액 불변식을 검증해야 합니다.").get(i));
            question.putArray("choices").add("첫 번째 방법").add("두 번째 방법")
                    .add("세 번째 방법").add("네 번째 방법");
        }
        return root;
    }

    private List<String> prompts() {
        return List.of(
                "온라인 결제에서 금액 문자열을 계산에 사용할 때 소수 정밀도를 안전하게 유지하려면 어떤 방식이 적절한가요?",
                "할인된 금액을 여러 차례 합산해야 하는 서비스에서 반올림 시점은 어떻게 정해야 오류를 줄일 수 있나요?",
                "결제 요청이 네트워크 장애로 중복 전송될 때 멱등키와 금액 저장 방식을 함께 설계하려면 무엇을 선택해야 하나요?",
                "감사 이력에는 결제 금액이 정상으로 보이지만 정산 결과가 달라지는 상황을 어떻게 진단하고 수정해야 하나요?",
                "부분 실패 후 재시도와 환불이 동시에 일어나는 시스템에서 금액 일관성을 유지할 설계는 무엇인가요?");
    }
}
