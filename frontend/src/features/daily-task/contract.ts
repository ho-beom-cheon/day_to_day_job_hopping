import { decodeEnvelope } from "@/api/contract";
import { readId, type Id } from "@/types/id";

type RecordValue = Record<string, unknown>;

const object = (value: unknown, label: string): RecordValue => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as RecordValue;
};
const string = (value: unknown, label: string) => {
  if (typeof value !== "string") throw new TypeError(`${label} must be a string`);
  return value;
};
const nullableString = (value: unknown, label: string) => value === null || value === undefined ? null : string(value, label);
const integer = (value: unknown, label: string) => {
  if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`);
  return value as number;
};
const nullableInteger = (value: unknown, label: string) => value === null || value === undefined ? null : integer(value, label);
const decimal = (value: unknown, label: string) => {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new TypeError(`${label} must be a number`);
  return value;
};
const strings = (value: unknown, label: string) => {
  if (!Array.isArray(value)) throw new TypeError(`${label} must be an array`);
  return value.map((item, index) => string(item, `${label}[${index}]`));
};
const nullableStrings = (value: unknown, label: string) => value === null || value === undefined ? null : strings(value, label);
const oneOf = <T extends string>(value: unknown, allowed: readonly T[], label: string): T => {
  if (!allowed.includes(value as T)) throw new TypeError(`${label} is not supported`);
  return value as T;
};

export const TASK_STATUSES = ["PREPARING", "READY", "IN_PROGRESS", "GRADING", "FEEDBACK", "COMPLETED", "SWAPPED", "FAILED"] as const;
export type TaskStatus = typeof TASK_STATUSES[number];
export type TaskFormat = "CASE" | "REVIEW" | "INTERVIEW";

export type CompetencyRef = { code: string; title: string; areaTitle: string; priority: "P1" | "P2" | "P3" };
export type TaskSummary = { taskId: Id; status: TaskStatus; format: TaskFormat; competency: CompetencyRef; planReason: string; title: string | null; estimatedMinutes: number | null; failureCode: string | null };
export type CardSummary = { taskId: Id; competencyCode: string; title: string; interviewLine: string; updatedAt: string };
export type Today = { task: TaskSummary | null; week: { target: number; completed: number }; ai: { enabled: boolean; limitKrw: number; remainingKrw: number }; recentCards: CardSummary[] };

export type Turn = { turnNo: number; kind: "MAIN" | "FOLLOWUP"; question: string; answer: string | null; submitted: boolean; pending: boolean; feedback: string | null; checkPoints: string[] | null; checkPointsMet: string[] | null; scoreDelta: number | null };
export type Criterion = { criterionId: string; description: string; points: number; earnedPoints: number | null; reason: string | null };
export type Result = { status: "GRADED" | "HOLD" | "SELF_GRADED"; baseScore: number | null; finalScore: number | null; feedback: string; missingPoints: string[]; improvedAnswer: string; holdReason: string; scoreExcluded: boolean };
export type CardDraft = { title: string; summary: string; interviewLine: string; reviewPrompt: string };
export type Card = { title: string; body: string; interviewLine: string; reviewPrompt: string };
export type DailyTask = {
  taskId: Id; status: TaskStatus; format: TaskFormat; timeMode: "SHORT" | "STANDARD" | "DEEP"; gradingMode: "AI" | "SELF";
  followupLimit: number; competency: CompetencyRef; planReason: string; failureCode: string | null;
  task: { title: string; situation: string; material: string; question: string; estimatedMinutes: number; modelAnswerPoints: string[] | null } | null;
  sheet: { version: number; reviewLabel: string; markdown: string } | null;
  turns: Turn[]; rubric: Criterion[] | null; result: Result | null; cardDraft: CardDraft | null; card: Card | null;
  usefulness: number | null; pending: boolean; revision: number;
};

function competency(value: unknown): CompetencyRef {
  const data = object(value, "competency");
  return { code: string(data.code, "code"), title: string(data.title, "title"), areaTitle: string(data.areaTitle, "areaTitle"), priority: oneOf(data.priority, ["P1", "P2", "P3"] as const, "priority") };
}

function summary(value: unknown): TaskSummary {
  const data = object(value, "task summary");
  return {
    taskId: readId(data.taskId), status: oneOf(data.status, TASK_STATUSES, "status"), format: oneOf(data.format, ["CASE", "REVIEW", "INTERVIEW"] as const, "format"),
    competency: competency(data.competency), planReason: string(data.planReason, "planReason"), title: nullableString(data.title, "title"),
    estimatedMinutes: nullableInteger(data.estimatedMinutes, "estimatedMinutes"), failureCode: nullableString(data.failureCode, "failureCode"),
  };
}

export function decodeToday(value: unknown): Today {
  const data = object(value, "today");
  const week = object(data.week, "week"), ai = object(data.ai, "ai");
  if (!Array.isArray(data.recentCards)) throw new TypeError("recentCards must be an array");
  return {
    task: data.task === null ? null : summary(data.task),
    week: { target: integer(week.target, "target"), completed: integer(week.completed, "completed") },
    ai: { enabled: ai.enabled === true, limitKrw: decimal(ai.limitKrw, "limitKrw"), remainingKrw: decimal(ai.remainingKrw, "remainingKrw") },
    recentCards: data.recentCards.map(item => { const card = object(item, "card"); return { taskId: readId(card.taskId), competencyCode: string(card.competencyCode, "competencyCode"), title: string(card.title, "title"), interviewLine: string(card.interviewLine, "interviewLine"), updatedAt: string(card.updatedAt, "updatedAt") }; }),
  };
}

function turn(value: unknown): Turn {
  const data = object(value, "turn");
  return {
    turnNo: integer(data.turnNo, "turnNo"), kind: oneOf(data.kind, ["MAIN", "FOLLOWUP"] as const, "kind"), question: string(data.question, "question"),
    answer: nullableString(data.answer, "answer"), submitted: data.submitted === true, pending: data.pending === true, feedback: nullableString(data.feedback, "feedback"),
    checkPoints: nullableStrings(data.checkPoints, "checkPoints"), checkPointsMet: nullableStrings(data.checkPointsMet, "checkPointsMet"), scoreDelta: nullableInteger(data.scoreDelta, "scoreDelta"),
  };
}

function draft(value: unknown): CardDraft | null {
  if (value === null || value === undefined) return null;
  const data = object(value, "card draft");
  return { title: string(data.title, "title"), summary: string(data.summary, "summary"), interviewLine: string(data.interviewLine, "interviewLine"), reviewPrompt: string(data.reviewPrompt, "reviewPrompt") };
}

export function decodeTask(value: unknown): DailyTask {
  const data = object(value, "task");
  if (!Array.isArray(data.turns)) throw new TypeError("turns must be an array");
  const material = data.task === null ? null : object(data.task, "material");
  const sheet = data.sheet === null ? null : object(data.sheet, "sheet");
  const result = data.result === null ? null : object(data.result, "result");
  const card = data.card === null ? null : object(data.card, "card");
  return {
    taskId: readId(data.taskId), status: oneOf(data.status, TASK_STATUSES, "status"), format: oneOf(data.format, ["CASE", "REVIEW", "INTERVIEW"] as const, "format"),
    timeMode: oneOf(data.timeMode, ["SHORT", "STANDARD", "DEEP"] as const, "timeMode"), gradingMode: oneOf(data.gradingMode, ["AI", "SELF"] as const, "gradingMode"),
    followupLimit: integer(data.followupLimit, "followupLimit"), competency: competency(data.competency), planReason: string(data.planReason, "planReason"),
    failureCode: nullableString(data.failureCode, "failureCode"),
    task: material && { title: string(material.title, "title"), situation: string(material.situation, "situation"), material: string(material.material, "material"), question: string(material.question, "question"), estimatedMinutes: integer(material.estimatedMinutes, "estimatedMinutes"), modelAnswerPoints: nullableStrings(material.modelAnswerPoints, "modelAnswerPoints") },
    sheet: sheet && { version: integer(sheet.version, "version"), reviewLabel: string(sheet.reviewLabel, "reviewLabel"), markdown: string(sheet.markdown, "markdown") },
    turns: data.turns.map(turn),
    rubric: data.rubric === null ? null : (Array.isArray(data.rubric) ? data.rubric : []).map(item => { const c = object(item, "criterion"); return { criterionId: string(c.criterionId, "criterionId"), description: string(c.description, "description"), points: integer(c.points, "points"), earnedPoints: nullableInteger(c.earnedPoints, "earnedPoints"), reason: nullableString(c.reason, "reason") }; }),
    result: result && { status: oneOf(result.status, ["GRADED", "HOLD", "SELF_GRADED"] as const, "result status"), baseScore: nullableInteger(result.baseScore, "baseScore"), finalScore: nullableInteger(result.finalScore, "finalScore"), feedback: string(result.feedback ?? "", "feedback"), missingPoints: strings(result.missingPoints ?? [], "missingPoints"), improvedAnswer: string(result.improvedAnswer ?? "", "improvedAnswer"), holdReason: string(result.holdReason ?? "", "holdReason"), scoreExcluded: result.scoreExcluded === true },
    cardDraft: draft(data.cardDraft),
    card: card && { title: string(card.title, "title"), body: string(card.body, "body"), interviewLine: string(card.interviewLine, "interviewLine"), reviewPrompt: string(card.reviewPrompt, "reviewPrompt") },
    usefulness: nullableInteger(data.usefulness, "usefulness"), pending: data.pending === true, revision: integer(data.revision, "revision"),
  };
}

export const decodeTodayEnvelope = (value: unknown) => decodeEnvelope(value, decodeToday).data;
export const decodeTaskEnvelope = (value: unknown) => decodeEnvelope(value, decodeTask).data;

const FAILURE_MESSAGES: Record<string, string> = {
  AI_BUDGET_EXCEEDED: "이번 달 AI 예산 상한에 닿아 AI를 쓰지 못했어요. 자가 채점으로 진행해요.",
  AI_UNAVAILABLE: "AI 채점을 쓸 수 없어 자가 채점으로 바꿨어요.",
  AI_DISABLED: "서버의 AI 설정이 꺼져 있어요.",
  AI_PRICE_UNAVAILABLE: "AI 비용을 계산할 수 없어 AI 기능을 멈췄어요.",
  AI_REQUEST_LIMIT_EXCEEDED: "오늘 AI 호출 안전 한도에 닿았어요.",
  AI_CONCURRENT_JOB_LIMIT: "처리 중인 AI 작업이 많아요. 잠시 후 다시 시도해 주세요.",
  AI_QUALITY_REJECTED: "AI 결과가 검증을 통과하지 못했어요.",
  AI_OUTPUT_INCOMPLETE: "AI 응답이 중간에 끊겼어요.",
  AI_PROVIDER_ERROR: "AI 공급자 오류가 났어요.",
  AI_PROVIDER_RESULT_UNKNOWN: "AI 응답 결과를 확인하지 못했어요.",
  AI_PROVIDER_QUOTA_EXHAUSTED: "OpenAI 계정 한도에 닿았어요.",
  BANK_EXHAUSTED: "준비된 과제를 모두 사용했어요.",
};
export const failureMessage = (code: string | null) => code ? FAILURE_MESSAGES[code] ?? `처리하지 못했어요 (${code}).` : null;
export const formatLabel: Record<TaskFormat, string> = { CASE: "장애·사례 분석", REVIEW: "코드 리뷰·설계 판단", INTERVIEW: "면접 답변" };
