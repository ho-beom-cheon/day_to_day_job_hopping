package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** Renders one replaceable, app-owned Notion block without touching a learner's other blocks. */
final class NotionPageTemplate {
    static final String MARKER = "Daily Leap 학습 기록";
    private static final int MAX_CHILDREN = 90;
    private static final Set<String> LABELS = Set.of(
            "문제", "보기", "내 답", "정답", "채점", "점수", "피드백", "해설", "오답 상태",
            "내 메모", "오답 분석", "학습 자료", "완료 시각", "제출 사유", "제출 방식", "휴식 사유",
            "기간", "필수 학습", "학습 시간", "첫 풀이 정답", "진도율", "평가",
            "이번 주 완료한 학습", "다음에 이어서 할 학습", "주간 학습 내역", "월간 학습 결과");

    private final ObjectMapper json;
    private final NotionMarkdown markdown;

    NotionPageTemplate(ObjectMapper json) { this.json = json; this.markdown = new NotionMarkdown(json); }

    ObjectNode render(String entityType, JsonNode payload) {
        ObjectNode root = paragraph(MARKER + " · " + title(entityType));
        ObjectNode content = (ObjectNode) root.get("paragraph");
        content.set("rich_text", styledText(MARKER + " · " + title(entityType), false, "gray"));
        ArrayNode children = content.putArray("children");
        String summary = payload.path("summary").asText();
        String date = payload.path("occurredAt").asText();
        if (("완료 · " + date).equals(summary)) summary = "학습 완료";
        children.add(mutedParagraph(date + (summary.isBlank() ? "" : "  ·  " + summary)));
        children.add(divider());
        String details = payload.path("details").asText("").replace("\r\n", "\n");
        for (String section : markdown.sections(details, this::isSectionLabel)) {
            if (children.size() >= MAX_CHILDREN - 2) break;
            addSection(children, section);
        }
        if (children.size() >= MAX_CHILDREN - 2) {
            children.add(paragraph("내용이 길어 나머지는 Daily Leap 앱에서 확인하세요."));
        }
        return root;
    }

