import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LearningSessionScreen } from "./session-screen";
import { todayLearningKeys } from "./queries";

describe("learning session history", () => {
  it("shows an old curriculum as a readable record without dead practice actions", () => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    client.setQueryData(todayLearningKeys.dashboard(), { curriculum: { curriculumId: "2" },
      today: { sessions: [{ sessionId: "32", status: "IN_PROGRESS" }] }, recentSessions: [] });
    client.setQueryData(todayLearningKeys.session("22"), {
      sessionId: "22", learningDayId: "5", curriculumId: "1", scheduledDate: "2026-09-27",
      category: "JAVA", title: "이전 Java 학습", description: "이전 과정", sequence: 1, required: true,
      estimatedMinutes: 45, actualMinutes: 0, status: "IN_PROGRESS", timeSource: null,
      startedAt: "2026-09-27T09:00:00+09:00", completedAt: null,
      requiredContentCount: 1, completedRequiredContentCount: 0, revision: 0,
    });
    client.setQueryData(todayLearningKeys.contents("22"), [{ contentId: "14", title: "Java 값 비교",
      completionRule: "ALL_PROBLEMS_ATTEMPTED", status: "NOT_STARTED", required: true }]);
    client.setQueryData(todayLearningKeys.content("14"), {
      contentId: "14", title: "Java 값 비교", type: "PRACTICE", required: true,
      status: "NOT_STARTED", completionRule: "ALL_PROBLEMS_ATTEMPTED", learningObjective: "값 비교",
      body: "이전 학습 본문", keyPoints: [], problems: [{ problemId: "1", title: "확인 문제", type: "MULTIPLE_CHOICE" }],
    });
    client.setQueryData(["extra-learning", "22"], null);
    render(<QueryClientProvider client={client}><LearningSessionScreen sessionId="22" /></QueryClientProvider>);

    expect(screen.getByRole("heading", { name: "현재 과정이 아닌 학습 기록이에요" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "현재 세션 열기 →" })).toHaveAttribute("href", "/workspace/learning/32");
    expect(screen.getByText("이전 과정의 학습 기록입니다. 본문만 확인할 수 있어요.")).toBeInTheDocument();
    expect(screen.getByText("이전 기록")).toBeInTheDocument();
    expect(screen.getByText("이전 학습 본문")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /확인 문제 문제 풀기/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "문제 풀이 확인하고 완료" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "세션 완료" })).not.toBeInTheDocument();
  });
});

describe("four-week hands-on learning", () => {
  it("shows the lesson and one finish action without question panels or a start gate", () => {
    const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    client.setQueryData(todayLearningKeys.dashboard(), { curriculum: { curriculumId: "2" },
      today: { sessions: [{ sessionId: "32", status: "NOT_STARTED" }] }, recentSessions: [] });
    client.setQueryData(todayLearningKeys.session("32"), {
      sessionId: "32", learningDayId: "7", curriculumId: "2", scheduledDate: "2026-09-27",
      category: "JAVA", title: "1일차 학습", description: "참조와 값 비교", sequence: 1, required: true,
      estimatedMinutes: 90, actualMinutes: 0, status: "NOT_STARTED", timeSource: null,
      startedAt: null, completedAt: null, requiredContentCount: 1, completedRequiredContentCount: 0, revision: 0,
    });
    client.setQueryData(todayLearningKeys.contents("32"), [{ contentId: "20", title: "참조와 값 비교",
      type: "PRACTICE", completionRule: "READ_ACK", status: "NOT_STARTED", required: true }]);
    client.setQueryData(todayLearningKeys.content("20"), {
      contentId: "20", title: "참조와 값 비교", type: "PRACTICE", required: true,
      status: "NOT_STARTED", completionRule: "READ_ACK", learningObjective: "값 비교",
      body: "## 직접 해보기\n두 값을 비교하세요.", keyPoints: [], problems: [{ problemId: "7", title: "별도 확인 문제", type: "MULTIPLE_CHOICE" }],
    });
    client.setQueryData(["extra-learning", "32"], null);
    render(<QueryClientProvider client={client}><LearningSessionScreen sessionId="32" /></QueryClientProvider>);

    expect(screen.getByRole("heading", { name: "직접 해보기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "오늘 학습 완료" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /AI 확인 문제/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "기본 확인 문제" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "세션 마무리" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "학습 시작" })).not.toBeInTheDocument();
  });
});
