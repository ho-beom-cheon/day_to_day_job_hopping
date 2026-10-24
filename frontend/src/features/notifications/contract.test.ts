import { describe, expect, it } from "vitest";
import { decodeNotificationsEnvelope } from "./contract";

const payload = (targetPath = "/progress", type = "STUDY_REMINDER") => ({
  success: true,
  data: { content: [{ notificationId: "501", type, title: "학습 기록", message: "저장됐어요.", targetPath, read: false, createdAt: "2026-09-23T18:00:00+09:00", readAt: null }], page: 0, size: 20, totalElements: 1, totalPages: 1, unreadCount: 1 },
  error: null,
  meta: { traceId: "0".repeat(32), serverTime: "2026-09-23T18:00:01+09:00" },
});

describe("notification contract", () => {
  it("keeps identifier strings and decodes the page", () => {
    const decoded = decodeNotificationsEnvelope(payload());
    expect(decoded.data.content[0]?.notificationId).toBe("501");
    expect(decoded.data.unreadCount).toBe(1);
  });
  it("rejects external paths and unknown types", () => {
    expect(() => decodeNotificationsEnvelope(payload("https://example.com"))).toThrow(/internal path/);
    expect(() => decodeNotificationsEnvelope(payload("/progress", "OTHER"))).toThrow(/Unsupported/);
  });
});
