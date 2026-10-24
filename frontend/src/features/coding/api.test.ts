import { describe, expect, it } from "vitest";
import { decodeLink, decodeLog, decodeOverview } from "./api";

const link = { linkId: "7", site: "PROGRAMMERS", siteProblemRef: "42576", title: "완주하지 못한 선수", url: "https://school.programmers.co.kr/learn/courses/30/lessons/42576",
  level: 1, category: "DATA_STRUCTURE", own: false, hidden: false, attempts: 0, lastResult: null };
const log = { logId: "3", problemLinkId: "7", solvedOn: "2026-10-22", site: "PROGRAMMERS", problemTitle: "완주하지 못한 선수", problemUrl: link.url, category: "DATA_STRUCTURE",
  language: "Java", result: "SOLUTION_VIEWED", minutes: 30, stuckNote: "해시 카운트", note: "", source: "MANUAL", retryStep: 1, nextRetryAt: "2026-10-25", revision: 0 };

describe("coding contract", () => {
  it("keeps ids as strings and decodes the overview", () => {
    const overview = decodeOverview({ weeklyTarget: 2, weekStart: "2026-10-19", weekDone: 1, dueRetries: [log], categoryStats: [{ category: "DP", attempts: 2, successes: 1 }],
      recommendations: [{ reason: "RETRY_DUE", link, retryOf: log }, { reason: "NEW_CATEGORY", link, retryOf: null }] });
    expect(overview.recommendations[0].retryOf?.logId).toBe("3");
    expect(overview.recommendations[1].link?.linkId).toBe("7");
    expect(decodeLink({ ...link, solvedBefore: true }).solvedBefore).toBe(true);
    expect(decodeLink(link).solvedBefore).toBe(false);
    expect(overview.categoryStats[0]).toEqual({ category: "DP", attempts: 2, successes: 1 });
  });

  it("rejects unknown categories, results and numeric ids", () => {
    expect(() => decodeLink({ ...link, category: "COBOL" })).toThrow();
    expect(() => decodeLog({ ...log, result: "PARTIAL" })).toThrow();
    expect(() => decodeLink({ ...link, linkId: 7 })).toThrow();
  });
});
