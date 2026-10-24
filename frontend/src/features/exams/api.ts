import { api } from "@/api";import { createMutationAction } from "@/api/client";import { decodeEnvelope } from "@/api/contract";import { decodeAttempt,decodeExamAttemptSummaries,decodeExamPage,decodeResult,decodeSubmit } from "./contract";
export async function getExams(){
  const load=async(page:number)=>(await api.request({path:`/api/v1/tests?page=${page}&size=100`,decode:v=>decodeEnvelope(v,decodeExamPage).data})).data;
  const first=await load(0),content=[...first.content];
  for(let page=1;page<first.totalPages;page++)content.push(...(await load(page)).content);
  return {...first,content};
}
export async function getRecentExamAttempts(testId:string){return(await api.request({path:`/api/v1/test-attempts?testId=${testId}&page=0&size=20`,decode:v=>decodeEnvelope(v,decodeExamAttemptSummaries).data})).data}
export async function startExam(testId:string){const a=createMutationAction({testId});return(await api.request({path:`/api/v1/tests/${testId}/attempts`,method:"POST",idempotencyKey:a.idempotencyKey,decode:v=>decodeEnvelope(v,decodeAttempt).data})).data}
export async function getAttempt(id:string){return(await api.request({path:`/api/v1/test-attempts/${id}`,decode:v=>decodeEnvelope(v,decodeAttempt).data})).data}
export async function saveAnswer(attemptId:string,problemId:string,answer:string|null,revision:number){return(await api.request({path:`/api/v1/test-attempts/${attemptId}/answers/${problemId}`,method:"PUT",body:{answer},etag:`"${revision}"`,decode:v=>decodeEnvelope(v,x=>x).data})).data}
export async function submitExam(id:string){const a=createMutationAction({attemptId:id});return(await api.request({path:`/api/v1/test-attempts/${id}/submit`,method:"POST",idempotencyKey:a.idempotencyKey,decode:v=>decodeEnvelope(v,decodeSubmit).data})).data}
export async function getResult(id:string){return(await api.request({path:`/api/v1/test-attempts/${id}/result`,decode:v=>decodeEnvelope(v,decodeResult).data})).data}
