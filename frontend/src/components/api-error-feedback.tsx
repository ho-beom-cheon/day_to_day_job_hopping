"use client";
import type { ApiErrorViewModel } from "@/mappers/api-error";
import { StatePanel } from "./ui/state-panel";

export function ApiErrorFeedback({ viewModel, onRefresh }: { viewModel: ApiErrorViewModel; onRefresh: () => void }) {
  return <StatePanel state={viewModel.state} onRefresh={onRefresh} />;
}
