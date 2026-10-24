import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import {
  decodeDeleted,
  decodeJobReceipt,
  decodeNotion,
  decodeNotionAuthorization,
  decodeNotionDisconnect,
  decodePush,
  decodePushSubscription,
  decodePushSubscriptions,
  decodeSettings,
  decodeSlack,
  decodeSlackDeliveries,
  decodeSlackDelivery,
  type NotificationSettings,
  type SlackConnection,
} from "./contract";
import type { BrowserPushSubscription } from "./web-push";

export const integrationKeys = { all: ["integrations"] as const };

export const settingsQuery = queryOptions({
  queryKey: [...integrationKeys.all, "settings"],
  queryFn: async () => (await api.request({ path: "/api/v1/notifications/settings", decode: decodeSettings })).data.data,
});
export const notionQuery = queryOptions({
  queryKey: [...integrationKeys.all, "notion"],
  queryFn: async () => (await api.request({ path: "/api/v1/integrations/notion", decode: decodeNotion })).data.data,
});
export const slackQuery = queryOptions({
  queryKey: [...integrationKeys.all, "slack"],
  queryFn: async () => (await api.request({ path: "/api/v1/integrations/slack", decode: decodeSlack })).data.data,
});
export const slackDeliveriesQuery = queryOptions({
  queryKey: [...integrationKeys.all, "slack-deliveries"],
  queryFn: async () => (await api.request({
    path: "/api/v1/integrations/slack/deliveries?page=0&size=5&sort=createdAt,desc",
    decode: decodeSlackDeliveries,
  })).data.data,
  refetchInterval: (query) => query.state.data?.content.some(({ status }) => status === "READY" || status === "PROCESSING") ? 2_000 : false,
});
export const slackDeliveryQuery = (deliveryId: string) => queryOptions({
  queryKey: [...integrationKeys.all, "slack-deliveries", deliveryId],
  queryFn: async () => (await api.request({
    path: `/api/v1/integrations/slack/deliveries/${encodeURIComponent(deliveryId)}`,
    decode: decodeSlackDelivery,
  })).data.data,
});
export const pushQuery = queryOptions({
  queryKey: [...integrationKeys.all, "push"],
  queryFn: async () => (await api.request({ path: "/api/v1/push/config", decode: decodePush })).data.data,
});
export const pushSubscriptionsQuery = queryOptions({
  queryKey: [...integrationKeys.all, "push-subscriptions"],
  queryFn: async () => (await api.request({ path: "/api/v1/push/subscriptions", decode: decodePushSubscriptions })).data.data,
});

export const updateSettings = ({ body, revision }: {
  body: Partial<Pick<NotificationSettings, "studyReminder" | "testReminder" | "streakReminder" | "weeklyReport" | "pushEnabled" | "scheduledRemindersEnabled">>;
  revision: number;
}) => api.request({
  path: "/api/v1/notifications/settings",
  method: "PATCH",
  body,
  etag: `"${revision}"`,
  decode: (value) => decodeSettings(value).data,
});

export const updateSlack = ({ body, revision }: {
  body: Partial<Pick<SlackConnection, "enabled" | "eventTypes">>;
  revision: number;
}) => api.request({
  path: "/api/v1/integrations/slack",
  method: "PATCH",
  body,
  etag: `"${revision}"`,
  decode: (value) => decodeSlack(value).data,
});

export const startNotionAuthorization = () => api.request({
  path: "/api/v1/integrations/notion/authorization-requests",
  method: "POST",
  idempotencyKey: crypto.randomUUID(),
  decode: (value) => decodeNotionAuthorization(value).data,
});

export const disconnectNotion = () => api.request({
  path: "/api/v1/integrations/notion",
  method: "DELETE",
  decode: (value) => decodeNotionDisconnect(value).data,
});

export const sendSlackTest = () => api.request({
  path: "/api/v1/integrations/slack/test-messages",
  method: "POST",
  idempotencyKey: crypto.randomUUID(),
  decode: (value) => decodeJobReceipt(value).data,
});

export const registerPushSubscription = ({ deviceId, deviceName, subscription }: {
  deviceId: string;
  deviceName: string;
  subscription: BrowserPushSubscription;
}) => api.request({
  path: "/api/v1/push/subscriptions",
  method: "POST",
  body: { deviceId, deviceName, ...subscription },
  idempotencyKey: crypto.randomUUID(),
  decode: (value) => decodePushSubscription(value).data,
});

export const deletePushSubscription = (subscriptionId: string) => api.request({
  path: `/api/v1/push/subscriptions/${subscriptionId}`,
  method: "DELETE",
  decode: (value) => decodeDeleted(value).data,
});
