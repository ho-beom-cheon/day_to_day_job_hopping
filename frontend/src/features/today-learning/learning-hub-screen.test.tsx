import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LearningHubScreen } from "./learning-hub-screen";
import { extraLearningHistoryQuery } from "@/features/extra-learning/active-extra-sessions";
import { todayLearningKeys } from "./queries";

function renderHub(data: unknown, extras: unknown[] = []) {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
  client.setQueryData(todayLearningKeys.dashboard(), data);
  client.setQueryData(extraLearningHistoryQuery.queryKey, extras);
  return render(<QueryClientProvider client={client}><LearningHubScreen /></QueryClientProvider>);
}

describe("LearningHubScreen", () => {
  it("shows today's ordered sessions and prioritizes the active session", () => {
    renderHub({ curriculum: { curriculumId: "1", title: "백엔드 이직 과정", totalRequiredSessions: 12, completedRequiredSessions: 3, currentWeek: 2, currentDayNo: 4, completionRate: 25 }, today: { learningDayId: "11", curriculumId: "1", date: "2026-09-21", dayNo: 4, weekNo: 2, monthNo: 1, kind: "STUDY", title: "Java 컬렉션", description: "컬렉션의 핵심 구조를 학습합니다.", status: "IN_PROGRESS", estimatedMinutes: 70, actualMinutes: 20, progressRate: 33, completedSessionCount: 1, totalSessionCount: 3, scheduleRevision: 1, sessions: [{ sessionId: "101", category: "JAVA", title: "List 기본", sequence: 1, required: true, estimatedMinutes: 20, status: "COMPLETED" }, { sessionId: "102", category: "JAVA", title: "Set과 Map", sequence: 2, required: true, estimatedMinutes: 30, status: "IN_PROGRESS" }, { sessionId: "103", category: "JAVA", title: "실전 문제", sequence: 3, required: false, estimatedMinutes: 20, status: "NOT_STARTED" }] }, recentSessions: [] });
    expect(screen.getByRole("heading", { name: "학습하기" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이어서 학습" })).toHaveAttribute("href", "/workspace/learning/102");
    expect(screen.getByRole("link", { name: /List 기본 완료한 세션 보기/ })).toHaveAttribute("href", "/workspace/learning/101");
    expect(screen.getByText("33%")).toBeInTheDocument();
  });

  it("does not invent sessions before curriculum assignment", () => {
    renderHub({ curriculum: null, today: null, recentSessions: [] });
    expect(screen.getByRole("heading", { name: "먼저 학습 과정을 선택해 주세요" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /학습 계획 만들기/ })).toHaveAttribute("href", "/onboarding");
  });

  it("shows active extra study separately from today's assigned session", () => {
    renderHub({ curriculum: { curriculumId: "1", title: "Java 과정" }, today: { learningDayId: "11", curriculumId: "1", date: "2026-09-21", dayNo: 2, weekNo: 1, monthNo: 1, kind: "STUDY", title: "2일차 · 동등성과 해시 계약", description: "", status: "IN_PROGRESS", estimatedMinutes: 90, actualMinutes: 0, progressRate: 0, completedSessionCount: 0, totalSessionCount: 1, scheduleRevision: 1, sessions: [{ sessionId: "3", category: "JAVA", title: "2일차 학습", sequence: 1, required: true, estimatedMinutes: 90, status: "IN_PROGRESS" }] }, recentSessions: [] }, [{ sessionId: "49", mode: "EXPLORE", title: "10일차 · 정렬과 컬렉션 경계", category: "JAVA", status: "IN_PROGRESS", courseStatus: "ACTIVE", targetMinutes: 60, recordedMinutes: 0, todayMinutes: 0, scheduledDate: "2026-09-21" }, { sessionId: "3", mode: "AHEAD", title: "2일차 학습", category: "JAVA", status: "IN_PROGRESS", courseStatus: "ACTIVE", targetMinutes: 15, recordedMinutes: 0, todayMinutes: 0, scheduledDate: "2026-09-21" }]);
    expect(screen.getByRole("heading", { name: "오늘 배정된 정규 학습 세션" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /2일차 학습 이어서 학습/ })).toHaveAttribute("href", "/workspace/learning/3");
    expect(screen.getByRole("heading", { name: "진행 중인 추가 학습" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /10일차 · 정렬과 컬렉션 경계/ })).toHaveAttribute("href", "/workspace/learning/49");
    expect(screen.getAllByRole("link", { name: /2일차 학습/ })).toHaveLength(1);
  });
});
