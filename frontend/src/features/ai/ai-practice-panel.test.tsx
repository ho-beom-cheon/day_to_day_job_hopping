import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AiPracticePanel } from "./ai-practice-panel";
import { getPracticeJob, requestPracticeQuestions } from "./practice-api";
import type { PracticeJob } from "./practice-contract";

vi.mock("./practice-api", () => ({ getPracticeJob: vi.fn(), requestPracticeQuestions: vi.fn() }));

const unavailable: PracticeJob = {
  jobId: "12", status: "FAILED", questionCount: 12, latestQuestionCount: null,
  estimatedMinutes: 45, targetDifficulty: 3, difficultyLevels: Array(12).fill(3),
  qualityRejected: true, generationAvailable: false, availableDifficulties: [], rejectedQuestionCount: 0,
};

function showPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><AiPracticePanel contentId="14"
    basicPracticeHref="/practice/1?contentId=14" /></QueryClientProvider>);
}

describe("AI practice panel", () => {
  it("sends a learner with a short source straight to the basic question", async () => {
    vi.mocked(getPracticeJob).mockResolvedValue(unavailable);
    showPanel();

    expect(await screen.findByRole("link", { name: "기본 확인 문제 풀기 →" }))
      .toHaveAttribute("href", "/practice/1?contentId=14");
    expect(screen.getByText("이 학습 자료는 AI 문제를 만들 만큼 준비되지 않았어요.")).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "출제 난이도" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "현재 과정 확인하기" })).not.toBeInTheDocument();
    expect(screen.getByText(/이전 AI 생성 결과는 검사를 통과하지 못해/)).toBeInTheDocument();
    expect(requestPracticeQuestions).not.toHaveBeenCalled();
  });

  it("offers a difficulty that supports fewer questions before requesting AI", async () => {
    vi.mocked(getPracticeJob).mockImplementation(async (_contentId, difficulty) => difficulty === 5
      ? { ...unavailable, status: "NONE", questionCount: 8, targetDifficulty: 5,
        difficultyLevels: Array(8).fill(5), qualityRejected: false,
        generationAvailable: true, availableDifficulties: [5] }
      : { ...unavailable, status: "NONE", availableDifficulties: [5] });
    showPanel();

    fireEvent.click(await screen.findByRole("button", { name: "설계 중심 · 5/5로 바꾸기" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "AI 문제 8개 만들기" })).toBeInTheDocument());
    expect(getPracticeJob).toHaveBeenCalledWith("14", 5);
    expect(requestPracticeQuestions).not.toHaveBeenCalled();
  });
});
