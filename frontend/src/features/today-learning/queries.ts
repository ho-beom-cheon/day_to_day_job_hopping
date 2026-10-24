import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";
import { decodeContentComplete, decodeContentList, decodeDashboard, decodeLearningContent, decodeSession, decodeSessionComplete } from "./contract";

export const todayLearningKeys = {
  all: ["today-learning"] as const,
  dashboard: () => [...todayLearningKeys.all, "dashboard"] as const,
  session: (sessionId: string) => [...todayLearningKeys.all, "session", sessionId] as const,
  contents: (sessionId: string) => [...todayLearningKeys.session(sessionId), "contents"] as const,
  content: (contentId: string) => [...todayLearningKeys.all, "content", contentId] as const,
};

export const dashboardQuery = queryOptions({
  queryKey: todayLearningKeys.dashboard(),
  queryFn: async () => (await api.request({ path: "/api/v1/dashboard", decode: value => decodeEnvelope(value, decodeDashboard).data })).data,
});

export const learningSessionQuery = (sessionId: string) => queryOptions({ queryKey: todayLearningKeys.session(sessionId), queryFn: async () => (await api.request({ path: `/api/v1/learning-sessions/${sessionId}`, decode: value => decodeEnvelope(value, decodeSession).data })).data });
export const sessionContentsQuery = (sessionId: string) => queryOptions({ queryKey: todayLearningKeys.contents(sessionId), queryFn: async () => (await api.request({ path: `/api/v1/learning-sessions/${sessionId}/contents`, decode: value => decodeEnvelope(value, decodeContentList).data })).data });
export const learningContentQuery = (contentId: string) => queryOptions({ queryKey: todayLearningKeys.content(contentId), queryFn: async () => (await api.request({ path: `/api/v1/learning-contents/${contentId}`, decode: value => decodeEnvelope(value, decodeLearningContent).data })).data });

export async function startLearningSession(sessionId: string) { return (await api.request({ path: `/api/v1/learning-sessions/${sessionId}/start`, method: "POST", decode: value => decodeEnvelope(value, decodeSession).data })).data; }
export async function completeLearningContent(contentId: string) { return (await api.request({ path: `/api/v1/learning-contents/${contentId}/complete`, method: "POST", decode: value => decodeEnvelope(value, decodeContentComplete).data })).data; }
export type CompletionAction = ReturnType<typeof createMutationAction<{ actualMinutes: number | null }>>;
export function createSessionCompletionAction(actualMinutes: number | null): CompletionAction { return createMutationAction({ actualMinutes }); }
export async function completeLearningSession(sessionId: string, action: CompletionAction) { return (await api.request({ path: `/api/v1/learning-sessions/${sessionId}/complete`, method: "POST", body: action.getPayload(), idempotencyKey: action.idempotencyKey, decode: value => decodeEnvelope(value, decodeSessionComplete).data })).data; }
