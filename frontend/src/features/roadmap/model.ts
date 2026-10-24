import type { CurriculumDto, RoadmapMonthDto, RoadmapWeekDto, SchedulePreviewDto } from "./contract";

const koDate = new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", timeZone: "Asia/Seoul" });
export const formatDate = (value: string) => koDate.format(new Date(`${value}T00:00:00+09:00`));
export const percent = (value: number | null) => value === null ? "산정 전" : `${value.toFixed(1)}%`;
export const etagFor = (revision: number) => `"${revision}"`;
export const seoulToday = (now = new Date()) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
export function confirmRequest(preview: SchedulePreviewDto) {
  if (preview.action === "REST_ADD") return { path: "/api/v1/rest-days" as const, method: "POST" as const, body: { date: preview.targetDate, reason: preview.reason, previewId: preview.previewId } };
  if (preview.action === "REST_REMOVE") return { path: `/api/v1/rest-days/${preview.targetDate}?previewId=${preview.previewId}` as `/api/v1/${string}`, method: "DELETE" as const };
  return { path: `/api/v1/curriculums/${preview.curriculumId}/reschedule` as `/api/v1/${string}`, method: "POST" as const, body: { previewId: preview.previewId } };
}

export function toRoadmapViewModel(curriculum: CurriculumDto, months: RoadmapMonthDto[], weeks: RoadmapWeekDto[]) {
  return {
    id: curriculum.curriculumId, title: curriculum.title, period: `${formatDate(curriculum.startDate)} – ${formatDate(curriculum.endDate)}`,
    completion: percent(curriculum.completionRate), revision: curriculum.scheduleRevision,
    policy: `주 ${7 - curriculum.studyPolicy.restDaysPerWeek}일 학습 · 하루 ${curriculum.studyPolicy.dailyStudyMinutes}분`,
    months: months.map(month => ({ ...month, period: `${formatDate(month.startDate)} – ${formatDate(month.endDate)}`, progressLabel: percent(month.progressRate), weeks: weeks.filter(week => week.monthNo === month.monthNo).map(week => ({ ...week, period: `${formatDate(week.startDate)} – ${formatDate(week.endDate)}`, progressLabel: percent(week.progressRate) })) })),
  };
}
const pendingKey = (curriculumId: string) => `daily-career:schedule-preview:${curriculumId}`;
export function savePendingPreview(preview: SchedulePreviewDto) { if (typeof sessionStorage !== "undefined") sessionStorage.setItem(pendingKey(preview.curriculumId), JSON.stringify(preview)); }
export function clearPendingPreview(curriculumId: string) { if (typeof sessionStorage !== "undefined") sessionStorage.removeItem(pendingKey(curriculumId)); }
export function restorePendingPreview(curriculumId: string, revision: number, now = Date.now()): SchedulePreviewDto | null {
  try {
    if (typeof sessionStorage === "undefined") return null;
    const raw = sessionStorage.getItem(pendingKey(curriculumId)); if (!raw) return null;
    const value = JSON.parse(raw) as SchedulePreviewDto;
    if (value.curriculumId !== curriculumId || value.scheduleRevision !== revision || Date.parse(value.expiresAt) <= now) { clearPendingPreview(curriculumId); return null; }
    return value;
  } catch { clearPendingPreview(curriculumId); return null; }
}
