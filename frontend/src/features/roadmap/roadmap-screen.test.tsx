import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import { ApiError } from "@/api/client";
import type { CurriculumTemplateDto } from "@/features/onboarding/contract";
import { curriculumTemplatesQuery } from "@/features/onboarding/queries";
import type { CurriculumDto, SchedulePreviewDto } from "./contract";
import { currentCurriculumQuery, roadmapMonthsQuery, roadmapWeeksQuery } from "./queries";
import { RoadmapScreen } from "./roadmap-screen";

vi.mock("@/api", () => ({ api: { request: vi.fn() } }));

const course = {
  curriculumId: "3", templateId: "1", durationMonths: 6, title: "학습 과정", startDate: "2026-09-27",
  nominalEndDate: "2027-03-26", endDate: "2026-10-02", totalWeeks: 1,
  totalLearningDays: 6, completedRequiredSessions: 0, totalRequiredSessions: 6,
  completionRate: 0, status: "ACTIVE", scheduleRevision: 3,
  studyPolicy: { dailyStudyMinutes: 60, restDaysPerWeek: 2, restWeekdays: ["SATURDAY", "SUNDAY"] },
} satisfies CurriculumDto;
const preview: SchedulePreviewDto = {
  previewId: "4", curriculumId: "3", scheduleRevision: 3, action: "REST_ADD",
  baseDate: "2026-09-28", targetDate: "2026-09-28", reason: "PERSONAL",
  affectedLearningDayCount: 2, previousEndDate: "2026-10-02", newEndDate: "2026-10-03",
  expiresAt: "2099-09-27T02:30:00+09:00", moves: [], warnings: [],
};

function setup(templates: CurriculumTemplateDto[] = []) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(currentCurriculumQuery.queryKey, { data: course, etag: '"3"' });
  client.setQueryData(roadmapMonthsQuery("3").queryKey, []);
  client.setQueryData(roadmapWeeksQuery("3").queryKey, []);
  client.setQueryData(curriculumTemplatesQuery.queryKey, templates);
  render(<QueryClientProvider client={client}><RoadmapScreen /></QueryClientProvider>);
}

beforeEach(() => { vi.resetAllMocks(); sessionStorage.clear(); });

describe("roadmap schedule changes", () => {
  it("offers a reviewed course switch and sends the selected template with a revision", async () => {
    const target: CurriculumTemplateDto = { templateId: "2", templateVersion: "2026.09.1", title: "Java 4주", description: "20일 학습", durationMonths: 1, totalWeeks: 4, totalLearningDays: 20, totalRequiredSessions: 20, publishedAt: "2026-09-26T17:56:28+09:00" };
    vi.mocked(api.request).mockImplementation(async options => {
      if (options.path.endsWith("/switch")) return { data: { ...course, curriculumId: "4", templateId: "2", title: target.title, totalLearningDays: 20 }, etag: '"1"' } as never;
      return { data: [], etag: null } as never;
    });
    setup([target]);
    expect(screen.getByText("20학습일 · 필수 세션 20개", { exact: false })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: /현재 과정을 종료하고 선택한 과정의 1일차부터 시작할게요/ }));
    fireEvent.click(screen.getByRole("button", { name: "선택한 과정으로 바꾸기" }));
    await waitFor(() => expect(api.request).toHaveBeenCalledWith(expect.objectContaining({
      path: "/api/v1/curriculums/3/switch", method: "POST", body: expect.objectContaining({ templateId: "2" }), etag: '"3"', idempotencyKey: expect.any(String),
    })));
  });
  it("discards a preview when its rest reason changes and shows the reason in the new preview", async () => {
    vi.mocked(api.request).mockImplementation(async options => ({ data: { ...preview, reason: (options.body as { reason: "PERSONAL" | "VACATION" }).reason }, etag: null }) as never);
    setup();
    fireEvent.change(screen.getByLabelText("대상 날짜"), { target: { value: "2026-09-28" } });
    fireEvent.click(screen.getByRole("button", { name: "변경 결과 미리보기" }));
    await screen.findByText("대상 날짜: 9월 28일 · 사유: 개인 일정", {}, { timeout: 3000 });
    fireEvent.change(screen.getByLabelText("휴식 사유"), { target: { value: "VACATION" } });
    expect(screen.queryByText("대상 날짜: 9월 28일 · 사유: 개인 일정")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "이대로 일정 확정" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "변경 결과 미리보기" }));
    await screen.findByText("대상 날짜: 9월 28일 · 사유: 휴가", {}, { timeout: 3000 });
    expect(api.request).toHaveBeenLastCalledWith(expect.objectContaining({ body: expect.objectContaining({ reason: "VACATION" }) }));
  });

  it("keeps a started-day rejection visible instead of presenting refresh as success", async () => {
    vi.mocked(api.request).mockRejectedValueOnce(new ApiError(409, { success: false, data: null, error: {
      code: "RESCHEDULE_LOCKED_DAY", message: "시작했거나 완료한 학습일은 이동할 수 없습니다.", details: [], retryable: false, retryAfterSeconds: null,
    } }, null));
    setup();
    fireEvent.click(screen.getByRole("button", { name: "변경 결과 미리보기" }));
    await screen.findByText("이미 시작한 학습일이에요.");
    expect(screen.getByText("시작했거나 완료한 학습은 휴식일로 옮길 수 없어요. 아직 시작하지 않은 날짜를 선택해 주세요.")).toBeInTheDocument();
    await waitFor(() => expect(api.request).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/최신 일정을 불러왔/)).not.toBeInTheDocument();
  });

  it("explains a rejected confirmation when learning started after the preview", async () => {
    const startedPreview = { ...preview, baseDate: "2026-09-27", targetDate: "2026-09-27", reason: "VACATION" as const };
    vi.mocked(api.request).mockImplementation(async options => {
      if (options.path.endsWith("/reschedule-preview")) return { data: startedPreview, etag: null } as never;
      if (options.path === "/api/v1/rest-days") throw new ApiError(412, { success: false, data: null, error: {
        code: "PRECONDITION_FAILED", message: "일정이 변경되었습니다.", details: [], retryable: false, retryAfterSeconds: null,
      } }, null);
      if (options.path.startsWith("/api/v1/learning-days/")) return { data: { status: "IN_PROGRESS" }, etag: null } as never;
      if (options.path === "/api/v1/curriculums/current") return { data: { ...course, scheduleRevision: 4 }, etag: '"4"' } as never;
      return { data: [], etag: null } as never;
    });
    setup();
    fireEvent.change(screen.getByLabelText("휴식 사유"), { target: { value: "VACATION" } });
    fireEvent.click(screen.getByRole("button", { name: "변경 결과 미리보기" }));
    await screen.findByRole("button", { name: "이대로 일정 확정" });
    fireEvent.click(screen.getByRole("button", { name: "이대로 일정 확정" }));
    await screen.findByText("9월 27일 학습을 이미 시작해 휴식일로 바꿀 수 없어요. 변경은 반영되지 않았습니다. 아직 시작하지 않은 날짜를 선택해 주세요.");
    expect(screen.queryByRole("button", { name: "이대로 일정 확정" })).not.toBeInTheDocument();
    expect(api.request).toHaveBeenCalledWith(expect.objectContaining({ path: "/api/v1/learning-days/2026-09-27?curriculumId=3" }));
  });
});
