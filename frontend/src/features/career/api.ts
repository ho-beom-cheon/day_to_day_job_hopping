import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeEnvelope } from "@/api/contract";
import { readId, type Id } from "@/types/id";
import { decodeTask } from "@/features/daily-task/contract";

type RecordValue = Record<string, unknown>;
const object = (value: unknown, label: string): RecordValue => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as RecordValue;
};
const text = (value: unknown, label: string) => { if (typeof value !== "string") throw new TypeError(`${label} must be a string`); return value; };
const optional = (value: unknown, label: string) => value === null || value === undefined ? null : text(value, label);
const integer = (value: unknown, label: string) => { if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`); return value as number; };

export type CareerFact = { factId: Id; projectName: string; clientName: string | null; periodFrom: string; periodTo: string | null; roleText: string; techText: string; confirmedScope: string; cautionText: string; status: "CANDIDATE" | "CONFIRMED"; revision: number };
export type FactInput = Omit<CareerFact, "factId" | "status" | "revision">;
export type ClaimReview = { status: string; issues: { type: string; detail: string }[]; likelyQuestions: string[]; suggestedRevision: string } | { errorCode: string };
export type ResumeClaim = { claimId: Id; careerFactId: Id | null; claimText: string; metricConfirmed: boolean; reviewStatus: "NEEDS_FIX" | "NEEDS_EVIDENCE" | "OK" | null; review: ClaimReview | null; reviewPending: boolean; revisedText: string | null; revision: number };

function fact(value: unknown): CareerFact {
  const data = object(value, "fact");
  if (data.status !== "CANDIDATE" && data.status !== "CONFIRMED") throw new TypeError("Unknown fact status");
  return { factId: readId(data.factId), projectName: text(data.projectName, "projectName"), clientName: optional(data.clientName, "clientName"), periodFrom: text(data.periodFrom, "periodFrom"),
    periodTo: optional(data.periodTo, "periodTo"), roleText: text(data.roleText, "roleText"), techText: text(data.techText, "techText"), confirmedScope: text(data.confirmedScope, "confirmedScope"),
    cautionText: text(data.cautionText, "cautionText"), status: data.status, revision: integer(data.revision, "revision") };
}

function claim(value: unknown): ResumeClaim {
  const data = object(value, "claim");
  const review = data.review === null || data.review === undefined ? null : object(data.review, "review");
  return { claimId: readId(data.claimId), careerFactId: data.careerFactId === null || data.careerFactId === undefined ? null : readId(data.careerFactId), claimText: text(data.claimText, "claimText"),
    metricConfirmed: data.metricConfirmed === true, reviewStatus: data.reviewStatus as ResumeClaim["reviewStatus"] ?? null,
    review: review && ("errorCode" in review ? { errorCode: text(review.errorCode, "errorCode") } : {
      status: text(review.status, "status"),
      issues: (Array.isArray(review.issues) ? review.issues : []).map(item => { const issue = object(item, "issue"); return { type: text(issue.type, "type"), detail: text(issue.detail, "detail") }; }),
      likelyQuestions: (Array.isArray(review.likelyQuestions) ? review.likelyQuestions : []).map(item => text(item, "question")),
      suggestedRevision: text(review.suggestedRevision ?? "", "suggestedRevision"),
    }),
    reviewPending: data.reviewPending === true, revisedText: optional(data.revisedText, "revisedText"), revision: integer(data.revision, "revision") };
}

const list = <T,>(decode: (value: unknown) => T) => (value: unknown) => decodeEnvelope(value, data => { if (!Array.isArray(data)) throw new TypeError("Expected a list"); return data.map(decode); }).data;
const one = <T,>(decode: (value: unknown) => T) => (value: unknown) => decodeEnvelope(value, decode).data;

export const careerKeys = { all: ["career"] as const };
export const factsQuery = queryOptions({ queryKey: [...careerKeys.all, "facts"], queryFn: async () => (await api.request({ path: "/api/v1/career/facts", decode: list(fact) })).data });
export const claimsQuery = queryOptions({
  queryKey: [...careerKeys.all, "claims"],
  queryFn: async () => (await api.request({ path: "/api/v1/career/claims", decode: list(claim) })).data,
  refetchInterval: query => query.state.data?.some(item => item.reviewPending) ? 2_000 : false,
});

export const createFact = async (input: FactInput) => (await api.request({ path: "/api/v1/career/facts", method: "POST", body: input, decode: one(fact) })).data;
export const updateFact = async (id: Id, revision: number, input: FactInput) => (await api.request({ path: `/api/v1/career/facts/${id}`, method: "PATCH", body: input, etag: `"${revision}"`, decode: one(fact) })).data;
export const confirmFact = async (id: Id, revision: number) => (await api.request({ path: `/api/v1/career/facts/${id}/confirm`, method: "POST", etag: `"${revision}"`, decode: one(fact) })).data;
export const createClaim = async (careerFactId: Id | null, claimText: string) => (await api.request({ path: "/api/v1/career/claims", method: "POST", body: { careerFactId, claimText }, decode: one(claim) })).data;
export const patchClaim = async (id: Id, revision: number, body: { metricConfirmed?: boolean; revisedText?: string }) => (await api.request({ path: `/api/v1/career/claims/${id}`, method: "PATCH", body, etag: `"${revision}"`, decode: one(claim) })).data;
export const reviewClaim = async (id: Id) => (await api.request({ path: `/api/v1/career/claims/${id}/review`, method: "POST", decode: one(claim) })).data;
export const interviewClaim = async (id: Id) => (await api.request({ path: `/api/v1/career/claims/${id}/interview`, method: "POST", decode: one(decodeTask) })).data;
