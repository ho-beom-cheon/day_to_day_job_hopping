import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LearningMarkdown } from "./learning-markdown";

describe("LearningMarkdown", () => {
  it("renders Java generics as code and official sources as safe links", () => {
    render(<LearningMarkdown body={'## 실행 예제\n\n```java\nList<String> names = List.of("A");\n```\n\n- [공식 문서](https://docs.oracle.com/en/java/)'} />);
    expect(screen.getByRole("heading", { name: "실행 예제" })).toBeInTheDocument();
    expect(screen.getByLabelText("코드 예제")).toHaveTextContent('List<String> names = List.of("A");');
    expect(screen.getByRole("link", { name: "공식 문서" })).toHaveAttribute("rel", "noreferrer noopener");
  });
  it("does not activate raw HTML, remote images, or dangerous links", () => {
    const { container } = render(<LearningMarkdown body={'<script>alert(1)</script>\n\n<img src="https://example.test/tracker">\n\n![tracking](https://example.test/image)\n\n[bad](javascript:alert%281%29)'} />);
    expect(container.querySelector("script, img")).toBeNull();
    expect(screen.queryByRole("link")).toBeNull();
  });
});
