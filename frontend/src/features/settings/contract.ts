import { decodeEnvelope } from "@/api/contract";
import { weekdayOptions, type Weekday } from "@/lib/weekdays";

export type StudyPreferences = {
  studyPolicy: { dailyStudyMinutes: number; restDaysPerWeek: number; restWeekdays: Weekday[] };
  weekdayStudyStartTime: string;
  weekendStudyStartTime: string;
};
export type UserSettings = StudyPreferences & {
  theme: "LIGHT" | "DARK" | "SYSTEM";
  language: "KO";
  timeZone: "Asia/Seoul";
  revision: number;
  updatedAt: string;
};

const object = (value: unknown): Record<string, unknown> => {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError("Expected settings object");
  return value as Record<string, unknown>;
};
const integer = (value: unknown, min: number, max: number): number => {
  if (typeof value !== "number" || !Number.isInteger(value) || value < min || value > max) throw new TypeError("Invalid settings integer");
  return value;
};
export const isStudyTime = (value: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(value);
const time = (value: unknown): string => {
  if (typeof value !== "string" || !isStudyTime(value)) throw new TypeError("Invalid study time");
  return value;
};

export function decodeUserSettings(value: unknown): UserSettings {
  const item = object(value), policy = object(item.studyPolicy);
  const restDaysPerWeek = integer(policy.restDaysPerWeek, 0, 6);
  const days = policy.restWeekdays;
  if (!Array.isArray(days) || days.length !== restDaysPerWeek || new Set(days).size !== days.length ||
    !days.every(day => weekdayOptions.some(option => option.value === day))) throw new TypeError("Invalid rest weekdays");
  if (item.theme !== "LIGHT" && item.theme !== "DARK" && item.theme !== "SYSTEM") throw new TypeError("Invalid theme");
  if (item.language !== "KO" || item.timeZone !== "Asia/Seoul") throw new TypeError("Unsupported settings locale");
  if (typeof item.updatedAt !== "string" || !Number.isFinite(Date.parse(item.updatedAt))) throw new TypeError("Invalid settings timestamp");
  return {
    studyPolicy: { dailyStudyMinutes: integer(policy.dailyStudyMinutes, 15, 480), restDaysPerWeek, restWeekdays: days as Weekday[] },
    weekdayStudyStartTime: time(item.weekdayStudyStartTime), weekendStudyStartTime: time(item.weekendStudyStartTime),
    theme: item.theme, language: item.language, timeZone: item.timeZone,
    revision: integer(item.revision, 0, 2147483647), updatedAt: item.updatedAt,
  };
}

export const decodeUserSettingsEnvelope = (value: unknown) => decodeEnvelope(value, decodeUserSettings).data;
