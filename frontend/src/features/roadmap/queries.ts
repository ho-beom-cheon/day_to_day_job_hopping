import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeCurriculumEnvelope, decodeMonthsEnvelope, decodeWeekDetailEnvelope, decodeWeeksEnvelope } from "./contract";

export const roadmapKeys = { all: ["roadmap"] as const, current: () => [...roadmapKeys.all, "current"] as const, months: (id: string) => [...roadmapKeys.all, id, "months"] as const, weeks: (id: string) => [...roadmapKeys.all, id, "weeks"] as const, week: (id: string, weekNo: number) => [...roadmapKeys.all, id, "week", weekNo] as const };
export const currentCurriculumQuery = queryOptions({ queryKey: roadmapKeys.current(), queryFn: () => api.request({ path: "/api/v1/curriculums/current", decode: decodeCurriculumEnvelope }), staleTime: 30_000 });
export const roadmapMonthsQuery = (id: string) => queryOptions({ queryKey: roadmapKeys.months(id), queryFn: async () => (await api.request({ path: `/api/v1/curriculums/${id}/months`, decode: decodeMonthsEnvelope })).data });
export const roadmapWeeksQuery = (id: string) => queryOptions({ queryKey: roadmapKeys.weeks(id), queryFn: async () => (await api.request({ path: `/api/v1/curriculums/${id}/weeks`, decode: decodeWeeksEnvelope })).data });
export const roadmapWeekQuery = (id: string, weekNo: number) => queryOptions({ queryKey: roadmapKeys.week(id, weekNo), queryFn: async () => (await api.request({ path: `/api/v1/curriculums/${id}/weeks/${weekNo}`, decode: decodeWeekDetailEnvelope })).data });
