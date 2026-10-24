import { queryOptions } from "@tanstack/react-query";
import { api } from "@/api";
import type { Id } from "@/types/id";
import { decodeTaskEnvelope, decodeTodayEnvelope, type DailyTask } from "./contract";

export const taskKeys = {
  all: ["daily-task"] as const,
  today: () => [...taskKeys.all, "today"] as const,
  task: (taskId: Id) => [...taskKeys.all, "task", taskId] as const,
};

const waiting = (task: DailyTask | undefined) => !!task && (task.pending || task.status === "PREPARING" || task.status === "GRADING");

export const todayQuery = queryOptions({
  queryKey: taskKeys.today(),
  queryFn: async () => (await api.request({ path: "/api/v1/today", decode: decodeTodayEnvelope })).data,
  refetchInterval: query => query.state.data?.task?.status === "PREPARING" ? 3_000 : false,
});

export const taskQuery = (taskId: Id) => queryOptions({
  queryKey: taskKeys.task(taskId),
  queryFn: async () => (await api.request({ path: `/api/v1/tasks/${encodeURIComponent(taskId)}`, decode: decodeTaskEnvelope })).data,
  refetchInterval: query => waiting(query.state.data) ? 2_000 : false,
});

const post = async (path: `/api/v1/${string}`, body?: unknown) =>
  (await api.request({ path, method: "POST", body, decode: decodeTaskEnvelope })).data;
const put = async (path: `/api/v1/${string}`, body: unknown) =>
  (await api.request({ path, method: "PUT", body, decode: decodeTaskEnvelope })).data;
const task = (taskId: Id) => `/api/v1/tasks/${encodeURIComponent(taskId)}` as const;

export const startToday = () => post("/api/v1/today");
export const startTask = (taskId: Id) => post(`${task(taskId)}/start`);
export const saveDraft = (taskId: Id, turnNo: number, answer: string, sheetOpened: boolean) => put(`${task(taskId)}/turns/${turnNo}/answer`, { answer, sheetOpened });
export const submitTurn = (taskId: Id, turnNo: number, answer: string, sheetOpened: boolean) => post(`${task(taskId)}/turns/${turnNo}/submit`, { answer, sheetOpened });
export const selfGrade = (taskId: Id, metCriteria: string[]) => post(`${task(taskId)}/self-grade`, { metCriteria });
export const finishTask = (taskId: Id) => post(`${task(taskId)}/finish`);
export const swapTask = (taskId: Id) => post(`${task(taskId)}/swap`);
export const saveCard = (taskId: Id, card: { title: string; body: string; interviewLine: string; reviewPrompt: string }) => put(`${task(taskId)}/card`, card);
export const sendFeedback = (taskId: Id, usefulness: number, note: string) => post(`${task(taskId)}/feedback`, { usefulness, note });
export const reportIssue = (taskId: Id, report: { target: "MATERIAL" | "FEEDBACK" | "FOLLOWUP"; reason: string; note: string; excludeScore: boolean }) => post(`${task(taskId)}/reports`, report);
