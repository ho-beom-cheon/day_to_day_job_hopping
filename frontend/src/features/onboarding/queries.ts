import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeTemplateEnvelope } from "./contract";

export const onboardingKeys={all:["onboarding"] as const,templates:()=>[...onboardingKeys.all,"templates"] as const};
export const curriculumTemplatesQuery=queryOptions({queryKey:onboardingKeys.templates(),queryFn:async()=> (await api.request({path:"/api/v1/curriculum-templates",decode:decodeTemplateEnvelope})).data,staleTime:5*60_000});
