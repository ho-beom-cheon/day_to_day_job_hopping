import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BrowserConnectionControls } from "./browser-connection-controls";
import { decodeBrowserConnection } from "./browser-connections";
import { decodeAvailableTargets } from "./notion-target-picker";

const mocks = vi.hoisted(() => ({ status: vi.fn(), authorize: vi.fn(), disconnect: vi.fn() }));
vi.mock("./browser-connections", async importOriginal => ({
  ...await importOriginal<typeof import("./browser-connections")>(),
  browserConnectionQuery: (provider: string) => ({ queryKey: ["integrations", "browser", provider], queryFn: mocks.status }),
  authorizeBrowserConnection: mocks.authorize,
  disconnectBrowserConnection: mocks.disconnect,
}));
const ready = { configured: true, connected: false, revision: 0, channelName: null, workspaceName: null, model: "test/model", managementUrl: null };
function show() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}><BrowserConnectionControls provider="openrouter"/></QueryClientProvider>);
}
beforeEach(() => { vi.clearAllMocks(); });
describe("browser connections", () => {
  it("disables authorization until the operator setup is ready", async () => {
    mocks.status.mockResolvedValue({ ...ready, configured: false }); show();
    await screen.findByText(/운영자의 최초 앱 설정/);
    expect(screen.getByRole("button", { name: "OpenRouter 연결" })).toBeDisabled();
    expect(mocks.authorize).not.toHaveBeenCalled();
  });
  it("shows a recoverable failure when authorization cannot start", async () => {
    mocks.status.mockResolvedValue(ready); mocks.authorize.mockRejectedValue(new Error("offline")); show();
    await waitFor(() => expect(screen.getByRole("button", { name: "OpenRouter 연결" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "OpenRouter 연결" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("완료하지 못했습니다");
    expect(mocks.authorize).toHaveBeenCalledWith("openrouter");
  });
  it("disconnects with the displayed revision and explains provider revocation", async () => {
    mocks.status.mockResolvedValue({ ...ready, connected: true, revision: 7 });
    mocks.disconnect.mockImplementation(async () => { mocks.status.mockResolvedValue({ ...ready, revision: 8 }); return { data: { disconnected: true } }; });
    show(); fireEvent.click(await screen.findByRole("button", { name: "연결 해제" }));
    expect(await screen.findByRole("status")).toHaveTextContent("공급자에서도 권한을 없애려면");
    expect(mocks.disconnect).toHaveBeenCalledWith("openrouter", 7);
    await waitFor(() => expect(screen.queryByRole("button", { name: "연결 해제" })).not.toBeInTheDocument());
  });
  it("rejects ambiguous status and preserves paginated Notion target IDs", () => {
    expect(() => decodeBrowserConnection({ ...ready, connected: "false" })).toThrow();
    expect(decodeAvailableTargets({ content: [{ title: "학습 기록", databaseId: "db-1", dataSourceId: "source-1" }], nextCursor: "next-page" })).toEqual({ content: [{ title: "학습 기록", databaseId: "db-1", dataSourceId: "source-1" }], nextCursor: "next-page" });
    expect(() => decodeAvailableTargets({ content: [{ title: "broken" }], nextCursor: null })).toThrow();
  });
});
