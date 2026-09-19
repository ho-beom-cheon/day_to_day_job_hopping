import { ApiError } from "@/api/client";

export type ApiErrorViewModel = {
  state: "conflict-stale" | "error";
  traceId: string | null;
};

// HTTP fallback only. Canonical ErrorCode decoding awaits the original ErrorEnvelope.
export function toApiErrorViewModel(error: unknown): ApiErrorViewModel {
  if (error instanceof ApiError) {
    return {
      state: error.kind === "stale" || error.kind === "precondition" ? "conflict-stale" : "error",
      traceId: error.traceId,
    };
  }
  return { state: "error", traceId: null };
}
