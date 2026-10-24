package dev.dailycareer.integration.notion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Converts source Markdown to native Notion blocks; never executes or rewrites code. */
final class NotionMarkdown {
    private static final int MAX_BLOCKS = 80;
    private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private static final Set<String> LANGUAGES = Set.of(
            "abap", "arduino", "bash", "basic", "c", "clojure", "coffeescript", "c++", "c#", "css",
            "dart", "diff", "docker", "elixir", "elm", "erlang", "flow", "fortran", "f#", "gherkin",
            "glsl", "go", "graphql", "groovy", "haskell", "html", "java", "javascript", "json", "julia",
            "kotlin", "latex", "less", "lisp", "livescript", "lua", "makefile", "markdown", "markup",
            "matlab", "mermaid", "nix", "objective-c", "ocaml", "pascal", "perl", "php", "plain text",
            "powershell", "prolog", "protobuf", "python", "r", "reason", "ruby", "rust", "sass", "scala",
            "scheme", "scss", "shell", "sql", "swift", "typescript", "vb.net", "verilog", "vhdl",
            "visual basic", "webassembly", "xml", "yaml", "java/c/c++/c#");
    private final ObjectMapper json;

    NotionMarkdown(ObjectMapper json) { this.json = json; }

    List<String> sections(String source, Predicate<String> isLabel) {
        Set<Integer> codeLines = new HashSet<>();
        PARSER.parse(source).accept(new AbstractVisitor() {
            private void protect(Node node) {
                node.getSourceSpans().forEach(span -> codeLines.add(span.getLineIndex()));
            }
            @Override public void visit(FencedCodeBlock node) { protect(node); }
            @Override public void visit(IndentedCodeBlock node) { protect(node); }
        });
        String[] lines = source.split("\n", -1);
        List<String> sections = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i - 1].isBlank() && !codeLines.contains(i) && isLabel.test(lines[i])) {
                sections.add(String.join("\n", java.util.Arrays.copyOfRange(lines, start, i)));
                start = i;
            }
        }
        sections.add(String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length)));
        return sections;
    }

    ArrayNode render(String source) {
        ArrayNode blocks = json.createArrayNode();
        append(blocks, PARSER.parse(source).getFirstChild(), "paragraph");
        return blocks;
    }

    private void append(ArrayNode blocks, Node first, String paragraphType) {
        for (Node node = first; node != null; node = node.getNext()) {
            if (blocks.size() >= MAX_BLOCKS) {
                if (blocks.size() == MAX_BLOCKS) blocks.add(block("paragraph", text("내용이 길어 나머지는 Daily Leap 앱에서 확인하세요.")));
                return;
            }
            if (node instanceof FencedCodeBlock code) blocks.add(code(code.getLiteral(), language(code.getInfo())));
            else if (node instanceof IndentedCodeBlock code) blocks.add(code(code.getLiteral(), "plain text"));
            else if (node instanceof Heading) blocks.add(block("heading_3", inlineNodes(node.getFirstChild())));
            else if (node instanceof Paragraph) blocks.add(block(paragraphType, inlineNodes(node.getFirstChild())));
            else if (node instanceof ThematicBreak) {
                ObjectNode divider = json.createObjectNode().put("object", "block").put("type", "divider");
                divider.putObject("divider");
                blocks.add(divider);
            } else if (node instanceof BulletList || node instanceof OrderedList) {
                String type = node instanceof OrderedList ? "numbered_list_item" : "bulleted_list_item";
                for (Node item = node.getFirstChild(); item != null; item = item.getNext()) append(blocks, item.getFirstChild(), type);
            } else if (node instanceof BlockQuote) append(blocks, node.getFirstChild(), "quote");
            else if (node instanceof HtmlBlock html) blocks.add(block("paragraph", text(html.getLiteral())));
        }
    }

    boolean hasCodeBlock(String source) {
        boolean[] found = {false};
        PARSER.parse(source).accept(new AbstractVisitor() {
            @Override public void visit(FencedCodeBlock node) { found[0] = true; }
            @Override public void visit(IndentedCodeBlock node) { found[0] = true; }
        });
        return found[0];
    }

    ArrayNode inline(String source) {
        ArrayNode result = json.createArrayNode();
        Node document = PARSER.parse(source);
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (!result.isEmpty()) result.addAll(text("\n"));
            result.addAll(inlineNodes(node.getFirstChild()));
        }
        return result.isEmpty() ? text(source) : result;
    }

    private ArrayNode inlineNodes(Node first) {
        ArrayNode parts = json.createArrayNode();
        for (Node node = first; node != null; node = node.getNext()) {
            if (node instanceof Text value) parts.addAll(text(value.getLiteral()));
            else if (node instanceof Code value) parts.addAll(annotate(text(value.getLiteral()), "code"));
            else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) parts.addAll(text("\n"));
            else if (node instanceof HtmlInline html) parts.addAll(text(html.getLiteral()));
            else {
                ArrayNode nested = inlineNodes(node.getFirstChild());
                if (node instanceof StrongEmphasis) annotate(nested, "bold");
                if (node instanceof Emphasis) annotate(nested, "italic");
                if (node instanceof Link link && link.getDestination().matches("(?i)https?://.*")) {
                    for (JsonNode part : nested) ((ObjectNode) part.path("text")).putObject("link").put("url", link.getDestination());
                }
                parts.addAll(nested);
            }
        }
        return parts;
    }

    private ArrayNode annotate(ArrayNode parts, String annotation) {
        for (JsonNode part : parts) ((ObjectNode) part).withObject("annotations").put(annotation, true);
        return parts;
    }

    ArrayNode text(String value) {
        ArrayNode parts = json.createArrayNode();
        for (int start = 0; start < value.length();) {
            int end = Math.min(start + 1800, value.length());
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            parts.addObject().put("type", "text").putObject("text").put("content", value.substring(start, end));
            start = end;
        }
        return parts;
    }

    private ObjectNode code(String source, String language) {
        ObjectNode block = block("code", text(source));
        ((ObjectNode) block.get("code")).put("language", language).putArray("caption");
        return block;
    }

    private ObjectNode block(String type, ArrayNode parts) {
        ObjectNode block = json.createObjectNode().put("object", "block").put("type", type);
        block.putObject(type).set("rich_text", parts);
        return block;
    }

    private String language(String info) {
        String value = info == null ? "" : info.strip().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        value = switch (value) {
            case "js", "jsx", "node" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "py" -> "python";
            case "sh", "zsh", "shellscript" -> "shell";
            case "yml" -> "yaml";
            case "cs", "csharp" -> "c#";
            case "cpp" -> "c++";
            case "kt" -> "kotlin";
            case "ps1", "pwsh" -> "powershell";
            case "dockerfile" -> "docker";
            default -> value;
        };
        return LANGUAGES.contains(value) ? value : "plain text";
    }
}
