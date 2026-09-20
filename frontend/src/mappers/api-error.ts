import { ApiError } from "@/api/client";

export type ApiErrorViewModel = {
  state: "conflict-stale" | "error";
  message: string;
  traceId: string | null;
};

export function toApiErrorViewModel(error: unknown): ApiErrorViewModel {
  if (error instanceof ApiError) {
    return {
      state: error.kind === "stale" || error.kind === "precondition" ? "conflict-stale" : "error",
      message: error.error?.message ?? "요청을 처리하지 못했어요.",
      traceId: error.traceId,
    };
  }
  return { state: "error", message: "요청을 처리하지 못했어요.", traceId: null };
}
