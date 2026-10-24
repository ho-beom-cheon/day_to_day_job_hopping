import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeCategoriesEnvelope, decodeEvaluationsEnvelope, decodeOverallEnvelope } from "./contract";
export const progressKeys={all:["progress"] as const};
export const overallProgressQuery=queryOptions({queryKey:[...progressKeys.all,"overall"],queryFn:async()=>(await api.request({path:"/api/v1/learning-progress",decode:decodeOverallEnvelope})).data.data});
export const categoryProgressQuery=queryOptions({queryKey:[...progressKeys.all,"categories"],queryFn:async()=>(await api.request({path:"/api/v1/learning-progress/categories",decode:decodeCategoriesEnvelope})).data.data});
export const evaluationsQuery=queryOptions({queryKey:[...progressKeys.all,"evaluations"],queryFn:async()=>(await api.request({path:"/api/v1/evaluations/monthly?page=0&size=20&sort=createdAt,desc",decode:decodeEvaluationsEnvelope})).data.data});
