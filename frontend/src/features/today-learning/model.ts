import type { CurriculumDto, LearningDayDto, SessionStatus } from "./contract";

export function formatKoreanDate(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short", timeZone: "Asia/Seoul" }).format(new Date(Date.UTC(year, month - 1, day, 12)));
}

export function progressLabel(value: number | null): string { return value === null ? "평가 전" : `${value.toFixed(value % 1 === 0 ? 0 : 1)}%`; }
export function statusLabel(status: SessionStatus): string { return { NOT_STARTED: "대기", IN_PROGRESS: "진행 중", COMPLETED: "완료" }[status]; }

export function nextAvailableSession(today: LearningDayDto | null) {
  if (!today || today.kind !== "STUDY") return null;
  return today.sessions.find(session => session.status === "IN_PROGRESS") ?? today.sessions.find(session => session.status === "NOT_STARTED") ?? null;
}

export type DashboardViewModel = {
  curriculum: CurriculumDto | null;
  today: LearningDayDto | null;
  nextSession: ReturnType<typeof nextAvailableSession>;
  isRestDay: boolean;
};

export function toDashboardViewModel(curriculum: CurriculumDto | null, today: LearningDayDto | null): DashboardViewModel {
  return { curriculum, today, nextSession: nextAvailableSession(today), isRestDay: today?.kind === "REST" };
}
