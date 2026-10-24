import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AiPracticeScreen } from "./ai-practice-screen";
import { getPracticeSet, reportPracticeQuestion, submitPracticeAnswer } from "./practice-api";

vi.mock("./practice-api", () => ({ getPracticeSet: vi.fn(), submitPracticeAnswer: vi.fn(), reportPracticeQuestion: vi.fn() }));

const questions = Array.from({ length: 10 }, (_, index) => ({
  number: index + 1, prompt: `AI 질문 ${index + 1}`, choices: ["A", "B", "C", "D"],
  submittedAnswer: null, correct: null, correctAnswer: null, explanation: null,
}));

describe("AI practice screen", () => {
  it("renders code in choices and reveals formatted explanation only after submitting", async () => {
    const formatted = { ...questions[0], prompt: '금액 계산 코드를 확인하세요.\n\n```java\nBigDecimal value = new BigDecimal(0.1);\n```\n\n어떻게 수정할까요?',
      choices: ['```java\nnew BigDecimal("0.1");\n```', "double 사용", "float 사용", "정수 변환"] };
    vi.mocked(getPracticeSet).mockResolvedValue({ jobId: "6", contentId: "2", sessionId: "7", questions: [formatted, questions[1]] });
    vi.mocked(submitPracticeAnswer).mockResolvedValue({ ...formatted, submittedAnswer: 1,
      correct: true, correctAnswer: 1, explanation: '문자열로 생성해야 합니다.\n\n```output\n0.1\n```' });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<QueryClientProvider client={client}><AiPracticeScreen jobId="6" /></QueryClientProvider>);

    expect(await screen.findByText("어떻게 수정할까요?")).toBeInTheDocument();
    expect(screen.getAllByLabelText("코드 · Java")).toHaveLength(2);
    expect(screen.queryByLabelText("출력 예시")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("radio", { name: /1\. 코드 · Java/ }));
    fireEvent.click(screen.getByRole("button", { name: "답안 제출" }));
    expect(await screen.findByLabelText("출력 예시")).toHaveTextContent("0.1");
    expect(screen.getByRole("button", { name: "다음 문제" })).toBeEnabled();
  });

  it("shows feedback before moving to the next generated question", async () => {
    vi.mocked(getPracticeSet).mockResolvedValue({ jobId: "6", contentId: "2", sessionId: "7", questions });
    vi.mocked(submitPracticeAnswer).mockResolvedValue({ ...questions[0], submittedAnswer: 2,
      correct: true, correctAnswer: 2, explanation: "두 번째 선택지가 맞습니다." });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<QueryClientProvider client={client}><AiPracticeScreen jobId="6" /></QueryClientProvider>);

    expect(await screen.findByText("AI 질문 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다음 문제" })).toBeDisabled();
    fireEvent.click(screen.getByRole("radio", { name: "2. B" }));
    fireEvent.click(screen.getByRole("button", { name: "답안 제출" }));

    expect(await screen.findByText("두 번째 선택지가 맞습니다.")).toBeInTheDocument();
    expect(screen.getByText("AI 질문 1")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다음 문제" }));
    expect(screen.getByText("AI 질문 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "답안 제출" })).toBeDisabled();
  });

  it("hides AI feedback after the learner reports an incorrect answer", async () => {
    const submitted = { ...questions[0], submittedAnswer: 1, correct: false, correctAnswer: 2,
      explanation: "확인이 필요한 해설", sourceExcerpt: "학습 원문", reported: false };
    vi.mocked(getPracticeSet).mockResolvedValueOnce({ jobId: "6", contentId: "2", sessionId: "7",
      questions: [submitted, questions[1]] }).mockResolvedValueOnce({ jobId: "6", contentId: "2", sessionId: "7",
      questions: [{ ...submitted, reported: true }, questions[1]] });
    vi.mocked(reportPracticeQuestion).mockResolvedValue({});
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<QueryClientProvider client={client}><AiPracticeScreen jobId="6" /></QueryClientProvider>);

    await screen.findByText("AI 질문 2");
    fireEvent.click(screen.getByRole("button", { name: "이전 문제" }));
    expect(await screen.findByText("확인이 필요한 해설")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "정답·해설 오류 알리기" }));
    fireEvent.change(screen.getByRole("textbox", { name: "어떤 부분이 잘못됐나요?" }),
      { target: { value: "정답이 본문 설명과 다릅니다." } });
    fireEvent.click(screen.getByRole("button", { name: "오류로 표시" }));
    expect(await screen.findByText(/이후 다시 학습과 연습 시험에서 제외됩니다/)).toBeInTheDocument();
    expect(screen.queryByText("확인이 필요한 해설")).not.toBeInTheDocument();
  });
});
