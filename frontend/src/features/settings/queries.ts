import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeUserSettingsEnvelope, type StudyPreferences } from "./contract";

export const userSettingsKey = ["user-settings"] as const;
export const userSettingsQuery = queryOptions({
  queryKey: userSettingsKey,
  queryFn: async () => (await api.request({ path: "/api/v1/settings", decode: decodeUserSettingsEnvelope })).data,
});

export async function updateStudyPreferences({ body, revision }: { body: StudyPreferences; revision: number }) {
  return (await api.request({
    path: "/api/v1/settings", method: "PATCH", body, etag: `"${revision}"`, decode: decodeUserSettingsEnvelope,
  })).data;
}
