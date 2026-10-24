import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import type { Id } from "@/types/id";
import { decodeNotificationEnvelope, decodeNotificationsEnvelope, decodeReadAllEnvelope } from "./contract";

export const notificationKeys = { all: ["notifications"] as const };

export function notificationsQuery({ read, page = 0, size = 20 }: { read?: boolean; page?: number; size?: number }) {
  const filter = read === undefined ? "" : `&read=${read}`;
  return queryOptions({
    queryKey: [...notificationKeys.all, read ?? "all", page, size] as const,
    queryFn: async () => {
      const response = await api.request({
        path: `/api/v1/notifications?page=${page}&size=${size}&sort=createdAt,desc${filter}`,
        decode: decodeNotificationsEnvelope,
      });
      return { page: response.data.data, serverTime: response.data.meta.serverTime };
    },
  });
}

export async function markNotificationRead(notificationId: Id) {
  return (await api.request({
    path: `/api/v1/notifications/${notificationId}/read`, method: "POST", decode: decodeNotificationEnvelope,
  })).data.data;
}

export async function markAllNotificationsRead(through: string) {
  return (await api.request({
    path: "/api/v1/notifications/read-all", method: "POST", body: { through },
    idempotencyKey: crypto.randomUUID(), decode: decodeReadAllEnvelope,
  })).data.data;
}
