import { describe, expect, it } from "vitest";
import { decodeUserSettings } from "./contract";

const valid = {
  studyPolicy: { dailyStudyMinutes: 75, restDaysPerWeek: 1, restWeekdays: ["WEDNESDAY"] },
  weekdayStudyStartTime: "19:30", weekendStudyStartTime: "14:00",
  theme: "SYSTEM", language: "KO", timeZone: "Asia/Seoul", revision: 3, updatedAt: "2026-09-24T07:00:00+09:00",
};

describe("user settings contract", () => {
  it("keeps server preferences and revision", () => expect(decodeUserSettings(valid)).toEqual(valid));
  it.each([
    { studyPolicy: { ...valid.studyPolicy, dailyStudyMinutes: 14 } },
    { studyPolicy: { ...valid.studyPolicy, dailyStudyMinutes: 481 } },
    { studyPolicy: { ...valid.studyPolicy, restDaysPerWeek: 2 } },
    { studyPolicy: { ...valid.studyPolicy, restDaysPerWeek: 2, restWeekdays: ["MONDAY", "MONDAY"] } },
    { studyPolicy: { ...valid.studyPolicy, restWeekdays: ["FUNDAY"] } },
    { weekdayStudyStartTime: "24:00" }, { weekendStudyStartTime: "9:30" },
    { theme: "unknown" }, { revision: null }, { revision: -1 },
  ])("rejects malformed server preferences: %j", patch => expect(() => decodeUserSettings({ ...valid, ...patch })).toThrow());
});
