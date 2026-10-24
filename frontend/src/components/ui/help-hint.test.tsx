import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HelpHint } from "./help-hint";

describe("HelpHint", () => {
  it("opens the explanation by click and closes it with Escape or an outside click", () => {
    render(<div><HelpHint label="출제 난이도" summary="생각할 문제의 방식을 선택해요." items={[
      { term: "적용 · 3/5", description: "배운 개념을 상황에 적용해요." },
      { term: "설계 · 5/5", description: "조건을 비교해 설계해요." },
    ]} note="내 실력을 측정한 결과가 아니에요." /><button type="button">바깥</button></div>);
    const trigger = screen.getByRole("button", { name: "출제 난이도 도움말" });

    expect(trigger).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(trigger);
    expect(screen.getByRole("note")).toHaveTextContent("생각할 문제의 방식을 선택해요.");
    expect(screen.getByRole("note")).toHaveTextContent("적용 · 3/5");
    expect(screen.getByRole("note")).toHaveTextContent("설계 · 5/5");
    expect(screen.getByRole("note")).toHaveTextContent("내 실력을 측정한 결과가 아니에요.");
    expect(trigger).toHaveAttribute("aria-expanded", "true");

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("note")).not.toBeInTheDocument();

    fireEvent.click(trigger);
    fireEvent.pointerDown(screen.getByRole("button", { name: "바깥" }));
    expect(screen.queryByRole("note")).not.toBeInTheDocument();

    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole("button", { name: "출제 난이도 도움말 닫기" }));
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });
});
