import { Button } from "./button";

export type UiState = "loading" | "empty" | "error" | "disabled" | "completed" | "processing" | "conflict-stale" | "external-sync-partial-failure";
const copy: Record<UiState, { title: string; message: string }> = {
  loading: { title: "불러오는 중", message: "학습 정보를 가져오고 있어요." },
  empty: { title: "아직 학습 기록이 없어요", message: "학습을 시작하면 이곳에 기록이 쌓여요." },
  error: { title: "정보를 불러오지 못했어요", message: "잠시 후 다시 시도해 주세요." },
  disabled: { title: "아직 사용할 수 없어요", message: "준비가 끝나면 이용할 수 있어요." },
  completed: { title: "완료했어요", message: "학습 기록이 저장되었어요." },
  processing: { title: "처리하고 있어요", message: "결과가 준비되면 확인할 수 있어요." },
  "conflict-stale": { title: "정보가 변경되었어요", message: "최신 정보를 불러온 뒤 변경 내용을 다시 확인해 주세요." },
  "external-sync-partial-failure": { title: "일부 외부 연동이 지연되고 있어요", message: "학습 기록은 저장되었어요. 외부 연동 상태를 다시 확인해 주세요." },
};

export function StatePanel({ state, onRefresh }: { state: UiState; onRefresh?: () => void }) {
  const isError = ["error", "conflict-stale", "external-sync-partial-failure"].includes(state);
  const busy = state === "loading" || state === "processing";
  return <section role={isError ? "alert" : "status"} aria-busy={busy} className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
    <h2 className="text-base font-bold text-slate-900">{copy[state].title}</h2>
    <p className="mt-2 text-sm leading-6 text-slate-500">{copy[state].message}</p>
    {onRefresh && isError && <Button className="mt-4" variant="outline" onClick={onRefresh}>최신 정보 불러오기</Button>}
  </section>;
}
