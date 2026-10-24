import { describe, expect, it } from "vitest";
import { decodeCurriculum, decodeCurriculumTemplates } from "./contract";

const template={templateId:"9007199254740993",templateVersion:"2026.09.1",title:"6개월 과정",description:"설명",durationMonths:6,totalWeeks:26,totalLearningDays:129,totalRequiredSessions:387,publishedAt:"2026-09-21T01:00:00+09:00"};
const curriculum={curriculumId:"9007199254740995",templateId:template.templateId,templateVersion:template.templateVersion,title:template.title,startDate:"2026-09-21",nominalEndDate:"2027-03-20",endDate:"2027-03-28",totalWeeks:26,totalLearningDays:129,totalRequiredSessions:387,completedRequiredSessions:0,currentWeek:1,currentDayNo:1,status:"IN_PROGRESS",completionRate:0,scheduleRevision:1,createdAt:"2026-09-21T01:00:00+09:00",studyPolicy:{dailyStudyMinutes:60,restDaysPerWeek:2,restWeekdays:["SATURDAY","SUNDAY"]}};

describe("onboarding contract",()=>{
  it("keeps 64-bit identifiers as strings",()=>expect(decodeCurriculumTemplates([template])[0].templateId).toBe("9007199254740993"));
  it("rejects a numeric identifier",()=>expect(()=>decodeCurriculumTemplates([{...template,templateId:1}])).toThrow());
  it("decodes nullable current position after completion",()=>expect(decodeCurriculum({...curriculum,currentWeek:null,currentDayNo:null,status:"COMPLETED"})).toMatchObject({currentWeek:null,currentDayNo:null}));
  it("rejects undocumented curriculum status",()=>expect(()=>decodeCurriculum({...curriculum,status:"UNKNOWN"})).toThrow("status"));
  it("rejects undocumented weekdays",()=>expect(()=>decodeCurriculum({...curriculum,studyPolicy:{...curriculum.studyPolicy,restWeekdays:["HOLIDAY"]}})).toThrow("weekday"));
});
