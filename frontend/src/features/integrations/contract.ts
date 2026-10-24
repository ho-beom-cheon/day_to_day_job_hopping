import { decodeEnvelope, type ApiEnvelope } from "@/api/contract";

type RecordValue = Record<string, unknown>;

const object = (value: unknown, label: string): RecordValue => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as RecordValue;
};
const string = (value: unknown, label: string) => {
  if (typeof value !== "string") throw new TypeError(`${label} must be a string`);
  return value;
};
const boolean = (value: unknown, label: string) => {
  if (typeof value !== "boolean") throw new TypeError(`${label} must be boolean`);
  return value;
};
const integer = (value: unknown, label: string) => {
  if (!Number.isInteger(value)) throw new TypeError(`${label} must be integer`);
  return value as number;
};
const nullableString = (value: unknown, label: string) => value === null ? null : string(value, label);

export type TimedReminder = { enabled: boolean; time: string };
export type NotificationSettings = {
  studyReminder: TimedReminder;
  testReminder: { enabled: boolean; beforeMinutes: number };
  streakReminder: TimedReminder;
  weeklyReport: { enabled: boolean; dayOfWeek: string; time: string };
  pushEnabled: boolean;
  scheduledRemindersEnabled: boolean;
  updatedAt: string;
  revision: number;
};
export type NotionConnection = {
  connected: boolean;
  status: "DISCONNECTED" | "CONNECTED" | "REAUTH_REQUIRED";
  workspaceName: string | null;
  workspaceId: string | null;
  notionVersion: string;
  connectionRevision: number;
};
export type NotionAuthorization = { authorizationUrl: string; expiresAt: string };
export type NotionDisconnect = { disconnected: boolean; providerRevocationStatus: "NOT_REQUIRED" | "PENDING" | "COMPLETED" | "FAILED" };
export type SlackConnection = {
  configured: boolean;
  channelName: string | null;
  enabled: boolean;
  eventTypes: string[];
  updatedAt: string;
  revision: number;
};
export type SlackDeliveryStatus = "READY" | "PROCESSING" | "SUCCESS" | "FAILED" | "UNKNOWN";
export type SlackDelivery = {
  deliveryId: string;
  eventType: string;
  status: SlackDeliveryStatus;
  createdAt: string;
  completedAt: string | null;
  errorCode: string | null;
};
export type SlackDeliveryPage = {
  content: SlackDelivery[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type JobReceipt = {
  jobId: string;
  status: "READY" | "PROCESSING" | "SUCCESS";
  statusPath: string;
  createdAt: string;
  reused: boolean;
};
export type PushConfig = { enabled: boolean; publicKey: string | null };
export type PushSubscription = {
  subscriptionId: string;
  deviceId: string;
  deviceName: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};
export type DeletedResult = { deleted: boolean };

const timedReminder = (value: unknown) => {
  const data = object(value, "reminder");
  return { enabled: boolean(data.enabled, "enabled"), time: string(data.time, "time") };
};

const slackDeliveryStatus = (value: unknown): SlackDeliveryStatus => {
  const status = string(value, "status");
  if (!["READY", "PROCESSING", "SUCCESS", "FAILED", "UNKNOWN"].includes(status)) {
    throw new TypeError("status must be a known Slack delivery status");
  }
  return status as SlackDeliveryStatus;
};

const jobStatus = (value: unknown): JobReceipt["status"] => {
  const status = string(value, "status");
  if (!["READY", "PROCESSING", "SUCCESS"].includes(status)) throw new TypeError("status must be a known job status");
  return status as JobReceipt["status"];
};

export function settings(value: unknown): NotificationSettings {
  const data = object(value, "settings");
  const test = object(data.testReminder, "testReminder");
  const weekly = object(data.weeklyReport, "weeklyReport");
  return {
    studyReminder: timedReminder(data.studyReminder),
    testReminder: { enabled: boolean(test.enabled, "enabled"), beforeMinutes: integer(test.beforeMinutes, "beforeMinutes") },
    streakReminder: timedReminder(data.streakReminder),
    weeklyReport: {
      enabled: boolean(weekly.enabled, "enabled"),
      dayOfWeek: string(weekly.dayOfWeek, "dayOfWeek"),
      time: string(weekly.time, "time"),
    },
    pushEnabled: boolean(data.pushEnabled, "pushEnabled"),
    scheduledRemindersEnabled: boolean(data.scheduledRemindersEnabled, "scheduledRemindersEnabled"),
    updatedAt: string(data.updatedAt, "updatedAt"),
    revision: integer(data.revision, "revision"),
  };
}

export function notion(value: unknown): NotionConnection {
  const data = object(value, "notion");
  const status = string(data.status, "status") as NotionConnection["status"];
  return {
    connected: boolean(data.connected, "connected"),
    status,
    workspaceName: nullableString(data.workspaceName, "workspaceName"),
    workspaceId: nullableString(data.workspaceId, "workspaceId"),
    notionVersion: string(data.notionVersion, "notionVersion"),
    connectionRevision: integer(data.connectionRevision, "connectionRevision"),
  };
}

export function notionAuthorization(value: unknown): NotionAuthorization {
  const data = object(value, "notion authorization");
  return { authorizationUrl: string(data.authorizationUrl, "authorizationUrl"), expiresAt: string(data.expiresAt, "expiresAt") };
}
export function notionDisconnect(value: unknown): NotionDisconnect {
  const data = object(value, "notion disconnect");
  return { disconnected: boolean(data.disconnected, "disconnected"), providerRevocationStatus: string(data.providerRevocationStatus, "providerRevocationStatus") as NotionDisconnect["providerRevocationStatus"] };
}
export function slack(value: unknown): SlackConnection {
  const data = object(value, "slack");
  if (!Array.isArray(data.eventTypes)) throw new TypeError("eventTypes must be array");
  return {
    configured: boolean(data.configured, "configured"),
    channelName: nullableString(data.channelName, "channelName"),
    enabled: boolean(data.enabled, "enabled"),
    eventTypes: data.eventTypes.map((event) => string(event, "event")),
    updatedAt: string(data.updatedAt, "updatedAt"),
    revision: integer(data.revision, "revision"),
  };
}

export function slackDelivery(value: unknown): SlackDelivery {
  const data = object(value, "slack delivery");
  return {
    deliveryId: string(data.deliveryId, "deliveryId"),
    eventType: string(data.eventType, "eventType"),
    status: slackDeliveryStatus(data.status),
    createdAt: string(data.createdAt, "createdAt"),
    completedAt: nullableString(data.completedAt, "completedAt"),
    errorCode: nullableString(data.errorCode, "errorCode"),
  };
}

export function slackDeliveries(value: unknown): SlackDeliveryPage {
  const data = object(value, "slack deliveries");
  if (!Array.isArray(data.content)) throw new TypeError("content must be an array");
  return {
    content: data.content.map(slackDelivery),
    page: integer(data.page, "page"),
    size: integer(data.size, "size"),
    totalElements: integer(data.totalElements, "totalElements"),
    totalPages: integer(data.totalPages, "totalPages"),
  };
}

export function jobReceipt(value: unknown): JobReceipt {
  const data = object(value, "job receipt");
  return {
    jobId: string(data.jobId, "jobId"),
    status: jobStatus(data.status),
    statusPath: string(data.statusPath, "statusPath"),
    createdAt: string(data.createdAt, "createdAt"),
    reused: boolean(data.reused, "reused"),
  };
}

export function push(value: unknown): PushConfig {
  const data = object(value, "push");
  return { enabled: boolean(data.enabled, "enabled"), publicKey: nullableString(data.publicKey, "publicKey") };
}

export function pushSubscription(value: unknown): PushSubscription {
  const data = object(value, "subscription");
  return {
    subscriptionId: string(data.subscriptionId, "subscriptionId"),
    deviceId: string(data.deviceId, "deviceId"),
    deviceName: string(data.deviceName, "deviceName"),
    active: boolean(data.active, "active"),
    createdAt: string(data.createdAt, "createdAt"),
    updatedAt: string(data.updatedAt, "updatedAt"),
  };
}

export function pushSubscriptions(value: unknown): PushSubscription[] {
  if (!Array.isArray(value)) throw new TypeError("subscriptions must be an array");
  return value.map(pushSubscription);
}

export function deleted(value: unknown): DeletedResult {
  const data = object(value, "deleted");
  return { deleted: boolean(data.deleted, "deleted") };
}

export const decodeSettings = (value: unknown): ApiEnvelope<NotificationSettings> => decodeEnvelope(value, settings);
export const decodeNotion = (value: unknown): ApiEnvelope<NotionConnection> => decodeEnvelope(value, notion);
export const decodeNotionAuthorization = (value: unknown): ApiEnvelope<NotionAuthorization> => decodeEnvelope(value, notionAuthorization);
export const decodeNotionDisconnect = (value: unknown): ApiEnvelope<NotionDisconnect> => decodeEnvelope(value, notionDisconnect);
export const decodeSlack = (value: unknown): ApiEnvelope<SlackConnection> => decodeEnvelope(value, slack);
export const decodeSlackDeliveries = (value: unknown): ApiEnvelope<SlackDeliveryPage> => decodeEnvelope(value, slackDeliveries);
export const decodeSlackDelivery = (value: unknown): ApiEnvelope<SlackDelivery> => decodeEnvelope(value, slackDelivery);
export const decodeJobReceipt = (value: unknown): ApiEnvelope<JobReceipt> => decodeEnvelope(value, jobReceipt);
export const decodePush = (value: unknown): ApiEnvelope<PushConfig> => decodeEnvelope(value, push);
export const decodePushSubscriptions = (value: unknown): ApiEnvelope<PushSubscription[]> => decodeEnvelope(value, pushSubscriptions);
export const decodePushSubscription = (value: unknown): ApiEnvelope<PushSubscription> => decodeEnvelope(value, pushSubscription);
export const decodeDeleted = (value: unknown): ApiEnvelope<DeletedResult> => decodeEnvelope(value, deleted);
