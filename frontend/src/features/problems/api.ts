import { api } from "@/api";
import { createMutationAction } from "@/api/client";
import { decodeEnvelope } from "@/api/contract";
import { decodeAttempt, decodeProblem, decodeWrongAnswer, decodeWrongPage } from "./contract";

export async function getProblem(problemId: string, contentId?: string) {
  return (await api.request({path:`/api/v1/problems/${problemId}${contentId ? `?contentId=${encodeURIComponent(contentId)}` : ""}`,decode:v=>decodeEnvelope(v,decodeProblem).data})).data;
}
export async function submitAttempt(problemId: string, answer: string, contentId: string|null) {
  const action=createMutationAction({problemId,answer,contentId});
  return (await api.request({path:"/api/v1/problem-attempts",method:"POST",body:action.getPayload(),idempotencyKey:action.idempotencyKey,decode:v=>decodeEnvelope(v,decodeAttempt).data})).data;
}
export async function getWrongAnswers(status?: "UNRESOLVED"|"RESOLVED") {
  const query=status?`?status=${status}`:"";
  return (await api.request({path:`/api/v1/wrong-answers${query}`,decode:v=>decodeEnvelope(v,decodeWrongPage).data})).data;
}
export async function getWrongAnswer(id:string) {
  const response=await api.request({path:`/api/v1/wrong-answers/${id}`,decode:v=>decodeEnvelope(v,decodeWrongAnswer).data});
  return {...response.data,etag:response.etag};
}
export async function saveWrongNote(id:string,note:string,etag:string) {
  return (await api.request({path:`/api/v1/wrong-answers/${id}`,method:"PATCH",body:{note},etag,decode:v=>decodeEnvelope(v,decodeWrongAnswer).data})).data;
}
export async function resolveWrong(id:string) {
  const action=createMutationAction({resolutionType:"MANUAL",attemptId:null});
  return (await api.request({path:`/api/v1/wrong-answers/${id}/resolve`,method:"POST",body:action.getPayload(),idempotencyKey:action.idempotencyKey,decode:v=>decodeEnvelope(v,decodeWrongAnswer).data})).data;
}
export async function reopenWrong(id:string) {
  return (await api.request({path:`/api/v1/wrong-answers/${id}/reopen`,method:"POST",decode:v=>decodeEnvelope(v,decodeWrongAnswer).data})).data;
}
