import { decodeEnvelope } from "@/api/contract";
import { readId, type Id } from "@/types/id";
import { displayCurriculumTitle } from "@/features/curriculum/display-title";

type RecordValue = Record<string, unknown>;
const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
export type Weekday = typeof weekdays[number];

export type CurriculumTemplateDto = {
  templateId: Id; templateVersion: string; title: string; description: string;
  durationMonths: number; totalWeeks: number; totalLearningDays: number;
  totalRequiredSessions: number; publishedAt: string;
};
export type CurriculumDto = {
  curriculumId: Id; templateId: Id; templateVersion: string; title: string;
  startDate: string; nominalEndDate: string; endDate: string;
  totalWeeks: number; totalLearningDays: number; totalRequiredSessions: number;
  completedRequiredSessions: number; currentWeek: number | null; currentDayNo: number | null;
  status: "PLANNED" | "IN_PROGRESS" | "PAUSED" | "COMPLETED" | "CANCELED";
  completionRate: number; scheduleRevision: number; createdAt: string;
  studyPolicy: { dailyStudyMinutes: number; restDaysPerWeek: number; restWeekdays: Weekday[] };
};
export type CreateCurriculumRequest = {
  templateId: Id; startDate: string;
  studyPolicy: { dailyStudyMinutes: number; restDaysPerWeek: number; restWeekdays: Weekday[] };
};

function record(value: unknown, label: string): RecordValue { if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${label} must be an object`); return value as RecordValue; }
function string(value: unknown, label: string): string { if (typeof value !== "string") throw new TypeError(`${label} must be a string`); return value; }
function integer(value: unknown, label: string): number { if (!Number.isInteger(value)) throw new TypeError(`${label} must be an integer`); return value as number; }
function number(value: unknown, label: string): number { if (typeof value !== "number" || !Number.isFinite(value)) throw new TypeError(`${label} must be a number`); return value; }
function nullableInteger(value: unknown, label: string): number | null { return value === null ? null : integer(value, label); }
function weekday(value: unknown): Weekday { if (typeof value !== "string" || !(weekdays as readonly string[]).includes(value)) throw new TypeError("weekday is not supported"); return value as Weekday; }

export function decodeCurriculumTemplate(value: unknown): CurriculumTemplateDto {
  const item = record(value, "curriculum template");
  return { templateId:readId(item.templateId), templateVersion:string(item.templateVersion,"templateVersion"), title:displayCurriculumTitle(string(item.title,"title")), description:string(item.description,"description"), durationMonths:integer(item.durationMonths,"durationMonths"), totalWeeks:integer(item.totalWeeks,"totalWeeks"), totalLearningDays:integer(item.totalLearningDays,"totalLearningDays"), totalRequiredSessions:integer(item.totalRequiredSessions,"totalRequiredSessions"), publishedAt:string(item.publishedAt,"publishedAt") };
}
export function decodeCurriculumTemplates(value: unknown): CurriculumTemplateDto[] { if (!Array.isArray(value)) throw new TypeError("templates must be an array"); return value.map(decodeCurriculumTemplate); }
export function decodeCurriculum(value: unknown): CurriculumDto {
  const item=record(value,"curriculum"), policy=record(item.studyPolicy,"studyPolicy"), rawDays=policy.restWeekdays;
  if (!Array.isArray(rawDays)) throw new TypeError("restWeekdays must be an array");
  const status=string(item.status,"status");
  if (!["PLANNED","IN_PROGRESS","PAUSED","COMPLETED","CANCELED"].includes(status)) throw new TypeError("status is not supported");
  return { curriculumId:readId(item.curriculumId),templateId:readId(item.templateId),templateVersion:string(item.templateVersion,"templateVersion"),title:displayCurriculumTitle(string(item.title,"title")),startDate:string(item.startDate,"startDate"),nominalEndDate:string(item.nominalEndDate,"nominalEndDate"),endDate:string(item.endDate,"endDate"),totalWeeks:integer(item.totalWeeks,"totalWeeks"),totalLearningDays:integer(item.totalLearningDays,"totalLearningDays"),totalRequiredSessions:integer(item.totalRequiredSessions,"totalRequiredSessions"),completedRequiredSessions:integer(item.completedRequiredSessions,"completedRequiredSessions"),currentWeek:nullableInteger(item.currentWeek,"currentWeek"),currentDayNo:nullableInteger(item.currentDayNo,"currentDayNo"),status:status as CurriculumDto["status"],completionRate:number(item.completionRate,"completionRate"),scheduleRevision:integer(item.scheduleRevision,"scheduleRevision"),createdAt:string(item.createdAt,"createdAt"),studyPolicy:{dailyStudyMinutes:integer(policy.dailyStudyMinutes,"dailyStudyMinutes"),restDaysPerWeek:integer(policy.restDaysPerWeek,"restDaysPerWeek"),restWeekdays:rawDays.map(weekday)}};
}
export const decodeTemplateEnvelope = (value: unknown) => decodeEnvelope(value, decodeCurriculumTemplates).data;
export const decodeCurriculumEnvelope = (value: unknown) => decodeEnvelope(value, decodeCurriculum).data;
