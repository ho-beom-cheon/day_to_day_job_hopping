import { api } from "@/api";
import { createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";
import { decodeReceipt } from "@/features/ai/contract";
import { readId } from "@/types/id";

export type LessonInput = { topic: string; subject: string; stage: string; targetMinutes: number; baseContentVersionId: string | null };
export type LessonJob = { jobId: string; topic: string; subject: string; stage: string; targetMinutes: number; status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "UNKNOWN" | "CANCELED"; errorCode: string | null; contentVersionId: string | null; title: string | null };
function text(value: unknown): string { if (typeof value !== "string") throw new TypeError("Expected text"); return value; }
export function decodeLessonJobs(value: unknown): LessonJob[] {
  if (!Array.isArray(value)) throw new TypeError("Expected lesson job list");
  return value.map((row: unknown) => {
    if (!row || typeof row !== "object") throw new TypeError("Expected lesson job");
    const d = row as Record<string, unknown>;
    const status = text(d.status);
    if (!["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "UNKNOWN", "CANCELED"].includes(status)
      || typeof d.targetMinutes !== "number" || ![30, 60, 90, 120].includes(d.targetMinutes)) throw new TypeError("Invalid lesson job");
    return { jobId: readId(d.jobId), topic: text(d.topic), subject: text(d.subject), stage: text(d.stage), targetMinutes: d.targetMinutes,
      status: status as LessonJob["status"], errorCode: d.errorCode == null ? null : text(d.errorCode),
      contentVersionId: d.contentVersionId == null ? null : readId(d.contentVersionId), title: d.title == null ? null : text(d.title) };
  });
}
export type LessonAction = ReturnType<typeof createMutationAction<LessonInput>>;
export async function createLesson(action: LessonAction) {
  return (await api.request({ path: "/api/v1/ai/learning-topics", method: "POST", body: action.getPayload(), idempotencyKey: action.idempotencyKey, decode: v => decodeEnvelope(v, decodeReceipt).data })).data;
}
export async function getLessonJobs() {
  return (await api.request({ path: "/api/v1/ai/learning-topics", decode: v => decodeEnvelope(v, decodeLessonJobs).data })).data;
}
