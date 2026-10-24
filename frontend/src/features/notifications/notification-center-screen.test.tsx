import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api";
import type { NotificationItem } from "./contract";
import { NotificationCenterScreen } from "./notification-center-screen";
import { notificationsQuery } from "./queries";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/api", () => ({ api: { request: vi.fn() } }));

const item: NotificationItem = {
  notificationId: "501", type: "STUDY_REMINDER", title: "학습 기록", message: "저장됐어요.",
  targetPath: "/progress", read: false, createdAt: "2026-09-24T11:39:00+09:00", readAt: null,
};
const serverTime = "2026-09-25T10:00:00+09:00";

function setup(notification = item) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
  client.setQueryData(notificationsQuery({ read: undefined, page: 0, size: 10 }).queryKey, {
    page: { content: [notification], page: 0, size: 10, totalElements: 1, totalPages: 1, unreadCount: notification.read ? 0 : 1 },
    serverTime,
  });
  render(<QueryClientProvider client={client}><NotificationCenterScreen/></QueryClientProvider>);
}

beforeEach(() => { vi.resetAllMocks(); });

describe("notification confirmation", () => {
  it("marks an unread notification before navigating", async () => {
    setup();
    let finishRead!: (value: { data: unknown; etag: string | null }) => void;
    vi.mocked(api.request).mockImplementation(options => {
      if (options.path.includes("/read")) return new Promise(resolve => { finishRead = resolve; });
      return Promise.resolve({ data: { data: { content: [{ ...item, read: true, readAt: serverTime }], page: 0, size: 10, totalElements: 1, totalPages: 1, unreadCount: 0 }, meta: { serverTime } }, etag: null });
    });
    fireEvent.click(screen.getByRole("button", { name: "확인하기" }));
    await waitFor(() => expect(api.request).toHaveBeenCalledWith(expect.objectContaining({ path: "/api/v1/notifications/501/read", method: "POST" })));
    expect(push).not.toHaveBeenCalled();
    await act(async () => finishRead({ data: { data: { ...item, read: true, readAt: serverTime } }, etag: null }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/progress"));
  });

  it("stays on the notification when saving read state fails", async () => {
    setup();
    vi.mocked(api.request).mockRejectedValue(new Error("read failed"));
    fireEvent.click(screen.getByRole("button", { name: "확인하기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("읽음 상태를 저장하지 못했습니다");
    expect(push).not.toHaveBeenCalled();
  });

  it("opens an already read notification without another read request", async () => {
    setup({ ...item, read: true, readAt: serverTime });
    fireEvent.click(screen.getByRole("button", { name: "확인하기" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/progress"));
    expect(api.request).not.toHaveBeenCalled();
  });
});
