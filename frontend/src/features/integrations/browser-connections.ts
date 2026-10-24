import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeEnvelope } from "@/api/contract";
import { integrationKeys } from "./queries";

export type BrowserProvider = "slack" | "openrouter";
export type BrowserConnection = {
  configured: boolean; connected: boolean; revision: number; channelName: string | null;
  workspaceName: string | null; model: string | null; managementUrl: string | null;
};
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError("Invalid connection response");
  return value as Record<string, unknown>;
}
function optionalText(value: unknown): string | null {
  if (value === null) return null;
  if (typeof value !== "string") throw new TypeError("Invalid connection text");
  return value;
}
export function decodeBrowserConnection(value: unknown): BrowserConnection {
  const v = object(value);
  if (typeof v.configured !== "boolean" || typeof v.connected !== "boolean" || !Number.isSafeInteger(v.revision)) throw new TypeError("Invalid connection status");
  return { configured: v.configured, connected: v.connected, revision: v.revision as number,
    channelName: optionalText(v.channelName), workspaceName: optionalText(v.workspaceName), model: optionalText(v.model), managementUrl: optionalText(v.managementUrl) };
}
const path = (provider: BrowserProvider) => `/api/v1/integrations/browser/${provider}` as const;
export const browserConnectionQuery = (provider: BrowserProvider) => queryOptions({
  queryKey: [...integrationKeys.all, "browser", provider],
  queryFn: async () => (await api.request({ path: path(provider), decode: v => decodeEnvelope(v, decodeBrowserConnection) })).data.data,
});
export const authorizeBrowserConnection = (provider: BrowserProvider) => api.request({
  path: `${path(provider)}/authorization-requests`, method: "POST",
  decode: v => decodeEnvelope(v, value => {
    const result = object(value);
    if (typeof result.authorizationUrl !== "string") throw new TypeError("Missing authorization URL");
    const url = new URL(result.authorizationUrl);
    const expected = provider === "slack" ? "https://slack.com" : "https://openrouter.ai";
    if (url.origin !== expected || url.username || url.password) throw new TypeError("Unexpected authorization host");
    return { authorizationUrl: url.href };
  }).data,
});
export const disconnectBrowserConnection = (provider: BrowserProvider, revision: number) => api.request({
  path: path(provider), method: "DELETE", etag: `"${revision}"`,
  decode: v => decodeEnvelope(v, value => {
    const result = object(value);
    if (result.disconnected !== true) throw new TypeError("Disconnect failed");
    return { disconnected: true, managementUrl: optionalText(result.managementUrl) };
  }).data,
});
export const notionSetupQuery = queryOptions({
  queryKey: [...integrationKeys.all, "notion-setup"],
  queryFn: async () => (await api.request({ path: "/api/v1/integrations/notion/setup", decode: v => decodeEnvelope(v, value => {
    const result = object(value);
    if (typeof result.configured !== "boolean") throw new TypeError("Invalid setup status");
    return { configured: result.configured };
  }) })).data.data,
});
