package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotionPageTemplateTest {
    private final ObjectMapper json = new ObjectMapper();
    private final NotionPageTemplate template = new NotionPageTemplate(json);

    @Test void problemUsesScannableQuestionChoicesAnswersAndFeedbackBlocks() {
        JsonNode payload = json.valueToTree(Map.of(
                "summary", "문제 풀이 제출 · 1/1", "occurredAt", "2026-09-26",
                "details", "문제 풀이\n\n문제\n같은 요청을 한 번만 처리하는 성질은?"
                        + "\n\n보기\n1번 · 멱등성\n2번 · 캐시"
                        + "\n\n내 답\n1번 · 멱등성\n\n정답\n1번 · 멱등성"
                        + "\n\n채점\n정답\n\n피드백\n중복 처리를 막습니다."));

        JsonNode root = template.render("PROBLEM", payload);
        JsonNode children = root.path("paragraph").path("children");

        assertThat(root.path("type").asText()).isEqualTo("paragraph");
        assertThat(children.toString()).contains("heading_2", "답안 확인", "1번 · 멱등성", "2번 · 캐시",
                "내 답", "정답", "채점 결과", "중복 처리를 막습니다.");
        assertThat(root.toString()).contains("🧩 문제", "✍️ 답안 확인", "💡 피드백")
                .doesNotContain("callout", "_background", "🎯");
        assertThat(children.toString()).doesNotContain("\"content\":\"A\"");
    }

    @Test void learningMarkdownHeadingAndEscapedSeedNewlinesBecomeReadableBlocks() {
        JsonNode payload = json.valueToTree(Map.of(
                "summary", "완료 · 2026-09-26", "occurredAt", "2026-09-26",
                "details", "학습 주제: Java\n\n학습 자료\n# 값 비교\\n\\n문자열은 equals로 비교한다."));

        JsonNode children = template.render("LEARNING_RESULT", payload).path("paragraph").path("children");

        assertThat(children.toString()).contains("학습 자료", "문자열은 equals로 비교한다.");
        assertThat(children.get(2).path("heading_2").path("rich_text").get(0)
                .path("text").path("content").asText()).isEqualTo("📖 학습 자료");
        assertThat(children.get(2).path("heading_2").has("color")).isFalse();
        assertThat(children.get(3).path("heading_3").path("rich_text").get(0)
                .path("text").path("content").asText()).isEqualTo("값 비교");
        assertThat(children.get(4).path("paragraph").toString()).contains("문자열은 equals로 비교한다.");
        assertThat(children.toString()).doesNotContain("callout", "_background");
    }

    @Test void learningUsesSimpleBlocksAndPreservesCode() {
        String code = "public class Example {\n\n    String value = \"`equals`\\n\\n\";\n}\n";
        JsonNode root = render("LEARNING_RESULT", "학습 자료\n# 값 비교\n\n첫 문단의 `equals` 설명.\n\n둘째 문단.\n\n"
                + "```java\n" + code + "```\n\n## 다음 주제\n\n마지막 문단.");

        assertThat(blocks(root, "callout")).isEmpty();
        assertThat(blocks(root, "heading_3")).hasSize(2);
        assertThat(blocks(root, "paragraph").toString()).contains("둘째 문단.", "마지막 문단.");
        JsonNode codeBlock = blocks(root, "code").getFirst().path("code");
        assertThat(codeBlock.path("language").asText()).isEqualTo("java");
        assertThat(text(codeBlock.path("rich_text"))).isEqualTo(code);
        assertThat(root.toString()).contains("\"code\":true", "다음 주제");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNING_RESULT", "PROBLEM", "TEST_RESULT", "WRONG_ANSWER", "WEEKLY_REPORT", "MONTHLY_REPORT"})
    void everyRecordTypePreservesFencesBlankLinesAndLabelsInsideCode(String entityType) {
        String source = "SELECT id\n\n  FROM users;\n\n정답\n# code comment\n";
        JsonNode root = render(entityType, "해설\n~~~SQL\n" + source + "~~~\n\n피드백\n코드 다음 설명.");
        JsonNode code = blocks(root, "code").getFirst().path("code");

        assertThat(code.path("language").asText()).isEqualTo("sql");
        assertThat(text(code.path("rich_text"))).isEqualTo(source);
        assertThat(root.toString()).contains("코드 다음 설명.").doesNotContain("~~~");
    }

    @ParameterizedTest
    @ValueSource(strings = {"문제", "보기", "내 답", "정답", "내 메모", "오답 분석", "평가"})
    void codeBlocksWorkInEveryContentField(String label) {
        JsonNode root = render("PROBLEM", label + "\n```js\nconst value = 1;\n```\n");
        JsonNode code = blocks(root, "code").getFirst().path("code");
        assertThat(code.path("language").asText()).isEqualTo("javascript");
        assertThat(text(code.path("rich_text"))).isEqualTo("const value = 1;\n");
    }

    @Test void indentedUnknownLanguageAndUnclosedCodeRemainReadable() {
        JsonNode root = render("WRONG_ANSWER", "내 답\n    first();\n        second();\n\n정답\n"
                + "```unsupported-language\nanswer();\n```\n\n해설\n```py\nprint('끝')");
        List<JsonNode> codes = blocks(root, "code");
        assertThat(codes).hasSize(3);
        assertThat(text(codes.get(0).path("code").path("rich_text"))).isEqualTo("first();\n    second();\n");
        assertThat(codes.get(1).path("code").path("language").asText()).isEqualTo("plain text");
        assertThat(codes.get(2).path("code").path("language").asText()).isEqualTo("python");
        assertThat(text(codes.get(2).path("code").path("rich_text"))).isEqualTo("print('끝')\n");
    }

    @Test void longCodeIsChunkedWithoutLosingUnicodeOrApplyingInlineFormatting() {
        String source = "x".repeat(1799) + "😀\n" + "    `literal`;\n".repeat(200);
        JsonNode code = blocks(render("TEST_RESULT", "문제\n```java\n" + source + "```"), "code").getFirst().path("code");
        assertThat(text(code.path("rich_text"))).isEqualTo(source);
        for (JsonNode part : code.path("rich_text")) {
            String value = part.path("text").path("content").asText();
            assertThat(value.length()).isLessThanOrEqualTo(1800);
            assertThat(Character.isHighSurrogate(value.charAt(value.length() - 1))).isFalse();
            assertThat(part.has("annotations")).isFalse();
        }
    }

    private JsonNode render(String type, String details) {
        return template.render(type, json.valueToTree(Map.of("occurredAt", "2026-09-27", "details", details)));
    }

    private List<JsonNode> blocks(JsonNode node, String type) {
        List<JsonNode> result = new ArrayList<>();
        if (node.isObject() && type.equals(node.path("type").asText())) result.add(node);
        if (node.isContainerNode()) for (JsonNode child : node) result.addAll(blocks(child, type));
        return result;
    }

    private String text(JsonNode richText) {
        StringBuilder value = new StringBuilder();
        richText.forEach(part -> value.append(part.path("text").path("content").asText()));
        return value.toString();
    }
}
