import { describe, expect, it } from "vitest";
import { decodeLearningDay } from "./contract";

describe("today learning contract", () => {
  it("decodes string ids without numeric coercion", () => {
    const value = decodeLearningDay({ learningDayId: "9007199254740993", curriculumId: "9007199254740995", date: "2026-09-21", dayNo: 1, weekNo: 1, monthNo: 1, kind: "STUDY", title: "오늘", description: "", status: "NOT_STARTED", estimatedMinutes: 30, actualMinutes: 0, progressRate: null, completedSessionCount: 0, totalSessionCount: 1, scheduleRevision: 1, sessions: [{ sessionId: "9007199254740997", category: "JAVA", title: "문법", sequence: 1, required: true, estimatedMinutes: 30, status: "NOT_STARTED" }] });
    expect(value?.learningDayId).toBe("9007199254740993"); expect(value?.sessions[0].sessionId).toBe("9007199254740997"); expect(value?.progressRate).toBeNull();
  });
  it("rejects numeric ids at the transport boundary", () => { expect(() => decodeLearningDay({ learningDayId: 1, curriculumId: "2", date: "2026-09-21", dayNo: 1, weekNo: 1, monthNo: 1, kind: "STUDY", title: "오늘", description: "", status: "NOT_STARTED", estimatedMinutes: 30, actualMinutes: 0, progressRate: 0, completedSessionCount: 0, totalSessionCount: 0, sessions: [] })).toThrow("ID must be a JSON string"); });
});
