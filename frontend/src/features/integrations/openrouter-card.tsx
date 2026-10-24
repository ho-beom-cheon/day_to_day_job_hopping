"use client";
import { Bot } from "lucide-react";
import { BrowserConnectionControls } from "./browser-connection-controls";

export function OpenRouterCard() {
  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6">
    <h2 className="flex items-center gap-3 text-lg font-black text-slate-900 dark:text-slate-50"><Bot size={22}/>AI · OpenRouter</h2>
    <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">새 학습 루프는 서버의 OpenAI 키와 월 예산으로 동작하며 이 연결을 사용하지 않습니다. 필요 없으면 연결을 해제하세요.</p>
    <BrowserConnectionControls provider="openrouter"/>
  </article>;
}
