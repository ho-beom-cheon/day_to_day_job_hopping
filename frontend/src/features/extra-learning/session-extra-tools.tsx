"use client";

import Link from "next/link";
import { modeLabels, type ExtraEntry } from "./api";

export function SessionExtraTools({ entry }: { entry: ExtraEntry }) {
  return <section className="rounded-2xl border border-blue-200 bg-blue-50 p-5 dark:border-slate-700 dark:bg-slate-900">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="font-extrabold text-slate-950">{modeLabels[entry.mode]} · 예상 {entry.targetMinutes}분</h2>
      <Link className="inline-flex min-h-11 items-center text-sm font-bold text-blue-700" href="/learning/extra">추가 학습 목록 →</Link>
    </div>
    <p className="mt-2 text-sm leading-6 text-slate-600">
      {entry.status === "COMPLETED" ? "학습 완료가 저장됐어요. " : "학습 내용과 문제를 마치면 완료할 수 있어요. 시간을 직접 입력할 필요는 없어요. "}
      {entry.mode === "AHEAD" ? "완료하면 정규 진도에 반영되며 예정일에 다시 완료할 필요는 없어요." : "기본 학습의 완료 상태는 유지돼요."}
    </p>
  </section>;
}
