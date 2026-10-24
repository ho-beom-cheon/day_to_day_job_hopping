import { api } from "@/api";
import { createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";

export type Kind = "DAILY" | "WEEKLY" | "MONTHLY" | "FINAL";
export type Scope = { kind:Kind;key:string;title:string;objectiveCount:number;newObjectiveCount:number;sourceCount:number;extraSourceCount:number;available:boolean;replacement:string;sourceReady:boolean;sourceIssues?:{sourceId:string;title:string;reasons:string[]}[];partCount:number };
export type EvaluationSummary = { id:string;kind:Kind;key:string;title:string;objectiveCount:number;partCount:number;submittedPartCount:number;createdAt:string };
export type Question = { id:string;type:"MCQ"|"SHORT_ANSWER"|"INTERVIEW";prompt:string;choices:string[];objectiveIds:string[] };
export type Grade = {questionId:string;status:string;answer:string;earnedPoints?:number;correctAnswer:string;explanation:string;feedback?:string;holdReason?:string;improvedAnswer?:string;missingPoints?:string[]};
export type Part = { id:string;partNo:number;revision:number;status:string;serverNow:string;expiresAt?:string;answers:Record<string,string>;plan:{assessmentSeconds:number;totalSeconds:number;slots:{id:string}[]};questions?:Question[];result?:{status:string;score:number|null;results:Grade[]};gradingRequested?:boolean;errorCode?:string;review?:{id:string;answer:number;wrongAnswerExplanations:string[];sourceEvidence:{sourceId:string;excerpt:string}[]}[] };
export type Evaluation = EvaluationSummary & {score?:number|null;objectives:{id:string;subject:string;description:string;extra:boolean}[];parts:Part[]};
export type EvaluationList = {sessionMinutes?:number;scopes:Scope[];evaluations:EvaluationSummary[]};
// Server controls these records; reject missing roots instead of treating an error payload as an empty exam.
function decodeList(value:unknown):EvaluationList {
  if(!value || typeof value!=="object" || !("scopes" in value) || !Array.isArray(value.scopes) || !("evaluations" in value) || !Array.isArray(value.evaluations))throw new Error("평가 목록 형식이 올바르지 않습니다.");
  return value as EvaluationList;
}
function decodeEvaluation(value:unknown):Evaluation {
  if(!value || typeof value!=="object" || !("id" in value) || typeof value.id!=="string" || !("parts" in value) || !Array.isArray(value.parts))throw new Error("평가 형식이 올바르지 않습니다.");
  return value as Evaluation;
}
export async function listEvaluations(){return(await api.request({path:"/api/v1/study-evaluations",decode:v=>decodeEnvelope(v,decodeList).data})).data;}
export async function getEvaluation(id:string){return(await api.request({path:`/api/v1/study-evaluations/${id}`,decode:v=>decodeEnvelope(v,decodeEvaluation).data})).data;}
export function evaluationAction(kind:Kind,key:string,retakeOf?:string){return createMutationAction({kind,key,retakeOf});}
export async function createEvaluation(action:ReturnType<typeof evaluationAction>){return(await api.request({path:"/api/v1/study-evaluations",method:"POST",body:action.getPayload(),idempotencyKey:action.idempotencyKey,decode:v=>decodeEnvelope(v,decodeEvaluation).data})).data;}
export async function updatePart(id:string,part:number,action:"generate"|"start"|"grade"|"save"|"submit",answers?:Record<string,string>,revision?:number){
  return(await api.request({path:`/api/v1/study-evaluations/${id}/parts/${part}/${action==="save"?"answers":action}`,method:action==="save"?"PUT":"POST",body:answers?{answers}:undefined,etag:revision===undefined?undefined:`"${revision}"`,decode:v=>decodeEnvelope(v,decodeEvaluation).data})).data;
}
