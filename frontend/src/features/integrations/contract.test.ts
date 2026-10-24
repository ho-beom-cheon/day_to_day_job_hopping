import { describe, expect, it } from "vitest";
import { jobReceipt, pushSubscription, settings, slackDeliveries } from "./contract";

const validSettings = {
  studyReminder: { enabled: true, time: "19:30" },
  testReminder: { enabled: true, beforeMinutes: 60 },
  streakReminder: { enabled: true, time: "21:00" },
  weeklyReport: { enabled: true, dayOfWeek: "SUNDAY", time: "20:00" },
  pushEnabled: false,
  scheduledRemindersEnabled: false,
  updatedAt: "2026-09-23T12:00:00+09:00",
  revision: 1,
};

describe("integration settings contract", () => {
  it("decodes the canonical TestReminder.beforeMinutes field", () => {
    expect(settings(validSettings).testReminder.beforeMinutes).toBe(60);
  });

  it("rejects the removed minutesBefore field", () => {
    expect(() => settings({
      ...validSettings,
      testReminder: { enabled: true, minutesBefore: 60 },
    })).toThrow(/beforeMinutes/);
  });

  it("keeps provider endpoint and keys out of the subscription view", () => {
    const decoded = pushSubscription({
      subscriptionId: "601", deviceId: "00000000-0000-4000-8000-000000000001",
      deviceName: "Windows Chrome", active: true,
      createdAt: "2026-09-23T12:00:00+09:00", updatedAt: "2026-09-23T12:00:00+09:00",
      endpoint: "https://provider.example/secret", keys: { auth: "secret" },
    });
    expect(decoded).not.toHaveProperty("endpoint");
    expect(decoded).not.toHaveProperty("keys");
  });

  it("decodes Slack delivery history without exposing a webhook", () => {
    const decoded = slackDeliveries({
      content: [{
        deliveryId: "901", eventType: "SLACK_TEST", status: "UNKNOWN",
        createdAt: "2026-09-23T12:00:00+09:00", completedAt: null,
        errorCode: "SLACK_RESULT_UNKNOWN", webhookUrl: "https://hooks.slack.com/services/secret",
      }],
      page: 0, size: 5, totalElements: 1, totalPages: 1,
    });
    expect(decoded.content[0].status).toBe("UNKNOWN");
    expect(decoded.content[0]).not.toHaveProperty("webhookUrl");
  });

  it("keeps large Slack job ids as strings", () => {
    expect(jobReceipt({
      jobId: "9223372036854775807", status: "READY",
      statusPath: "/api/v1/integrations/slack/deliveries/9223372036854775807",
      createdAt: "2026-09-23T12:00:00+09:00", reused: false,
    }).jobId).toBe("9223372036854775807");
  });
});
