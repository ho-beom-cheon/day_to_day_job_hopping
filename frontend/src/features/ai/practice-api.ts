import { api } from "@/api";
import { createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";
import { decodeReceipt } from "./contract";
import { decodeAiRun, decodeExamHistory, decodePracticeJob, decodePracticeQuestion, decodePracticeSet,
  decodeSavedSet, decodeWrongDetail, decodeWrongSummary, type AiRunMode } from "./practice-contract";

export async function getSavedAiSets() {
  return (await api.request({ path: "/api/v1/ai/practice-sets",
    decode: value => decodeEnvelope(value, data => {
      if (!Array.isArray(data)) throw new TypeError("Saved AI sets must be a list");
      return data.map(decodeSavedSet);
    }).data })).data;
}

export async function getPracticeJob(contentId: string, difficulty: number | null = null) {
  return (await api.request({
    path: `/api/v1/ai/practice-sets/by-content/${contentId}${difficulty === null ? "" : `?difficulty=${difficulty}`}`,
    decode: value => decodeEnvelope(value, decodePracticeJob).data,
  })).data;
}

export async function requestPracticeQuestions(contentId: string, difficulty: number) {
  const action = createMutationAction({ contentId, difficulty });
  return (await api.request({
    path: "/api/v1/ai/practice-problems", method: "POST", body: action.getPayload(),
    idempotencyKey: action.idempotencyKey,
    decode: value => decodeEnvelope(value, decodeReceipt).data,
  })).data;
}

export async function getPracticeSet(jobId: string) {
  return (await api.request({
    path: `/api/v1/ai/practice-sets/${jobId}`,
    decode: value => decodeEnvelope(value, decodePracticeSet).data,
  })).data;
}

export async function submitPracticeAnswer(jobId: string, questionNo: number, answerNo: number) {
  const action = createMutationAction({ questionNo, answerNo });
  return (await api.request({
    path: `/api/v1/ai/practice-sets/${jobId}/answers`, method: "POST", body: action.getPayload(),
    idempotencyKey: action.idempotencyKey,
    decode: value => decodeEnvelope(value, decodePracticeQuestion).data,
  })).data;
}

export async function reportPracticeQuestion(jobId: string, questionNo: number, reason: string) {
  const action = createMutationAction({ questionNo, reason });
  return (await api.request({
    path: `/api/v1/ai/practice-sets/${jobId}/reports`, method: "POST", body: action.getPayload(),
    idempotencyKey: action.idempotencyKey,
    decode: value => decodeEnvelope(value, data => data).data,
  })).data;
}

export async function startAiRun(jobId: string, mode: AiRunMode) {
  const action = createMutationAction({ mode });
  return (await api.request({ path: `/api/v1/ai/practice-sets/${jobId}/runs`, method: "POST",
    body: action.getPayload(), idempotencyKey: action.idempotencyKey,
    decode: value => decodeEnvelope(value, decodeAiRun).data })).data;
}

export async function getAiRun(runId: string) {
  return (await api.request({ path: `/api/v1/ai/practice-runs/${runId}`,
    decode: value => decodeEnvelope(value, decodeAiRun).data })).data;
}

export async function submitAiRunAnswer(runId: string, questionNo: number, answerNo: number) {
  const action = createMutationAction({ questionNo, answerNo });
  return (await api.request({ path: `/api/v1/ai/practice-runs/${runId}/answers`, method: "POST",
    body: action.getPayload(), idempotencyKey: action.idempotencyKey,
    decode: value => decodeEnvelope(value, decodeAiRun).data })).data;
}

export async function getAiExamHistory() {
  return (await api.request({ path: "/api/v1/ai/practice-runs",
    decode: value => decodeEnvelope(value, data => {
      if (!Array.isArray(data)) throw new TypeError("AI exam history must be a list");
      return data.map(decodeExamHistory);
    }).data })).data;
}

export async function getAiWrongNotes(status?: "OPEN" | "RESOLVED") {
  return (await api.request({ path: `/api/v1/ai/wrong-notes${status ? `?status=${status}` : ""}`,
    decode: value => decodeEnvelope(value, data => {
      if (!Array.isArray(data)) throw new TypeError("AI wrong notes must be a list");
      return data.map(decodeWrongSummary);
    }).data })).data;
}

export async function getAiWrongDetail(jobId: string, questionNo: number) {
  return (await api.request({ path: `/api/v1/ai/wrong-notes/${jobId}/${questionNo}`,
    decode: value => decodeEnvelope(value, decodeWrongDetail).data })).data;
}

export async function updateAiWrong(jobId: string, questionNo: number, change: { note?: string; status?: "OPEN" | "RESOLVED" }) {
  return (await api.request({ path: `/api/v1/ai/wrong-notes/${jobId}/${questionNo}`,
    method: "PATCH", body: change,
    decode: value => decodeEnvelope(value, decodeWrongDetail).data })).data;
}
