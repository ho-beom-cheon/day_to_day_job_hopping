import { decodeEnvelope } from "@/api/contract";
import { readId, type Id } from "@/types/id";
import { displayCurriculumTitle } from "@/features/curriculum/display-title";
import { decodeLearningDay, type LearningDayDto } from "@/features/today-learning/contract";

type Json = Record<string, unknown>;
const object = (value: unknown, label: string): Json => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`);
  return value as Json;
};
const text = (value: unknown, label: string) => { if (typeof value !== "string") throw new TypeError(`${label} must be a string`); return value; };
const integer = (value: unknown, label: string) => { if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`); return value as number; };
const numberOrNull = (value: unknown, label: string) => value === null ? null : (typeof value === "number" && Number.isFinite(value) ? value : (() => { throw new TypeError(`${label} must be a number or null`); })());
const date = (value: unknown, label: string) => { const result = text(value, label); if (!/^\d{4}-\d{2}-\d{2}$/.test(result)) throw new TypeError(`${label} must be a date`); return result; };
const instant = (value: unknown, label: string) => { const result = text(value, label); if (Number.isNaN(Date.parse(result))) throw new TypeError(`${label} must be a date-time`); return result; };

export type StudyPolicyDto = { dailyStudyMinutes: number; restDaysPerWeek: number; restWeekdays: string[] };
export type CurriculumDto = {
  curriculumId: Id; templateId: Id; durationMonths?: number; title: string; startDate: string; nominalEndDate: string; endDate: string;
  totalWeeks: number; totalLearningDays: number; completedRequiredSessions: number;
  totalRequiredSessions: number; completionRate: number | null; status: string;
  scheduleRevision: number; studyPolicy: StudyPolicyDto;
};
export type RoadmapMonthDto = { monthNo: number; title: string; startDate: string; endDate: string; requiredSessionCount: number; completedSessionCount: number; progressRate: number | null; weekNumbers: number[] };
export type RoadmapWeekDto = { weekNo: number; monthNo: number; title: string; startDate: string; endDate: string; requiredSessionCount: number; completedSessionCount: number; progressRate: number | null };
export type RoadmapWeekDetailDto = RoadmapWeekDto & { learningGoals: string[]; learningDays: LearningDayDto[] };
export type ScheduleAction = "REST_ADD" | "REST_REMOVE" | "POLICY_APPLY" | "SHIFT_BACKLOG";
export type RestReason = "PERSONAL" | "VACATION" | "HEALTH" | "WORK" | "ETC";
export type SchedulePreviewDto = {
  previewId: Id; curriculumId: Id; scheduleRevision: number; action: ScheduleAction; baseDate: string;
  targetDate: string | null; reason: RestReason | null; affectedLearningDayCount: number;
  previousEndDate: string; newEndDate: string; expiresAt: string;
  moves: { learningDayId: Id; previousDate: string; newDate: string }[]; warnings: string[];
};
export type ScheduleResultDto = { curriculumId: Id; previewId: Id; scheduleRevision: number; affectedLearningDayCount: number; previousEndDate: string; newEndDate: string; appliedAt: string };