    private boolean isSectionLabel(String line) {
        return LABELS.contains(line) || line.matches("문항 [0-9]+")
                || line.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} · .*");
    }

    private void addSection(ArrayNode children, String section) {
        if (section.isBlank()) return;
        if (section.strip().matches("문항 [0-9]+")) {
            children.add(block("heading_2", section.strip()));
            return;
        }
        String[] pair = section.split("\n", 2);
        String label = pair[0].strip();
        String value = pair.length == 2 ? pair[1] : "";
        if (label.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} · .*")) {
            children.add(block("heading_3", label));
            if (markdown.hasCodeBlock(value)) addMarkdown(children, value);
            else addLines(children, value);
            return;
        }
        if (!LABELS.contains(label)) {
            if (section.startsWith("학습 주제:")) return;
            if (section.strip().equals("문제 풀이") || section.strip().equals("오답 복습")
                    || section.startsWith("시험 결과:")) return;
            addMarkdown(children, section);
            return;
        }
        switch (label) {
            case "문제" -> {
                children.add(block("heading_2", "🧩 문제"));
                addMarkdown(children, value);
            }
            case "보기" -> {
                children.add(labeledParagraph("보기", "", "default"));
                addMarkdown(children, value);
            }
            case "내 답" -> {
                children.add(divider());
                children.add(block("heading_3", "✍️ 답안 확인"));
                addLabeledContent(children, "내 답", value);
            }
            case "정답" -> addLabeledContent(children, "정답", value);
            case "채점" -> children.add(labeledParagraph("채점 결과", value.strip(),
                    value.contains("오답") ? "red" : value.contains("대기") ? "gray"
                            : "정답".equals(value.strip()) ? "green" : "default"));
            case "점수" -> children.add(labeledParagraph("점수", value.strip(), "default"));
            case "오답 상태" -> children.add(labeledParagraph("복습 상태", wrongStatus(value.strip()), "default"));
            case "피드백", "해설" -> {
                children.add(block("heading_3", "💡 " + label));
                addMarkdown(children, value);
            }
            case "학습 자료" -> {
                // Older single-line seed headings encoded paragraph breaks literally.
                // Do not unescape code/string literals in ordinary multi-line Markdown.
                if (value.startsWith("#") && !value.stripTrailing().contains("\n")) {
                    value = value.replace("\\n\\n", "\n\n");
                }
                children.add(block("heading_2", "📖 학습 자료"));
                addMarkdown(children, value);
            }
            case "내 메모", "오답 분석" -> {
                children.add(block("heading_3", ("내 메모".equals(label) ? "📝 " : "🔎 ") + label));
                if (markdown.hasCodeBlock(value)) addMarkdown(children, value);
                else children.add(block("quote", value.strip()));
            }
            case "주간 학습 내역", "월간 학습 결과" -> {
                children.add(block("heading_2", ("주간 학습 내역".equals(label) ? "📅 " : "📊 ") + label));
                if (!value.isBlank()) addMarkdown(children, value);
            }
            case "이번 주 완료한 학습", "다음에 이어서 할 학습" -> {
                children.add(block("heading_3", ("이번 주 완료한 학습".equals(label) ? "✅ " : "➡️ ") + label));
                addMarkdown(children, value);
            }
            default -> addLabeledContent(children, label, value);
        }
    }

    private void addLines(ArrayNode children, String value) {
        for (String line : value.split("\n")) {
            if (children.size() >= MAX_CHILDREN - 1) return;
            String text = line.strip();
            if (text.isBlank()) continue;
            children.add(block("bulleted_list_item", text.startsWith("• ") ? text.substring(2) : text));
        }
    }

    private void addMarkdown(ArrayNode children, String source) {
        addBlocks(children, markdown.render(source));
    }

    private void addBlocks(ArrayNode children, ArrayNode blocks) {
        for (JsonNode block : blocks) {
            if (children.size() >= MAX_CHILDREN - 2) return;
            children.add(block);
        }
    }

    private void addLabeledContent(ArrayNode children, String label, String value) {
        if (markdown.hasCodeBlock(value)) {
            children.add(labeledParagraph(label, "", "default"));
            addMarkdown(children, value);
        } else children.add(labeledParagraph(label, value.strip(), "default"));
    }

    private ObjectNode paragraph(String value) { return block("paragraph", value); }

    private ObjectNode block(String type, String value) {
        ObjectNode block = json.createObjectNode().put("object", "block").put("type", type);
        block.putObject(type).set("rich_text", richText(value));
        return block;
    }

    private ObjectNode labeledParagraph(String label, String value, String color) {
        ObjectNode block = paragraph(label);
        ArrayNode parts = styledText(label, true, "default");
        if (!value.isBlank()) {
            parts.addAll(richText("   "));
            parts.addAll(styledText(value, false, color));
        }
        ((ObjectNode) block.get("paragraph")).set("rich_text", parts);
        return block;
    }

    private ObjectNode mutedParagraph(String value) {
        ObjectNode block = paragraph(value);
        ((ObjectNode) block.get("paragraph")).set("rich_text", styledText(value, false, "gray"));
        return block;
    }

    private ArrayNode styledText(String value, boolean bold, String color) {
        ArrayNode parts = richText(value);
        for (JsonNode part : parts) ((ObjectNode) part).withObject("annotations")
                .put("bold", bold).put("color", color);
        return parts;
    }

    private ObjectNode divider() {
        ObjectNode block = json.createObjectNode().put("object", "block").put("type", "divider");
        block.putObject("divider");
        return block;
    }

    private ArrayNode richText(String value) {
        return markdown.inline(value);
    }

    private static String title(String entityType) {
        return switch (entityType) {
            case "LEARNING_RESULT" -> "학습 완료";
            case "PROBLEM" -> "문제 풀이";
            case "TEST_RESULT" -> "시험 결과";
            case "WRONG_ANSWER" -> "오답 복습";
            case "WEEKLY_REPORT" -> "주간 리포트";
            case "MONTHLY_REPORT" -> "월간 리포트";
            default -> "학습 기록";
        };
    }

    private static String wrongStatus(String value) {
        return switch (value) {
            case "OPEN" -> "복습 필요";
            case "MASTERED" -> "복습 완료";
            case "REVIEWING" -> "복습 중";
            default -> value;
        };
    }
}
