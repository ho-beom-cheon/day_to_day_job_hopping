import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { QuestionContent, QuestionSummary } from "./question-content";

describe("question content", () => {
  it("keeps list previews free of markdown fences and nested interactive content", () => {
    const { container } = render(<QuestionSummary body={'문제 **조건**입니다.\n\n```java\nreturn 1;\n```\n\n[참고](https://example.com)'} />);
    expect(container).toHaveTextContent("문제 조건입니다.");
    expect(container).toHaveTextContent("return 1;");
    expect(container.textContent).not.toContain("```");
    expect(container.querySelector("pre, a, p, h1")).toBeNull();
  });

  it("separates paragraphs, source code, literal examples and given statements without changing code", () => {
    const source = 'if (amount < 10) {\n    System.out.println("<값>");\n}\n';
    const { container } = render(<QuestionContent body={
      `다음 코드를 확인하세요.\n\n\`\`\`java\n${source}\`\`\`\n\n\`\`\`input\n9\n\`\`\`\n\n\`\`\`output\n<값>\n\`\`\`\n\n> ㄱ. 주어진 조건입니다.\n>\n> ㄴ. 다른 조건입니다.\n\n어떤 결과가 나오나요?`
    } />);
    expect(screen.getByText("다음 코드를 확인하세요.").tagName).toBe("P");
    expect(screen.getByLabelText("코드 · Java")).toHaveAttribute("tabindex", "0");
    expect(container.querySelector("pre code")?.textContent).toBe(source);
    expect(screen.getByLabelText("입력 예시")).toHaveTextContent("9");
    expect(screen.getByLabelText("출력 예시")).toHaveTextContent("<값>");
    expect(screen.getByLabelText("보기")).toHaveTextContent("ㄱ. 주어진 조건입니다.");
    expect(screen.getByText("어떤 결과가 나오나요?").tagName).toBe("P");
  });

  it("renders task material tables inside a horizontally scrollable region", () => {
    const { container } = render(<QuestionContent body={"| 항목 | 관찰 |\n|---|---|\n| 실패 이력 | 확인되지 않음 |"} />);
    const table = container.querySelector("table");
    expect(table).not.toBeNull();
    expect(table?.parentElement).toHaveAttribute("tabindex", "0");
    expect(screen.getByText("실패 이력").tagName).toBe("TD");
    expect(container.textContent).not.toContain("|---|");
  });

  it("colors Java tokens while preserving whitespace and escaping source text", () => {
    const source = 'import java.util.*;\n\npublic class Example {\n    // output <script>\n    String value = "<script>";\n}\n';
    const { container } = render(<QuestionContent body={`\`\`\`java\n${source}\`\`\``} />);
    const code = container.querySelector("pre code");

    expect(code?.textContent).toBe(source);
    expect(code?.querySelectorAll("span").length).toBeGreaterThan(0);
    expect(container.querySelector("script")).toBeNull();
  });

  it("keeps old plain text and inline identifiers readable", () => {
    const { container } = render(<QuestionContent compact body={'기존 문제입니다.\n줄바꿈을 유지합니다. `BigDecimal`을 사용합니다.'} />);
    expect(container.querySelector("p")?.textContent).toContain("기존 문제입니다.\n줄바꿈을 유지합니다.");
    expect(container.querySelector("code")).toHaveTextContent("BigDecimal");
    expect(container.querySelector("pre")).toBeNull();
  });

  it("does not render active HTML, remote images or clickable links inside choices", () => {
    const { container } = render(<QuestionContent body={'<script>alert(1)</script>\n\n<img src="https://example.com/track">\n\n![이미지](https://example.com/track)\n\n[참고](javascript:alert)\n\n# 제목'} />);
    expect(container.querySelector("script, img, a, h1")).toBeNull();
    expect(screen.getByText("참고")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "제목" })).toBeInTheDocument();
  });
});
