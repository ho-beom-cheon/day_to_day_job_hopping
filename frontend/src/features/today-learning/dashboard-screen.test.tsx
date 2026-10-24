import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TodayDashboardScreen } from "./dashboard-screen";
import { extraLearningHistoryQuery } from "@/features/extra-learning/active-extra-sessions";
import { todayLearningKeys } from "./queries";

function renderDashboard(data: unknown, extras: unknown[] = []) {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
  client.setQueryData(todayLearningKeys.dashboard(), data);
  client.setQueryData(extraLearningHistoryQuery.queryKey, extras);
  return render(<QueryClientProvider client={client}><TodayDashboardScreen /></QueryClientProvider>);
}

describe("TodayDashboardScreen", () => {
  it("shows evaluation-before separately from available progress", () => {
    renderDashboard({ curriculum: { curriculumId: "1", title: "6개월 과정", totalRequiredSessions: 10, completedRequiredSessions: 2, currentWeek: 1, currentDayNo: 1, completionRate: 20 }, today: { learningDayId: "11", curriculumId: "1", date: "2026-09-21", dayNo: 1, weekNo: 1, monthNo: 1, kind: "STUDY", title: "기본기", description: "핵심 개념", status: "IN_PROGRESS", estimatedMinutes: 30, actualMinutes: 0, progressRate: 0, completedSessionCount: 0, totalSessionCount: 1, scheduleRevision: 1, sessions: [{ sessionId: "101", category: "JAVA", title: "Java", sequence: 1, required: true, estimatedMinutes: 30, status: "NOT_STARTED" }] }, recentSessions: [{ sessionId: "99", title: "이전 학습", category: "JAVA", status: "COMPLETED", scheduledDate: "2026-09-20" }] }, [{ sessionId: "49", mode: "EXPLORE", title: "10일차 · 정렬과 컬렉션 경계", category: "JAVA", status: "IN_PROGRESS", courseStatus: "ACTIVE", targetMinutes: 60, recordedMinutes: 0, todayMinutes: 0, scheduledDate: "2026-09-20" }]);
    expect(screen.getByText("배정된 학습 완료율")).toBeInTheDocument();
    expect(screen.getByText("20%")).toBeInTheDocument();
    expect(screen.getAllByText("평가 전")).toHaveLength(2);
    expect(screen.getByRole("link", { name: /오늘 정규 학습 시작/ })).toHaveAttribute("href", "/workspace/learning/101");
    expect(screen.getByRole("heading", { name: "진행 중인 추가 학습" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /10일차 · 정렬과 컬렉션 경계/ })).toHaveAttribute("href", "/workspace/learning/49");
    expect(screen.getByText("최근 활동을 더 채워볼까요?")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /이전 학습/ })).toHaveAttribute("href", "/workspace/learning/99");
    expect(screen.queryByText("첫 활동을 만들어 볼까요?")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /학습 이어가기/ })).toHaveAttribute("href", "/workspace/learning/101");
    expect(screen.getByLabelText("오늘의 개발자 명언")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /얼굴$/ })).toBeInTheDocument();
    expect(screen.queryByText("Plan. Code. Study. Grow.")).not.toBeInTheDocument();
  });

  it("does not invent learning when no curriculum exists", () => {
    renderDashboard({ curriculum: null, today: null, recentSessions: [] });
    expect(screen.getByRole("heading", { name: "아직 배정된 과정이 없어요" })).toBeInTheDocument();
    expect(screen.getByText("과정 배정 후 시작 가능")).toHaveAttribute("aria-disabled", "true");
  });
});
