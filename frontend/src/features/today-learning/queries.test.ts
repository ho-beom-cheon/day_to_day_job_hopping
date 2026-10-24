import { describe, expect, it, vi } from "vitest";

describe("session completion action", () => {
  it("keeps one frozen payload and idempotency key for retries", async () => {
    vi.stubGlobal("crypto", { randomUUID: () => "00000000-0000-4000-8000-000000000011" });
    const { createSessionCompletionAction } = await import("./queries");
    const action = createSessionCompletionAction(42);
    expect(action.idempotencyKey).toBe("00000000-0000-4000-8000-000000000011");
    expect(action.getPayload()).toEqual({ actualMinutes: 42 });
    expect(action.getPayload()).not.toBe(action.getPayload());
    vi.unstubAllGlobals();
  });
});
