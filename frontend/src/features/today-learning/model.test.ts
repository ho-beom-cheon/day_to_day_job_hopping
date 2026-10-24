import { describe, expect, it } from "vitest";
import { nextAvailableSession, progressLabel, toDashboardViewModel } from "./model";
import type { LearningDayDto } from "./contract";

const day: LearningDayDto = { learningDayId: "101", curriculumId: "1", date: "2026-09-21", dayNo: 1, weekNo: 1, monthNo: 1, kind: "STUDY", title: "오늘 학습", description: "설명", status: "IN_PROGRESS", estimatedMinutes: 60, actualMinutes: 20, progressRate: 50, completedSessionCount: 1, totalSessionCount: 2, scheduleRevision: 3, sessions: [
  { sessionId: "9007199254740993", category: "JAVA", title: "대기", sequence: 2, required: true, estimatedMinutes: 30, status: "NOT_STARTED" },
  { sessionId: "9007199254740995", category: "SPRING", title: "진행", sequence: 1, required: true, estimatedMinutes: 30, status: "IN_PROGRESS" },
] };

describe("today learning model", () => {
  it("keeps identifiers as strings and prioritizes an in-progress session", () => { expect(nextAvailableSession(day)?.sessionId).toBe("9007199254740995"); });
  it("does not offer a completed session as the next session", () => { expect(nextAvailableSession({ ...day, status: "COMPLETED", sessions: day.sessions.map(session => ({ ...session, status: "COMPLETED" as const })) })).toBeNull(); });
  it("renders missing denominators as evaluation-before instead of zero", () => { expect(progressLabel(null)).toBe("평가 전"); expect(progressLabel(0)).toBe("0%"); });
  it("recognizes a rest day without inventing a session", () => { const view = toDashboardViewModel(null, { ...day, learningDayId: null, dayNo: null, weekNo: null, monthNo: null, kind: "REST", sessions: [] }); expect(view.isRestDay).toBe(true); expect(view.nextSession).toBeNull(); });
});
