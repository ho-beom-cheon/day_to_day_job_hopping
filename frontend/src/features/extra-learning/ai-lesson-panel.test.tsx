import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AiLessonPanel, type LessonSeed } from "./ai-lesson-panel";
import { createLesson, decodeLessonJobs, getLessonJobs, type LessonJob } from "./ai-lesson-api";
import { startExtra } from "./api";

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("./ai-lesson-api", async original => ({ ...await original<typeof import("./ai-lesson-api")>(), createLesson: vi.fn(), getLessonJobs: vi.fn() }));
vi.mock("./api", async original => ({ ...await original<typeof import("./api")>(), startExtra: vi.fn() }));
const seed: LessonSeed = { key: 1, topic: "송금 멱등성", subject: "SPRING", stage: "심화", targetMinutes: 90, baseContentVersionId: null };
const job: LessonJob = { jobId: "9223372036854775806", topic: seed.topic, subject: seed.subject, stage: seed.stage, targetMinutes: 90, status: "RUNNING", contentVersionId: null, title: null, errorCode: null };
function show(value: LessonSeed | null = seed) {
  const close = vi.fn();
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AiLessonPanel seed={value} onClose={close}/></QueryClientProvider>);
  return close;
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(getLessonJobs).mockResolvedValue([]); vi.mocked(createLesson).mockResolvedValue({ jobId: "1", status: "READY" }); });
describe("personal AI learning", () => {
  it("requires an explicit request and carries the selected topic, level, duration", async () => {
    const close = show();
    expect(createLesson).not.toHaveBeenCalled();
    expect(screen.getByRole("combobox", { name: "생성 학습 시간" })).toHaveValue("90");
    fireEvent.click(screen.getByRole("button", { name: "학습자료 생성" }));
    await waitFor(() => expect(createLesson).toHaveBeenCalledOnce());
    expect(vi.mocked(createLesson).mock.calls[0][0].getPayload()).toEqual({ topic: seed.topic, subject: "SPRING", stage: "심화", targetMinutes: 90, baseContentVersionId: null });
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
  });
  it("keeps the base subject fixed for a deepening request", async () => {
    show({ ...seed, baseContentVersionId: "45", baseTitle: "트랜잭션 경계" });
    expect(screen.getByRole("combobox", { name: "생성할 분야" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "학습자료 생성" }));
    await waitFor(() => expect(createLesson).toHaveBeenCalledOnce());
    expect(vi.mocked(createLesson).mock.calls[0][0].getPayload().baseContentVersionId).toBe("45");
  });
  it("restores running jobs after reopening without requesting another generation", async () => {
    vi.mocked(getLessonJobs).mockResolvedValue([job]); show(null);
    expect(await screen.findByRole("status")).toHaveTextContent("공식 자료를 확인하며");
    expect(createLesson).not.toHaveBeenCalled();
  });
  it("starts a saved lesson using the existing extra learning route", async () => {
    vi.mocked(getLessonJobs).mockResolvedValue([{ ...job, status: "SUCCEEDED", contentVersionId: "9223372036854775805", title: "멱등성 실습" }]);
    vi.mocked(startExtra).mockResolvedValue({ sessionId: "9007199254740993", mode: "EXPLORE", title: "멱등성 실습", category: "SPRING", status: "IN_PROGRESS", courseStatus: "ACTIVE", targetMinutes: 90, recordedMinutes: 0, todayMinutes: 0, scheduledDate: "2026-09-27" });
    show(null); fireEvent.click(await screen.findByRole("button", { name: "이 자료로 학습 시작" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/workspace/learning/9007199254740993"));
    expect(vi.mocked(startExtra).mock.calls[0][0].getPayload().targetMinutes).toBe(90);
  });
  it("does not offer failed candidates for learning", async () => {
    vi.mocked(getLessonJobs).mockResolvedValue([{ ...job, status: "FAILED", errorCode: "AI_LEARNING_QUALITY_REJECTED" }]); show(null);
    expect(await screen.findByText(/학습자료로 저장하지 않았어요/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "이 자료로 학습 시작" })).not.toBeInTheDocument();
    expect(createLesson).not.toHaveBeenCalled();
    expect(decodeLessonJobs([job])[0].jobId).toBe(job.jobId);
    expect(() => decodeLessonJobs([{ ...job, jobId: 9223372036854775806 }])).toThrow();
  });
});
