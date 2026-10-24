import { describe, expect, it } from "vitest";
import { maxStartDate, seoulToday, toggleWeekday, toTemplateViewModel } from "./model";

describe("onboarding model",()=>{
  it("shows the complete four-week course by its own duration",()=>expect(toTemplateViewModel({templateId:"9007199254740995",templateVersion:"2026.09.1",title:"Java 4주",description:"설명",durationMonths:1,totalWeeks:4,totalLearningDays:20,totalRequiredSessions:20,publishedAt:"now"})).toMatchObject({durationLabel:"1개월 · 4주",volumeLabel:"학습일 20일 · 필수 세션 20개"}));
  it("formats a template without changing its identifier",()=>expect(toTemplateViewModel({templateId:"9007199254740993",templateVersion:"v1",title:"과정",description:"설명",durationMonths:6,totalWeeks:26,totalLearningDays:120,totalRequiredSessions:360,publishedAt:"now"})).toMatchObject({templateId:"9007199254740993",durationLabel:"6개월 · 26주",volumeLabel:"학습일 120일 · 필수 세션 360개"}));
  it("uses the calendar date in Asia/Seoul",()=>expect(seoulToday(new Date("2026-09-20T16:30:00Z"))).toBe("2026-09-21"));
  it("allows starts through exactly ninety days",()=>expect(maxStartDate("2026-09-21")).toBe("2026-12-20"));
  it("toggles rest weekdays and prevents selecting all seven",()=>{
    expect(toggleWeekday(["SATURDAY","SUNDAY"],"SATURDAY")).toEqual(["SUNDAY"]);
    expect(toggleWeekday(["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"],"SUNDAY")).toHaveLength(6);
  });
});