function policy(value: unknown): StudyPolicyDto {
  const item = object(value, "studyPolicy");
  if (!Array.isArray(item.restWeekdays)) throw new TypeError("restWeekdays must be an array");
  return { dailyStudyMinutes: integer(item.dailyStudyMinutes, "dailyStudyMinutes"), restDaysPerWeek: integer(item.restDaysPerWeek, "restDaysPerWeek"), restWeekdays: item.restWeekdays.map((v, i) => text(v, `restWeekdays[${i}]`)) };
}
export function decodeCurriculumEnvelope(value: unknown) {
  return decodeEnvelope(value, data => {
    if (data === null) return null;
    const item = object(data, "curriculum");
    return { curriculumId: readId(item.curriculumId), templateId: readId(item.templateId), durationMonths: item.durationMonths === undefined ? 6 : integer(item.durationMonths, "durationMonths"), title: displayCurriculumTitle(text(item.title, "title")), startDate: date(item.startDate, "startDate"), nominalEndDate: date(item.nominalEndDate, "nominalEndDate"), endDate: date(item.endDate, "endDate"), totalWeeks: integer(item.totalWeeks, "totalWeeks"), totalLearningDays: integer(item.totalLearningDays, "totalLearningDays"), completedRequiredSessions: integer(item.completedRequiredSessions, "completedRequiredSessions"), totalRequiredSessions: integer(item.totalRequiredSessions, "totalRequiredSessions"), completionRate: numberOrNull(item.completionRate, "completionRate"), status: text(item.status, "status"), scheduleRevision: integer(item.scheduleRevision, "scheduleRevision"), studyPolicy: policy(item.studyPolicy) } satisfies CurriculumDto;
  }).data;
}
export const decodeMonthsEnvelope = (value: unknown) => decodeEnvelope(value, data => {
  if (!Array.isArray(data)) throw new TypeError("months must be an array");
  return data.map(value => { const item = object(value, "month"); if (!Array.isArray(item.weekNumbers)) throw new TypeError("weekNumbers must be an array"); return { monthNo: integer(item.monthNo, "monthNo"), title: text(item.title, "title"), startDate: date(item.startDate, "startDate"), endDate: date(item.endDate, "endDate"), requiredSessionCount: integer(item.requiredSessionCount, "requiredSessionCount"), completedSessionCount: integer(item.completedSessionCount, "completedSessionCount"), progressRate: numberOrNull(item.progressRate, "progressRate"), weekNumbers: item.weekNumbers.map((v, i) => integer(v, `weekNumbers[${i}]`)) } satisfies RoadmapMonthDto; });
}).data;
export const decodeWeeksEnvelope = (value: unknown) => decodeEnvelope(value, data => {
  if (!Array.isArray(data)) throw new TypeError("weeks must be an array");
  return data.map(value => { const item = object(value, "week"); return { weekNo: integer(item.weekNo, "weekNo"), monthNo: integer(item.monthNo, "monthNo"), title: text(item.title, "title"), startDate: date(item.startDate, "startDate"), endDate: date(item.endDate, "endDate"), requiredSessionCount: integer(item.requiredSessionCount, "requiredSessionCount"), completedSessionCount: integer(item.completedSessionCount, "completedSessionCount"), progressRate: numberOrNull(item.progressRate, "progressRate") } satisfies RoadmapWeekDto; });
}).data;
export const decodeWeekDetailEnvelope = (value: unknown): RoadmapWeekDetailDto => decodeEnvelope(value, data => {
  const item = object(data, "week detail");
  if (!Array.isArray(item.learningGoals) || !Array.isArray(item.learningDays)) throw new TypeError("week detail collections must be arrays");
  return {
    weekNo: integer(item.weekNo, "weekNo"), monthNo: integer(item.monthNo, "monthNo"), title: text(item.title, "title"),
    startDate: date(item.startDate, "startDate"), endDate: date(item.endDate, "endDate"),
    requiredSessionCount: integer(item.requiredSessionCount, "requiredSessionCount"), completedSessionCount: integer(item.completedSessionCount, "completedSessionCount"),
    progressRate: numberOrNull(item.progressRate, "progressRate"),
    learningGoals: item.learningGoals.map((goal, index) => text(goal, `learningGoals[${index}]`)),
    learningDays: item.learningDays.map((day, index) => {
      const decoded = decodeLearningDay(day);
      if (!decoded || decoded.kind !== "STUDY") throw new TypeError(`learningDays[${index}] must be a study day`);
      return decoded;
    }),
  } satisfies RoadmapWeekDetailDto;
}).data;
export const decodePreviewEnvelope = (value: unknown) => decodeEnvelope(value, data => {
  const item = object(data, "preview"); if (!Array.isArray(item.moves) || !Array.isArray(item.warnings)) throw new TypeError("preview collections must be arrays");
  const action = text(item.action, "action") as ScheduleAction;
  if (!["REST_ADD", "REST_REMOVE", "POLICY_APPLY", "SHIFT_BACKLOG"].includes(action)) throw new TypeError("unsupported schedule action");
  return { previewId: readId(item.previewId), curriculumId: readId(item.curriculumId), scheduleRevision: integer(item.scheduleRevision, "scheduleRevision"), action, baseDate: date(item.baseDate, "baseDate"), targetDate: item.targetDate === null ? null : date(item.targetDate, "targetDate"), reason: item.reason === null ? null : text(item.reason, "reason") as RestReason, affectedLearningDayCount: integer(item.affectedLearningDayCount, "affectedLearningDayCount"), previousEndDate: date(item.previousEndDate, "previousEndDate"), newEndDate: date(item.newEndDate, "newEndDate"), expiresAt: instant(item.expiresAt, "expiresAt"), moves: item.moves.map((move, i) => { const row = object(move, `moves[${i}]`); return { learningDayId: readId(row.learningDayId), previousDate: date(row.previousDate, "previousDate"), newDate: date(row.newDate, "newDate") }; }), warnings: item.warnings.map((v, i) => text(v, `warnings[${i}]`)) } satisfies SchedulePreviewDto;
}).data;
export const decodeResultEnvelope = (value: unknown) => decodeEnvelope(value, data => { const item = object(data, "result"); return { curriculumId: readId(item.curriculumId), previewId: readId(item.previewId), scheduleRevision: integer(item.scheduleRevision, "scheduleRevision"), affectedLearningDayCount: integer(item.affectedLearningDayCount, "affectedLearningDayCount"), previousEndDate: date(item.previousEndDate, "previousEndDate"), newEndDate: date(item.newEndDate, "newEndDate"), appliedAt: instant(item.appliedAt, "appliedAt") } satisfies ScheduleResultDto; }).data;
