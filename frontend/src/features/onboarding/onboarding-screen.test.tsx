import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import type { CurrentUserDto } from "@/api/contract";
import { userSettingsKey } from "@/features/settings/queries";
import { OnboardingScreen } from "./onboarding-screen";
import { curriculumTemplatesQuery } from "./queries";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/api", () => ({ api: { request: vi.fn() } }));
const defaults = {
  studyPolicy: { dailyStudyMinutes: 75, restDaysPerWeek: 1, restWeekdays: ["WEDNESDAY"] },
  weekdayStudyStartTime: "19:30", weekendStudyStartTime: "14:00",
  theme: "SYSTEM", language: "KO", timeZone: "Asia/Seoul", revision: 3, updatedAt: "2026-09-24T07:00:00+09:00",
};
const session: CurrentUserDto = { userId: "1", email: "test@example.com", nickname: "테스트", profileImageUrl: null, role: "USER", curriculumId: null, revision: 1, joinedAt: "2026-09-24T07:00:00+09:00", updatedAt: "2026-09-24T07:00:00+09:00" };
function setup(withDefaults = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(curriculumTemplatesQuery.queryKey, [{ templateId: "2", templateVersion: "v1", publishedAt: "2026-09-24T07:00:00+09:00", title: "검토된 과정", description: "학습 과정", durationMonths: 6, totalWeeks: 24, totalLearningDays: 120, totalRequiredSessions: 240 }]);
  if (withDefaults) client.setQueryData(userSettingsKey, defaults);
  render(<QueryClientProvider client={client}><OnboardingScreen session={session}/></QueryClientProvider>);
  return client;
}
beforeEach(() => vi.resetAllMocks());
describe("onboarding study defaults", () => {
  it("loads saved study policy and includes it in curriculum creation", async () => {
    setup();
    expect(screen.getByRole("spinbutton", { name: /하루 학습 시간/ })).toHaveValue(75);
    expect(screen.getByRole("button", { name: "수" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "토" })).toHaveAttribute("aria-pressed", "false");
    vi.mocked(api.request).mockRejectedValue(new Error("stop before creating test curriculum"));
    fireEvent.click(screen.getByRole("button", { name: "이 계획으로 시작하기" }));
    await waitFor(() => expect(api.request).toHaveBeenCalledWith(expect.objectContaining({ path: "/api/v1/curriculums", body: expect.objectContaining({ studyPolicy: defaults.studyPolicy }) })));
  });
  it("preserves user edits when saved defaults refresh", async () => {
    const client = setup();
    fireEvent.change(screen.getByRole("spinbutton"), { target: { value: "95" } });
    fireEvent.click(screen.getByRole("button", { name: "금" }));
    await act(async () => { client.setQueryData(userSettingsKey, { ...defaults, revision: 4, studyPolicy: { dailyStudyMinutes: 120, restDaysPerWeek: 0, restWeekdays: [] } }); });
    expect(screen.getByRole("spinbutton")).toHaveValue(95);
    expect(screen.getByRole("button", { name: "금" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "수" })).toHaveAttribute("aria-pressed", "true");
  });
  it("does not silently use invented defaults when the settings request fails", async () => {
    vi.mocked(api.request).mockRejectedValue(new Error("offline"));
    setup(false);
    await screen.findByText("학습 기본값을 불러오지 못했어요.");
    expect(screen.getByRole("button", { name: "이 계획으로 시작하기" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "학습 기본값 다시 불러오기" })).toBeEnabled();
  });
});
