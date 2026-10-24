import { describe, expect, it } from "vitest";
import { decodeCurriculumEnvelope, decodeMonthsEnvelope, decodePreviewEnvelope, decodeWeekDetailEnvelope } from "./contract";

const envelope = (data: unknown) => ({ success: true, data, error: null, meta: { traceId: "0123456789abcdef0123456789abcdef", serverTime: "2026-09-21T09:00:00+09:00" } });

describe("roadmap contract", () => {
  it("keeps bigint identifiers as strings and nullable rates distinct", () => {
    const course = decodeCurriculumEnvelope(envelope({ curriculumId: "9007199254740993", templateId: "9007199254740995", title: "6개월 과정", startDate: "2026-09-21", nominalEndDate: "2027-03-21", endDate: "2027-03-23", totalWeeks: 24, totalLearningDays: 120, completedRequiredSessions: 0, totalRequiredSessions: 240, completionRate: null, status: "ACTIVE", scheduleRevision: 3, studyPolicy: { dailyStudyMinutes: 120, restDaysPerWeek: 2, restWeekdays: ["SATURDAY", "SUNDAY"] } }));
    expect(course?.curriculumId).toBe("9007199254740993");
    expect(course?.completionRate).toBeNull();
    expect(decodeMonthsEnvelope(envelope([{ monthNo: 1, title: "기반", startDate: "2026-09-21", endDate: "2026-10-20", requiredSessionCount: 20, completedSessionCount: 0, progressRate: null, weekNumbers: [1, 2, 3, 4] }]))[0].weekNumbers).toEqual([1, 2, 3, 4]);
  });

  it("decodes a schedule preview without converting ids", () => {
    const preview = decodePreviewEnvelope(envelope({ previewId: "401", curriculumId: "3", scheduleRevision: 1, strategy: "SHIFT", action: "REST_ADD", baseDate: "2026-09-22", targetDate: "2026-09-22", reason: "PERSONAL", studyPolicy: null, affectedLearningDayCount: 1, previousEndDate: "2027-03-21", newEndDate: "2027-03-22", moves: [{ learningDayId: "42", previousDate: "2026-09-22", newDate: "2026-09-23" }], expiresAt: "2026-09-21T10:10:00+09:00", warnings: [] }));
    expect(preview.previewId).toBe("401");
    expect(preview.moves[0].learningDayId).toBe("42");
  });

  it("keeps the assigned day and session when decoding a week detail", () => {
    const week = decodeWeekDetailEnvelope(envelope({
      weekNo: 1, monthNo: 1, title: "Java 기본", startDate: "2026-09-21", endDate: "2026-09-21",
      requiredSessionCount: 1, completedSessionCount: 0, progressRate: 0, learningGoals: ["값 비교 설명"],
      learningDays: [{ learningDayId: "9007199254740993", curriculumId: "3", date: "2026-09-21", dayNo: 1,
        weekNo: 1, monthNo: 1, kind: "STUDY", title: "1일차 · 값 비교", description: "값 비교를 설명한다.",
        status: "NOT_STARTED", estimatedMinutes: 60, actualMinutes: 0, progressRate: 0,
        completedSessionCount: 0, totalSessionCount: 1, scheduleRevision: 2,
        sessions: [{ sessionId: "9007199254740995", category: "JAVA", title: "값 비교 학습", sequence: 1,
          required: true, estimatedMinutes: 60, status: "NOT_STARTED" }] }],
    }));
    expect(week.learningDays[0].learningDayId).toBe("9007199254740993");
    expect(week.learningDays[0].sessions[0].sessionId).toBe("9007199254740995");
    expect(week.learningDays[0].date).toBe("2026-09-21");
  });

  it("accepts a missing current curriculum", () => expect(decodeCurriculumEnvelope(envelope(null))).toBeNull());
});
