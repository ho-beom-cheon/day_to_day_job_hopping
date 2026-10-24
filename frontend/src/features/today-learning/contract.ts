import { readId, type Id } from "@/types/id";
import { displayCurriculumTitle } from "@/features/curriculum/display-title";

type JsonRecord = Record<string, unknown>;

export type CurriculumDto = {
  curriculumId: Id;
  title: string;
  totalRequiredSessions: number;
  completedRequiredSessions: number;
  currentWeek: number | null;
  currentDayNo: number | null;
  completionRate: number | null;
};

export type SessionStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
export type DayStatus = SessionStatus | "REST" | "NONE";
export type SessionSummaryDto = { sessionId: Id; category: string; title: string; sequence: number; required: boolean; estimatedMinutes: number; status: SessionStatus };
export type RecentSessionDto = { sessionId: Id; title: string; category: string; status: "IN_PROGRESS" | "COMPLETED"; scheduledDate: string };
export type DashboardDto = { curriculum: CurriculumDto | null; today: LearningDayDto | null; recentSessions: RecentSessionDto[] };
export type LearningDayDto = {
  learningDayId: Id | null;
  curriculumId: Id;
  date: string;
  dayNo: number | null;
  weekNo: number | null;
  monthNo: number | null;
  kind: "STUDY" | "REST" | "NONE";
  title: string;
  description: string;
  status: DayStatus;
  estimatedMinutes: number;
  actualMinutes: number;
  progressRate: number | null;
  completedSessionCount: number;
  totalSessionCount: number;
  scheduleRevision: number;
  sessions: SessionSummaryDto[];
};

export type SessionDto = SessionSummaryDto & {
  learningDayId: Id;
  curriculumId: Id;
  scheduledDate: string;
  description: string;
  actualMinutes: number;
  timeSource: string | null;
  startedAt: string | null;
  completedAt: string | null;
  requiredContentCount: number;
  completedRequiredContentCount: number;
  revision: number;
};

export type ContentSummaryDto = { contentId: Id; contentVersionId: Id; title: string; type: string; sequence: number; required: boolean; status: "NOT_STARTED" | "COMPLETED"; completionRule: "READ_ACK" | "ALL_PROBLEMS_ATTEMPTED" };
export type LearningContentDto = ContentSummaryDto & { sessionId: Id; learningObjective: string; body: string; keyPoints: string[]; interviewPoints: string[]; problems: { problemId: Id; title: string; type: string }[]; completedAt: string | null };

function object(value: unknown, label: string): JsonRecord { if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`); return value as JsonRecord; }
function text(value: unknown, label: string): string { if (typeof value !== "string") throw new TypeError(`${label} must be a string`); return value; }
function integer(value: unknown, label: string): number { if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`); return value as number; }
function numberOrNull(value: unknown, label: string): number | null { if (value === null) return null; if (typeof value !== "number" || !Number.isFinite(value)) throw new TypeError(`${label} must be a number`); return value; }
function integerOrNull(value: unknown, label: string): number | null { return value === null ? null : integer(value, label); }
function nullableText(value: unknown, label: string): string | null { return value === null ? null : text(value, label); }
function boolean(value: unknown, label: string): boolean { if (typeof value !== "boolean") throw new TypeError(`${label} must be a boolean`); return value; }
function strings(value: unknown, label: string): string[] { if (!Array.isArray(value)) throw new TypeError(`${label} must be an array`); return value.map((item, index) => text(item, `${label}[${index}]`)); }
function oneOf<T extends string>(value: unknown, allowed: readonly T[], label: string): T { const result = text(value, label); if (!allowed.includes(result as T)) throw new TypeError(`${label} is not supported`); return result as T; }

export function decodeCurriculum(value: unknown): CurriculumDto | null {
  if (value === null) return null;
  const data = object(value, "curriculum");
  return { curriculumId: readId(data.curriculumId), title: displayCurriculumTitle(text(data.title, "title")), totalRequiredSessions: integer(data.totalRequiredSessions, "totalRequiredSessions"), completedRequiredSessions: integer(data.completedRequiredSessions, "completedRequiredSessions"), currentWeek: integerOrNull(data.currentWeek, "currentWeek"), currentDayNo: integerOrNull(data.currentDayNo, "currentDayNo"), completionRate: numberOrNull(data.completionRate, "completionRate") };
}

function decodeSessionStatus(value: unknown): SessionStatus { return oneOf(value, ["NOT_STARTED", "IN_PROGRESS", "COMPLETED"] as const, "status"); }
export function decodeSessionSummary(value: unknown): SessionSummaryDto { const data = object(value, "session"); return { sessionId: readId(data.sessionId), category: text(data.category, "category"), title: text(data.title, "title"), sequence: integer(data.sequence, "sequence"), required: boolean(data.required, "required"), estimatedMinutes: integer(data.estimatedMinutes, "estimatedMinutes"), status: decodeSessionStatus(data.status) }; }
export function decodeDashboard(value: unknown): DashboardDto {
  const data = object(value, "dashboard");
  if (!Array.isArray(data.recentSessions)) throw new TypeError("recentSessions must be an array");
  return {
    curriculum: decodeCurriculum(data.curriculum),
    today: decodeLearningDay(data.today),
    recentSessions: data.recentSessions.map((value, index) => {
      const session = object(value, `recentSessions[${index}]`);
      return { sessionId: readId(session.sessionId), title: text(session.title, "title"), category: text(session.category, "category"), status: oneOf(session.status, ["IN_PROGRESS", "COMPLETED"] as const, "status"), scheduledDate: text(session.scheduledDate, "scheduledDate") };
    }),
  };
}

