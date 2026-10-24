import { api } from "@/api";import { createMutationAction } from "@/api/client";import { decodeEnvelope } from "@/api/contract";import { decodeJob,decodeReceipt,decodeUsage } from "./contract";
export const getAiUsage=async()=>(await api.request({path:"/api/v1/ai/usage",decode:v=>decodeEnvelope(v,decodeUsage).data})).data;
export const getAiJob=async(id:string)=>(await api.request({path:`/api/v1/ai/jobs/${id}`,decode:v=>decodeEnvelope(v,decodeJob).data})).data;
export const requestDailyFeedback=async(curriculumId:string,date:string)=>{const action=createMutationAction({curriculumId,date});return(await api.request({path:"/api/v1/ai/daily-feedback",method:"POST",body:action.getPayload(),idempotencyKey:action.idempotencyKey,decode:v=>decodeEnvelope(v,decodeReceipt).data})).data};
