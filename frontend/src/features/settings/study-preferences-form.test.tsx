import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import { ApiError } from "@/api/client";
import type { UserSettings } from "./contract";
import { userSettingsKey } from "./queries";
import { StudyPreferencesForm } from "./study-preferences-form";

vi.mock("@/api", () => ({ api: { request: vi.fn() } }));
const value: UserSettings = {
  studyPolicy: { dailyStudyMinutes: 75, restDaysPerWeek: 1, restWeekdays: ["WEDNESDAY"] },
  weekdayStudyStartTime: "19:30", weekendStudyStartTime: "14:00",
  theme: "SYSTEM", language: "KO", timeZone: "Asia/Seoul", revision: 3, updatedAt: "2026-09-24T07:00:00+09:00",
};
function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
  client.setQueryData(userSettingsKey, value);
  render(<QueryClientProvider client={client}><StudyPreferencesForm/></QueryClientProvider>);
  return client;
}
const editMinutes = (minutes: string) => fireEvent.change(screen.getByRole("spinbutton", { name: "하루 학습 시간 (분)" }), { target: { value: minutes } });
beforeEach(() => vi.resetAllMocks());

describe("study preferences", () => {
  it("saves the complete policy with its original revision and uses the server result", async () => {
    setup();
    expect(screen.getByRole("button", { name: "수요일 휴식" })).toHaveAttribute("aria-pressed", "true");
    editMinutes("90");
    fireEvent.click(screen.getByRole("button", { name: "금요일 휴식" }));
    vi.mocked(api.request).mockResolvedValue({ data: { ...value, revision: 4, studyPolicy: { dailyStudyMinutes: 90, restDaysPerWeek: 2, restWeekdays: ["WEDNESDAY", "FRIDAY"] } }, etag: '"4"' });
    fireEvent.click(screen.getByRole("button", { name: "학습 기본값 저장" }));
    await screen.findByText("학습 기본값을 저장했어요.");
    expect(api.request).toHaveBeenCalledWith(expect.objectContaining({ path: "/api/v1/settings", method: "PATCH", etag: '"3"', body: {
      studyPolicy: { dailyStudyMinutes: 90, restDaysPerWeek: 2, restWeekdays: ["WEDNESDAY", "FRIDAY"] }, weekdayStudyStartTime: "19:30", weekendStudyStartTime: "14:00",
    } }));
    expect(screen.getByRole("button", { name: "학습 기본값 저장" })).toBeDisabled();
  });

  it("keeps a dirty draft when a background refetch discovers a newer revision", async () => {
    const client = setup();
    editMinutes("90");
    await act(async () => { client.setQueryData(userSettingsKey, { ...value, revision: 4, studyPolicy: { ...value.studyPolicy, dailyStudyMinutes: 120 } }); });
    expect(screen.getByRole("spinbutton")).toHaveValue(90);
    expect(await screen.findByRole("alert")).toHaveTextContent("다른 화면에서");
    expect(screen.getByRole("button", { name: "학습 기본값 저장" })).toBeDisabled();
    expect(api.request).not.toHaveBeenCalled();
  });

  it("recovers from a server conflict only after explicitly loading the latest settings", async () => {
    setup(); editMinutes("90");
    vi.mocked(api.request).mockRejectedValueOnce(new ApiError(412, null, null));
    fireEvent.click(screen.getByRole("button", { name: "학습 기본값 저장" }));
    await screen.findByRole("alert");
    editMinutes("100");
    expect(screen.getByRole("button", { name: "학습 기본값 저장" })).toBeDisabled();
    vi.mocked(api.request).mockResolvedValueOnce({ data: { ...value, revision: 4, studyPolicy: { ...value.studyPolicy, dailyStudyMinutes: 120 } }, etag: '"4"' });
    fireEvent.click(screen.getByRole("button", { name: "최신 값 불러오기" }));
    await waitFor(() => expect(screen.getByRole("spinbutton")).toHaveValue(120));
    editMinutes("130");
    expect(screen.getByRole("button", { name: "학습 기본값 저장" })).toBeEnabled();
  });

  it("retains inputs on a failed save and prevents invalid study minutes", async () => {
    setup(); editMinutes("14");
    expect(screen.getByRole("button", { name: "학습 기본값 저장" })).toBeDisabled();
    editMinutes("90");
    vi.mocked(api.request).mockRejectedValueOnce(new Error("offline"));
    fireEvent.click(screen.getByRole("button", { name: "학습 기본값 저장" }));
    await screen.findByRole("alert");
    expect(screen.getByRole("spinbutton")).toHaveValue(90);
    expect(screen.getByRole("button", { name: "학습 기본값 저장" })).toBeEnabled();
  });

  it("allows at most six rest days", () => {
    setup();
    for (const day of ["월", "화", "목", "금", "토"]) fireEvent.click(screen.getByRole("button", { name: `${day}요일 휴식` }));
    expect(screen.getByRole("button", { name: "일요일 휴식" })).toBeDisabled();
  });
});
