import type { CurriculumTemplateDto, Weekday } from "./contract";

export const weekdayOptions: ReadonlyArray<{ value: Weekday; label: string }> = [
  {value:"MONDAY",label:"월"},{value:"TUESDAY",label:"화"},{value:"WEDNESDAY",label:"수"},{value:"THURSDAY",label:"목"},{value:"FRIDAY",label:"금"},{value:"SATURDAY",label:"토"},{value:"SUNDAY",label:"일"},
];
export type CurriculumTemplateViewModel = CurriculumTemplateDto & { durationLabel: string; volumeLabel: string };
export function toTemplateViewModel(value: CurriculumTemplateDto): CurriculumTemplateViewModel { return {...value,durationLabel:value.durationMonths === 6 && value.totalWeeks < 20 ? `현재 제공 ${value.totalLearningDays}학습일` : `${value.durationMonths}개월 · ${value.totalWeeks}주`,volumeLabel:value.durationMonths === 6 && value.totalWeeks < 20 ? `필수 세션 ${value.totalRequiredSessions}개` : `학습일 ${value.totalLearningDays}일 · 필수 세션 ${value.totalRequiredSessions}개`}; }
export function seoulToday(now = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone:"Asia/Seoul", year:"numeric", month:"2-digit", day:"2-digit" }).format(now);
}
export function maxStartDate(start: string): string { const [year,month,day]=start.split("-").map(Number); const date=new Date(Date.UTC(year,month-1,day+90)); return date.toISOString().slice(0,10); }
export function toggleWeekday(current: Weekday[], value: Weekday): Weekday[] { return current.includes(value) ? current.filter(day=>day!==value) : current.length < 6 ? [...current,value] : current; }
