import { describe,expect,it } from "vitest";
import { decodeAttempt,decodeProblem,decodeWrongAnswer,decodeWrongPage } from "./contract";

describe("problem API contracts",()=>{
  it("keeps 64-bit ids as strings and decodes a practice problem",()=>{expect(decodeProblem({problemId:"9007199254740993",contentId:null,category:"JAVA",type:"TRUE_FALSE",difficulty:"MEDIUM",question:"List는 순서를 보장한다.",options:[],source:"CURATED",reviewStatus:"HUMAN_REVIEWED",previousAttemptCount:1,answered:true}).problemId).toBe("9007199254740993")});
  it("rejects unsupported question types",()=>{expect(()=>decodeProblem({problemId:"1",contentId:null,category:"JAVA",type:"ESSAY",difficulty:"MEDIUM",question:"q",options:[],source:"CURATED",reviewStatus:"HUMAN_REVIEWED",previousAttemptCount:0,answered:false})).toThrow("Unsupported problem type")});
  it("decodes result feedback without hiding retry semantics",()=>{expect(decodeAttempt({attemptId:"2",problemId:"1",contentId:null,answer:"1",correct:false,correctAnswer:"2",explanation:"because",attemptNo:2,wrongAnswerId:"3",wrongAnswerCreated:false,submittedAt:"2026-09-21T10:00:00+09:00"})).toMatchObject({attemptNo:2,correct:false,wrongAnswerCreated:false})});
  it("decodes list and revision-bearing detail",()=>{const summary={wrongAnswerId:"3",problemId:"1",category:"JAVA",question:"q",wrongCount:2,status:"UNRESOLVED",lastWrongAt:"2026-09-21T10:00:00+09:00",reviewCount:0};expect(decodeWrongPage({content:[summary],page:0,size:20,totalElements:1,totalPages:1}).content).toHaveLength(1);expect(decodeWrongAnswer({...summary,difficulty:"MEDIUM",myLastAnswer:"1",correctAnswer:"2",explanation:"e",firstWrongAt:"2026-09-20T10:00:00+09:00",resolvedAt:null,resolutionType:null,note:"",revision:4}).revision).toBe(4)})
});
