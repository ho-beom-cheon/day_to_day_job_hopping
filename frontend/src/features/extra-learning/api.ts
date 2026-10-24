import { api } from "@/api";
import { createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";
import { readId } from "@/types/id";

export type ExtraMode = "DEEPEN" | "EXPLORE" | "REVIEW" | "AHEAD";
export type ExtraTopic = { contentVersionId: string; sourceSessionId: string | null; title: string; category: string; problemCount: number; wrongCount: number; stage?: string | null; recommendedWeek?: number | null; learningObjective?: string | null; generated?: boolean };
export type ExtraEntry = { sessionId: string; mode: ExtraMode; title: string; category: string; status: string; courseStatus: string; targetMinutes: number; recordedMinutes: number; todayMinutes: number; scheduledDate: string };
export type ExtraStart = { mode: ExtraMode; contentVersionId: string | null; sourceSessionId: string | null; targetMinutes: number };
const modes = ["DEEPEN", "EXPLORE", "REVIEW", "AHEAD"] as const;
function object(value: unknown): Record<string, unknown> { if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError("Invalid extra learning data"); return value as Record<string, unknown>; }
function text(value: unknown): string { if (typeof value !== "string") throw new TypeError("Expected text"); return value; }
function integer(value: unknown): number { if (!Number.isInteger(value) || (value as number) < 0) throw new TypeError("Expected nonnegative integer"); return value as number; }
function list<T>(value: unknown, decode: (item: unknown) => T): T[] { if (!Array.isArray(value)) throw new TypeError("Expected list"); return value.map(decode); }
export function decodeExtraEntry(value: unknown): ExtraEntry {
  const d = object(value); const mode = text(d.mode); if (!modes.includes(mode as ExtraMode)) throw new TypeError("Unknown extra mode");
  const status = text(d.status); if (!["NOT_STARTED", "IN_PROGRESS", "COMPLETED"].includes(status)) throw new TypeError("Unknown status");
  return { sessionId: readId(d.sessionId), mode: mode as ExtraMode, title: text(d.title), category: text(d.category), status, courseStatus: text(d.courseStatus), targetMinutes: integer(d.targetMinutes), recordedMinutes: integer(d.recordedMinutes), todayMinutes: integer(d.todayMinutes), scheduledDate: text(d.scheduledDate) };
}
export function decodeExtraTopic(value: unknown): ExtraTopic { const d = object(value); return { contentVersionId: readId(d.contentVersionId), sourceSessionId: d.sourceSessionId === null ? null : readId(d.sourceSessionId), title: text(d.title), category: text(d.category), problemCount: integer(d.problemCount), wrongCount: integer(d.wrongCount), stage: d.stage == null ? null : text(d.stage), recommendedWeek: d.recommendedWeek == null ? null : integer(d.recommendedWeek), learningObjective: d.learningObjective == null ? null : text(d.learningObjective), generated: d.generated === true }; }
export async function getExtraTopics(mode: ExtraMode) { return (await api.request({ path: `/api/v1/extra-learning/topics?mode=${mode}`, decode: v => decodeEnvelope(v, d => list(d, decodeExtraTopic)).data })).data; }
export async function getExtraHistory() { return (await api.request({ path: "/api/v1/extra-learning", decode: v => decodeEnvelope(v, d => list(d, decodeExtraEntry)).data })).data; }
export async function getExtraDetail(sessionId: string) { return (await api.request({ path: `/api/v1/extra-learning/${sessionId}`, decode: v => decodeEnvelope(v, d => d === null ? null : decodeExtraEntry(d)).data })).data; }
export type StartAction = ReturnType<typeof createMutationAction<ExtraStart>>;
export async function startExtra(action: StartAction) { return (await api.request({ path: "/api/v1/extra-learning", method: "POST", body: action.getPayload(), idempotencyKey: action.idempotencyKey, decode: v => decodeEnvelope(v, decodeExtraEntry).data })).data; }
export async function saveExtraTime(sessionId: string, action: ReturnType<typeof createMutationAction<{ actualMinutes: number }>>) { return (await api.request({ path: `/api/v1/extra-learning/${sessionId}/time`, method: "POST", body: action.getPayload(), idempotencyKey: action.idempotencyKey, decode: v => decodeEnvelope(v, decodeExtraEntry).data })).data; }
export const modeLabels: Record<ExtraMode, string> = { DEEPEN: "오늘 내용 더 익히기", EXPLORE: "다른 분야 공부하기", REVIEW: "복습하기", AHEAD: "다음 진도 이어가기" };
export const categoryLabels: Record<string, string> = { JAVA: "Java", SPRING: "Spring", SQL: "SQL", ALGORITHM: "알고리즘", FINANCE: "금융·여신", INTERVIEW: "면접", AI: "AI 개발 활용" };
