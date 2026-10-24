import { render, screen, within } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { claimsQuery, factsQuery } from "@/features/career/api";
import type { Today } from "@/features/daily-task/contract";
import { todayQuery } from "@/features/daily-task/queries";
import { DashboardScreen } from "./dashboard-screen";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/api", () => ({ api: { request: vi.fn(() => new Promise(() => undefined)) } }));

const baseToday: Today = { task: null, week: { target: 4, completed: 1 }, ai: { enabled: true, limitKrw: 10000, remainingKrw: 7300.4 }, recentCards: [] };

function setup(today: Today) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(todayQuery.queryKey, today);
  client.setQueryData(factsQuery.queryKey, []);
  client.setQueryData(claimsQuery.queryKey, []);
  vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(new Response(null, { status: 503 }))));
  render(<QueryClientProvider client={client}><DashboardScreen/></QueryClientProvider>);
}

beforeEach(() => { vi.unstubAllGlobals(); });

describe("DashboardScreen", () => {
  it("shows the monthly hero, week progress and an empty card prompt before a task is issued", () => {
    setup(baseToday);
    expect(screen.getByAltText("책상에서 오늘의 과제를 준비하는 호범 캐릭터").getAttribute("src")).toMatch(/\/assets\/dashboard\/monthly\/hobeom-hero-\d{2}-v3\.png/);
    expect(screen.getByText("이번 주 목표까지 과제 3개 남았어요.")).toBeTruthy();
    expect(screen.getAllByRole("button", { name: "한 과제 더 받기" })).toHaveLength(2);
    expect(screen.getByText("25%")).toBeTruthy();
    expect(screen.getByText("7,300원 남음")).toBeTruthy();
    expect(screen.getByText("첫 핵심 카드를 만들어 볼까요?")).toBeTruthy();
  });

  it("links the hero and task panel to the issued task", () => {
    setup({ ...baseToday, task: { taskId: "77", status: "IN_PROGRESS", format: "CASE", competency: { code: "TX-01", title: "트랜잭션 경계", areaTitle: "데이터 정합성", priority: "P1" }, planReason: "수준 차이가 가장 커요.", title: "이체 중복 처리", estimatedMinutes: 40, failureCode: null } });
    expect(screen.getByRole("link", { name: /오늘 과제 이어 하기/ }).getAttribute("href")).toBe("/tasks/77");
    const panel = screen.getByRole("heading", { name: "오늘의 역량 과제" }).closest("section")!;
    expect(within(panel).getByText("이체 중복 처리")).toBeTruthy();
    expect(within(panel).getByRole("link", { name: /이어 하기/ }).getAttribute("href")).toBe("/tasks/77");
  });
});
