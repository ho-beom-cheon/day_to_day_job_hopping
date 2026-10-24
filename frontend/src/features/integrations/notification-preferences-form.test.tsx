import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import { ApiError } from "@/api/client";
import type { NotificationSettings } from "./contract";
import { NotificationPreferencesForm } from "./notification-preferences-form";
import { settingsQuery } from "./queries";

vi.mock("@/api", () => ({ api: { request: vi.fn() } }));

const original: NotificationSettings = {
  studyReminder: { enabled: true, time: "19:30" },
  testReminder: { enabled: true, beforeMinutes: 30 },
  streakReminder: { enabled: true, time: "21:00" },
  weeklyReport: { enabled: true, dayOfWeek: "SUNDAY", time: "20:00" },
  pushEnabled: false, scheduledRemindersEnabled: false, revision: 3, updatedAt: "2026-09-25T09:00:00+09:00",
};

function setup(value = original) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  client.setQueryData(settingsQuery.queryKey, value);
  render(<QueryClientProvider client={client}><NotificationPreferencesForm value={value}/></QueryClientProvider>);
  return client;
}

beforeEach(() => vi.resetAllMocks());

describe("notification preferences", () => {
  it("saves edited times, interval, and weekday with the original revision", async () => {
    const client = setup();
    fireEvent.change(screen.getAllByLabelText("매일 (한국 시간)")[0], { target: { value: "08:15" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "시험 시작 전 (분)" }), { target: { value: "45" } });
    fireEvent.change(screen.getByRole("combobox", { name: "요일" }), { target: { value: "MONDAY" } });
    fireEvent.change(screen.getByLabelText("시간 (한국 시간)"), { target: { value: "09:10" } });
    const saved = { ...original, revision: 4, studyReminder: { ...original.studyReminder, time: "08:15" }, testReminder: { ...original.testReminder, beforeMinutes: 45 }, weeklyReport: { ...original.weeklyReport, dayOfWeek: "MONDAY", time: "09:10" } };
    vi.mocked(api.request).mockResolvedValue({ data: saved, etag: '"4"' });
    fireEvent.click(screen.getByRole("button", { name: "알림 설정 저장" }));
    await screen.findByText("알림 설정을 저장했어요.");
    expect(api.request).toHaveBeenCalledWith(expect.objectContaining({
      path: "/api/v1/notifications/settings", method: "PATCH", etag: '"3"',
      body: { studyReminder: saved.studyReminder, testReminder: saved.testReminder, streakReminder: saved.streakReminder, weeklyReport: saved.weeklyReport, scheduledRemindersEnabled: false },
    }));
    expect(client.getQueryData(settingsQuery.queryKey)).toEqual(saved);
  });

  it("keeps invalid intervals from being saved", () => {
    setup();
    fireEvent.change(screen.getByRole("spinbutton", { name: "시험 시작 전 (분)" }), { target: { value: "0" } });
    expect(screen.getByRole("button", { name: "알림 설정 저장" })).toBeDisabled();
    expect(api.request).not.toHaveBeenCalled();
  });

  it("requires an explicit saved choice before scheduled reminders begin", async () => {
    setup();
    expect(screen.getByRole("checkbox", { name: /정기 알림 받기/ })).not.toBeChecked();
    fireEvent.click(screen.getByRole("checkbox", { name: /정기 알림 받기/ }));
    vi.mocked(api.request).mockResolvedValue({ data: { ...original, scheduledRemindersEnabled: true, revision: 4 }, etag: '"4"' });
    fireEvent.click(screen.getByRole("button", { name: "알림 설정 저장" }));
    await screen.findByText("알림 설정을 저장했어요.");
    expect(api.request).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.objectContaining({ scheduledRemindersEnabled: true }), etag: '"3"',
    }));
  });

  it("keeps the draft and blocks a stale revision", async () => {
    setup();
    fireEvent.change(screen.getByRole("spinbutton", { name: "시험 시작 전 (분)" }), { target: { value: "45" } });
    vi.mocked(api.request).mockRejectedValueOnce(new ApiError(412, null, null));
    fireEvent.click(screen.getByRole("button", { name: "알림 설정 저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("다른 화면에서");
    expect(screen.getByRole("spinbutton", { name: "시험 시작 전 (분)" })).toHaveValue(45);
    expect(screen.getByRole("button", { name: "알림 설정 저장" })).toBeDisabled();
  });
});
