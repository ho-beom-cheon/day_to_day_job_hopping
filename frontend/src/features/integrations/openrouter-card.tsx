"use client";
import Link from "next/link";
import { Bot } from "lucide-react";
import { BrowserConnectionControls } from "./browser-connection-controls";

export function OpenRouterCard() {
  return <article className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-6">
    <h2 className="flex items-center gap-3 text-lg font-black text-slate-900 dark:text-slate-50"><Bot size={22}/>AI · OpenRouter</h2>
    <p className="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-300">연결 후 AI 도우미에서 오늘의 학습 피드백을 만들 수 있습니다. 요청할 때 해당 날짜의 학습 제목과 진행 상태를 OpenRouter에 전송합니다. 사용 요금은 연결한 OpenRouter 계정에 적용됩니다.</p>
    <BrowserConnectionControls provider="openrouter"/>
    <Link href="/assistant" className="mt-5 inline-block text-sm font-bold text-blue-700 underline dark:text-blue-300">AI 도우미 열기</Link>
  </article>;
}
