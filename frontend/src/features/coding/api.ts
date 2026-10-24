import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeEnvelope } from "@/api/contract";
import { readId, type Id } from "@/types/id";

type RecordValue = Record<string, unknown>;
const object = (value: unknown, label: string): RecordValue => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as RecordValue;
};
const text = (value: unknown, label: string) => { if (typeof value !== "string") throw new TypeError(`${label} must be a string`); return value; };
const optional = (value: unknown, label: string) => value === null || value === undefined ? null : text(value, label);
const integer = (value: unknown, label: string) => { if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`); return value as number; };
const optionalInteger = (value: unknown, label: string) => value === null || value === undefined ? null : integer(value, label);

export const categories = [
  ["DATA_STRUCTURE", "자료구조"], ["SORT", "정렬"], ["BRUTE_FORCE", "완전 탐색"], ["GRAPH_SEARCH", "BFS·DFS"], ["BINARY_SEARCH", "이분 탐색"],
  ["TWO_POINTER", "투 포인터·누적합"], ["GREEDY", "그리디"], ["DP", "DP"], ["STRING", "문자열"], ["IMPLEMENTATION", "구현"], ["OTHER", "기타"],
  ["SQL_SELECT", "SQL SELECT·필터"], ["SQL_AGGREGATE", "SQL 집계·GROUP BY"], ["SQL_JOIN", "SQL JOIN"], ["SQL_SUBQUERY", "SQL 서브쿼리"],
  ["SQL_WINDOW", "SQL 윈도 함수"], ["SQL_FUNCTION", "SQL NULL·날짜·문자열"],
] as const;
export type Category = typeof categories[number][0];
export const categoryLabel = Object.fromEntries(categories) as Record<Category, string>;
export const results = [["SUCCESS", "성공"], ["FAILED", "실패"], ["TIMEOUT", "시간 초과"], ["SOLUTION_VIEWED", "해설 참고"]] as const;
export type Result = typeof results[number][0];
export const resultLabel = Object.fromEntries(results) as Record<Result, string>;
export const reasonLabel: Record<Recommendation["reason"], string> = { RETRY_DUE: "다시 풀 날", WEAK_CATEGORY: "약한 유형", NEW_CATEGORY: "처음 푸는 유형", NEXT: "다음 문제" };

const category = (value: unknown): Category => {
  const code = text(value, "category");
  if (!(code in categoryLabel)) throw new TypeError("Unknown category");
  return code as Category;
};
const result = (value: unknown): Result => {
  const code = text(value, "result");
  if (!(code in resultLabel)) throw new TypeError("Unknown result");
  return code as Result;
};

export type ProblemLink = { linkId: Id; site: string; siteProblemRef: string | null; title: string; url: string; level: number | null; category: Category; own: boolean; hidden: boolean; attempts: number; lastResult: Result | null };
export type PracticeLog = { logId: Id; problemLinkId: Id | null; solvedOn: string; site: string; problemTitle: string; problemUrl: string | null; category: Category; language: string; result: Result; minutes: number | null; stuckNote: string; note: string; source: "MANUAL" | "GITHUB"; retryStep: number; nextRetryAt: string | null; revision: number };
export type Recommendation = { reason: "RETRY_DUE" | "WEAK_CATEGORY" | "NEW_CATEGORY" | "NEXT"; link: ProblemLink | null; retryOf: PracticeLog | null };
export type CategoryStat = { category: Category; attempts: number; successes: number };
export type CodingOverview = { weeklyTarget: number; weekStart: string; weekDone: number; recommendations: Recommendation[]; dueRetries: PracticeLog[]; categoryStats: CategoryStat[] };
export type LogInput = { problemLinkId: Id | null; title: string; url: string; category: Category | ""; language: string; result: Result; minutes: number | null; stuckNote: string; note: string; solvedOn: string };

export function decodeLink(value: unknown): ProblemLink {
  const data = object(value, "link");
  return { linkId: readId(data.linkId), site: text(data.site, "site"), siteProblemRef: optional(data.siteProblemRef, "siteProblemRef"), title: text(data.title, "title"),
    url: text(data.url, "url"), level: optionalInteger(data.level, "level"), category: category(data.category), own: data.own === true, hidden: data.hidden === true,
    attempts: integer(data.attempts, "attempts"), lastResult: data.lastResult === null || data.lastResult === undefined ? null : result(data.lastResult) };
}

export function decodeLog(value: unknown): PracticeLog {
  const data = object(value, "log");
  if (data.source !== "MANUAL" && data.source !== "GITHUB") throw new TypeError("Unknown source");
  return { logId: readId(data.logId), problemLinkId: data.problemLinkId === null || data.problemLinkId === undefined ? null : readId(data.problemLinkId),
    solvedOn: text(data.solvedOn, "solvedOn"), site: text(data.site, "site"), problemTitle: text(data.problemTitle, "problemTitle"), problemUrl: optional(data.problemUrl, "problemUrl"),
    category: category(data.category), language: text(data.language, "language"), result: result(data.result), minutes: optionalInteger(data.minutes, "minutes"),
    stuckNote: text(data.stuckNote, "stuckNote"), note: text(data.note, "note"), source: data.source, retryStep: integer(data.retryStep, "retryStep"),
    nextRetryAt: optional(data.nextRetryAt, "nextRetryAt"), revision: integer(data.revision, "revision") };
}

export function decodeOverview(value: unknown): CodingOverview {
  const data = object(value, "overview");
  const list = (items: unknown, label: string) => { if (!Array.isArray(items)) throw new TypeError(`${label} must be a list`); return items; };
  return { weeklyTarget: integer(data.weeklyTarget, "weeklyTarget"), weekStart: text(data.weekStart, "weekStart"), weekDone: integer(data.weekDone, "weekDone"),
    recommendations: list(data.recommendations, "recommendations").map(item => {
      const row = object(item, "recommendation");
      if (!(typeof row.reason === "string" && row.reason in reasonLabel)) throw new TypeError("Unknown reason");
      return { reason: row.reason as Recommendation["reason"], link: row.link ? decodeLink(row.link) : null, retryOf: row.retryOf ? decodeLog(row.retryOf) : null };
    }),
    dueRetries: list(data.dueRetries, "dueRetries").map(decodeLog),
    categoryStats: list(data.categoryStats, "categoryStats").map(item => { const row = object(item, "stat"); return { category: category(row.category), attempts: integer(row.attempts, "attempts"), successes: integer(row.successes, "successes") }; }) };
}

const list = <T,>(decode: (value: unknown) => T) => (value: unknown) => decodeEnvelope(value, data => { if (!Array.isArray(data)) throw new TypeError("Expected a list"); return data.map(decode); }).data;
const one = <T,>(decode: (value: unknown) => T) => (value: unknown) => decodeEnvelope(value, decode).data;
const settings = (value: unknown) => ({ weeklyTarget: integer(object(value, "settings").weeklyTarget, "weeklyTarget") });

export const codingKeys = { all: ["coding"] as const };
export const codingOverviewQuery = queryOptions({ queryKey: [...codingKeys.all, "overview"], queryFn: async () => (await api.request({ path: "/api/v1/coding", decode: one(decodeOverview) })).data });
export const codingSettingsQuery = queryOptions({ queryKey: [...codingKeys.all, "settings"], queryFn: async () => (await api.request({ path: "/api/v1/coding/settings", decode: one(settings) })).data });
export const codingLinksQuery = queryOptions({ queryKey: [...codingKeys.all, "links"], queryFn: async () => (await api.request({ path: "/api/v1/coding/links", decode: list(decodeLink) })).data });
export const codingLogsQuery = queryOptions({ queryKey: [...codingKeys.all, "logs"], queryFn: async () => (await api.request({ path: "/api/v1/coding/logs", decode: list(decodeLog) })).data });

export const updateCodingTarget = async (weeklyTarget: number) => (await api.request({ path: "/api/v1/coding/settings", method: "PUT", body: { weeklyTarget }, decode: one(settings) })).data;
export const createLink = async (body: { title: string; url: string; level: number | null; category: Category }) => (await api.request({ path: "/api/v1/coding/links", method: "POST", body, decode: one(decodeLink) })).data;
export const setLinkHidden = async (id: Id, hidden: boolean) => (await api.request({ path: `/api/v1/coding/links/${id}/hidden`, method: hidden ? "PUT" : "DELETE", decode: one(decodeLink) })).data;
export const createLog = async (input: LogInput) => (await api.request({ path: "/api/v1/coding/logs", method: "POST", decode: one(decodeLog), body: {
  problemLinkId: input.problemLinkId, title: input.title, url: input.url, category: input.category || null, language: input.language, result: input.result,
  minutes: input.minutes, stuckNote: input.stuckNote, note: input.note, solvedOn: input.solvedOn || null } })).data;
export const addRetry = async (log: PracticeLog) => (await api.request({ path: `/api/v1/coding/logs/${log.logId}/retry`, method: "POST", etag: `"${log.revision}"`, decode: one(decodeLog) })).data;
