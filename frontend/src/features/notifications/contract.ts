import { decodeEnvelope, type ApiEnvelope } from "@/api/contract";
import { readId, type Id } from "@/types/id";

const notificationTypes = ["STUDY_REMINDER", "TEST_REMINDER", "STREAK_REMINDER", "WEEKLY_REPORT", "MONTHLY_REPORT", "INTEGRATION_FAILURE"] as const;
export type NotificationType = typeof notificationTypes[number];
export type NotificationItem = {
  notificationId: Id;
  type: NotificationType;
  title: string;
  message: string;
  targetPath: string;
  read: boolean;
  createdAt: string;
  readAt: string | null;
};
export type NotificationPage = {
  content: NotificationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
};
export type ReadAllResult = { updatedCount: number; through: string };

type ValueRecord = Record<string, unknown>;
const record = (value: unknown, label: string): ValueRecord => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as ValueRecord;
};
const text = (value: unknown, label: string) => {
  if (typeof value !== "string") throw new TypeError(`${label} must be a string`);
  return value;
};
const integer = (value: unknown, label: string) => {
  if (!Number.isInteger(value) || (value as number) < 0) throw new TypeError(`${label} must be a non-negative integer`);
  return value as number;
};

function notification(value: unknown): NotificationItem {
  const item = record(value, "notification");
  const type = text(item.type, "type");
  if (!notificationTypes.includes(type as NotificationType)) throw new TypeError("Unsupported notification type");
  const targetPath = text(item.targetPath, "targetPath");
  if (!/^\/(?!\/)[A-Za-z0-9/_?=&.\-]*$/.test(targetPath)) throw new TypeError("targetPath must be an internal path");
  if (typeof item.read !== "boolean") throw new TypeError("read must be a boolean");
  return {
    notificationId: readId(item.notificationId), type: type as NotificationType,
    title: text(item.title, "title"), message: text(item.message, "message"), targetPath,
    read: item.read, createdAt: text(item.createdAt, "createdAt"),
    readAt: item.readAt === null ? null : text(item.readAt, "readAt"),
  };
}

function page(value: unknown): NotificationPage {
  const result = record(value, "notification page");
  if (!Array.isArray(result.content)) throw new TypeError("content must be an array");
  return {
    content: result.content.map(notification), page: integer(result.page, "page"), size: integer(result.size, "size"),
    totalElements: integer(result.totalElements, "totalElements"), totalPages: integer(result.totalPages, "totalPages"),
    unreadCount: integer(result.unreadCount, "unreadCount"),
  };
}

export const decodeNotificationsEnvelope = (value: unknown): ApiEnvelope<NotificationPage> => decodeEnvelope(value, page);
export const decodeNotificationEnvelope = (value: unknown): ApiEnvelope<NotificationItem> => decodeEnvelope(value, notification);
export const decodeReadAllEnvelope = (value: unknown): ApiEnvelope<ReadAllResult> => decodeEnvelope(value, raw => {
  const result = record(raw, "read all result");
  return { updatedCount: integer(result.updatedCount, "updatedCount"), through: text(result.through, "through") };
});