export function decodeLearningDay(value: unknown): LearningDayDto | null {
  if (value === null) return null;
  const data = object(value, "learning day");
  if (!Array.isArray(data.sessions)) throw new TypeError("sessions must be an array");
  return { learningDayId: data.learningDayId === null ? null : readId(data.learningDayId), curriculumId: readId(data.curriculumId), date: text(data.date, "date"), dayNo: data.dayNo === null ? null : integer(data.dayNo, "dayNo"), weekNo: data.weekNo === null ? null : integer(data.weekNo, "weekNo"), monthNo: data.monthNo === null ? null : integer(data.monthNo, "monthNo"), kind: oneOf(data.kind, ["STUDY", "REST", "NONE"] as const, "kind"), title: text(data.title, "title"), description: text(data.description, "description"), status: oneOf(data.status, ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "REST", "NONE"] as const, "status"), estimatedMinutes: integer(data.estimatedMinutes, "estimatedMinutes"), actualMinutes: integer(data.actualMinutes, "actualMinutes"), progressRate: numberOrNull(data.progressRate, "progressRate"), completedSessionCount: integer(data.completedSessionCount, "completedSessionCount"), totalSessionCount: integer(data.totalSessionCount, "totalSessionCount"), scheduleRevision: integer(data.scheduleRevision, "scheduleRevision"), sessions: data.sessions.map(decodeSessionSummary) };
}

export function decodeLearningDays(value: unknown): LearningDayDto[] {
  if (!Array.isArray(value)) throw new TypeError("learning days must be an array");
  return value.map((item, index) => {
    const day = decodeLearningDay(item);
    if (day === null) throw new TypeError(`learning days[${index}] must not be null`);
    return day;
  });
}

export function decodeSession(value: unknown): SessionDto { const data = object(value, "session"); return { ...decodeSessionSummary(data), learningDayId: readId(data.learningDayId), curriculumId: readId(data.curriculumId), scheduledDate: text(data.scheduledDate, "scheduledDate"), description: text(data.description, "description"), actualMinutes: integer(data.actualMinutes, "actualMinutes"), timeSource: nullableText(data.timeSource, "timeSource"), startedAt: nullableText(data.startedAt, "startedAt"), completedAt: nullableText(data.completedAt, "completedAt"), requiredContentCount: integer(data.requiredContentCount, "requiredContentCount"), completedRequiredContentCount: integer(data.completedRequiredContentCount, "completedRequiredContentCount"), revision: integer(data.revision, "revision") }; }

export function decodeContentSummary(value: unknown): ContentSummaryDto { const data = object(value, "content"); return { contentId: readId(data.contentId), contentVersionId: readId(data.contentVersionId), title: text(data.title, "title"), type: text(data.type, "type"), sequence: integer(data.sequence, "sequence"), required: boolean(data.required, "required"), status: oneOf(data.status, ["NOT_STARTED", "COMPLETED"] as const, "status"), completionRule: oneOf(data.completionRule, ["READ_ACK", "ALL_PROBLEMS_ATTEMPTED"] as const, "completionRule") }; }
export function decodeContentList(value: unknown): ContentSummaryDto[] { if (!Array.isArray(value)) throw new TypeError("contents must be an array"); return value.map(decodeContentSummary); }
export function decodeLearningContent(value: unknown): LearningContentDto { const data = object(value, "learning content"); if (!Array.isArray(data.problems)) throw new TypeError("problems must be an array"); return { ...decodeContentSummary(data), sessionId: readId(data.sessionId), learningObjective: text(data.learningObjective, "learningObjective"), body: text(data.body, "body"), keyPoints: strings(data.keyPoints, "keyPoints"), interviewPoints: strings(data.interviewPoints, "interviewPoints"), problems: data.problems.map((item) => { const problem = object(item, "problem"); return { problemId: readId(problem.problemId), title: text(problem.title, "problem.title"), type: text(problem.type, "problem.type") }; }), completedAt: nullableText(data.completedAt, "completedAt") }; }

export function decodeContentComplete(value: unknown): { contentId: Id; status: "COMPLETED" } { const data = object(value, "content completion"); return { contentId: readId(data.contentId), status: oneOf(data.status, ["COMPLETED"] as const, "status") }; }
export function decodeSessionComplete(value: unknown): { session: SessionDto } { const data = object(value, "session completion"); return { session: decodeSession(data.session) }; }
