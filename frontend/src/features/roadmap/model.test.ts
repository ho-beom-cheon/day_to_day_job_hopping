import { beforeEach, describe, expect, it } from "vitest";
import { clearPendingPreview, confirmRequest, etagFor, formatDate, restorePendingPreview, savePendingPreview, toRoadmapViewModel } from "./model";
import type { CurriculumDto, SchedulePreviewDto } from "./contract";

const course: CurriculumDto = { curriculumId: "3", templateId: "1", title: "6개월 과정", startDate: "2026-09-21", nominalEndDate: "2027-03-21", endDate: "2027-03-22", totalWeeks: 24, totalLearningDays: 120, totalRequiredSessions: 240, completedRequiredSessions: 0, completionRate: null, status: "ACTIVE", scheduleRevision: 3, studyPolicy: { dailyStudyMinutes: 120, restDaysPerWeek: 2, restWeekdays: ["SATURDAY", "SUNDAY"] } };
const preview: SchedulePreviewDto = { previewId: "401", curriculumId: "3", scheduleRevision: 3, action: "REST_ADD", baseDate: "2026-09-22", targetDate: "2026-09-22", reason: "PERSONAL", affectedLearningDayCount: 1, previousEndDate: "2027-03-22", newEndDate: "2027-03-23", expiresAt: "2026-09-21T10:10:00+09:00", moves: [], warnings: [] };

describe("roadmap model", () => {
  beforeEach(() => sessionStorage.clear());
  it("does not disguise an unknown rate as zero", () => expect(toRoadmapViewModel(course, [], []).completion).toBe("산정 전"));
  it("formats revisions for If-Match", () => expect(etagFor(17)).toBe('"17"'));
  it("formats a Seoul calendar date consistently across server time zones", () => expect(formatDate("2026-09-28")).toBe("9월 28일"));
  it("restores only an unexpired preview for the same revision", () => {
    savePendingPreview(preview);
    expect(restorePendingPreview("3", 3, Date.parse("2026-09-21T10:00:00+09:00"))?.previewId).toBe("401");
    expect(restorePendingPreview("3", 4, Date.parse("2026-09-21T10:00:00+09:00"))).toBeNull();
    savePendingPreview(preview);
    expect(restorePendingPreview("3", 3, Date.parse("2026-09-21T10:11:00+09:00"))).toBeNull();
    clearPendingPreview("3");
  });
  it("selects the confirm endpoint from a restored preview action", () => {
    expect(confirmRequest({ ...preview, action: "REST_REMOVE", targetDate: "2026-09-22", reason: null })).toMatchObject({ method: "DELETE", path: "/api/v1/rest-days/2026-09-22?previewId=401" });
    expect(confirmRequest({ ...preview, action: "POLICY_APPLY", targetDate: null, reason: null })).toEqual({ method: "POST", path: "/api/v1/curriculums/3/reschedule", body: { previewId: "401" } });
  });
});
