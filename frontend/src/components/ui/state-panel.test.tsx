import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { StatePanel, type UiState } from "./state-panel";
import { Button } from "./button";

describe("shared UI states", () => {
  it.each(["loading", "empty", "error", "disabled", "completed", "processing", "conflict-stale", "external-sync-partial-failure"] as UiState[])("renders %s accessibly", state => {
    render(<StatePanel state={state} />);
    expect(screen.getByRole("heading")).toBeVisible();
    expect(screen.queryByRole("button", {name:/취소/})).not.toBeInTheDocument();
  });

  it("offers an explicit refresh for stale state without re-submitting a mutation", () => {
    const refresh = vi.fn();
    render(<StatePanel state="conflict-stale" onRefresh={refresh} />);
    fireEvent.click(screen.getByRole("button", {name:"최신 정보 불러오기"}));
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("keeps core success distinct from external sync failure", () => {
    render(<StatePanel state="external-sync-partial-failure" />);
    expect(screen.getByRole("alert")).toHaveTextContent("학습 기록은 저장되었어요");
  });

  it("prevents disabled actions", () => {
    const action = vi.fn();
    render(<Button disabled onClick={action}>학습 시작</Button>);
    fireEvent.click(screen.getByRole("button"));
    expect(action).not.toHaveBeenCalled();
  });
});
