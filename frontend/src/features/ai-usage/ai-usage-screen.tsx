"use client";

import { queryOptions, useQuery } from "@tanstack/react-query";
import { api } from "@/api";
import { decodeEnvelope } from "@/api/contract";
import { StatePanel } from "@/components/ui/state-panel";

type Usage = { enabled: boolean; model: string; periodStart: string; limitKrw: number; spentKrw: number; reservedKrw: number; remainingKrw: number; sixMonthSpentKrw: number; fxRateKrw: number; priceVerifiedAt: string | null; byJobType: { jobType: string; calls: number; spentKrw: number }[] };

function decodeUsage(value: unknown): Usage {
  if (!value || typeof value !== "object") throw new TypeError("usage must be an object");
  const data = value as Record<string, unknown>;
  const number = (key: string) => { if (typeof data[key] !== "number") throw new TypeError(`${key} must be a number`); return data[key] as number; };
  if (!Array.isArray(data.byJobType) || typeof data.model !== "string" || typeof data.periodStart !== "string") throw new TypeError("Invalid usage");
  return { enabled: data.enabled === true, model: data.model, periodStart: data.periodStart, limitKrw: number("limitKrw"), spentKrw: number("spentKrw"), reservedKrw: number("reservedKrw"),
    remainingKrw: number("remainingKrw"), sixMonthSpentKrw: number("sixMonthSpentKrw"), fxRateKrw: number("fxRateKrw"),
    priceVerifiedAt: typeof data.priceVerifiedAt === "string" ? data.priceVerifiedAt : null,
    byJobType: data.byJobType.map(item => { const row = item as Record<string, unknown>; return { jobType: String(row.jobType), calls: Number(row.calls), spentKrw: Number(row.spentKrw) }; }) };
}

const usageQuery = queryOptions({ queryKey: ["ai-usage"], queryFn: async () => (await api.request({ path: "/api/v1/ai/usage", decode: value => decodeEnvelope(value, decodeUsage).data })).data });
const won = (value: number) => `${value.toLocaleString("ko-KR", { maximumFractionDigits: 1 })}원`;
const labels: Record<string, string> = { TASK_BANK_GENERATE: "과제 생성", DAILY_TASK_GRADE: "채점", DAILY_FOLLOWUP_TURN: "꼬리질문", RESUME_CLAIM_REVIEW: "이력서 점검" };

export function AiUsageScreen() {
  const usage = useQuery(usageQuery);
  if (usage.isPending) return <StatePanel state="loading"/>;
  if (usage.isError) return <StatePanel state="error" onRefresh={() => void usage.refetch()}/>;
  const data = usage.data;
  const used = Math.min(100, (data.spentKrw + data.reservedKrw) / data.limitKrw * 100);
  return <div className="mx-auto grid max-w-3xl gap-5 pb-10">
    <header>
      <p className="text-sm font-extrabold text-blue-600 dark:text-blue-300">AI 사용량</p>
      <h1 className="mt-1 text-3xl font-black tracking-[-.04em] text-[#0a1837] dark:text-slate-50">이번 달 {data.periodStart.slice(0, 7)}</h1>
      <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">월 상한에 닿으면 AI 호출을 보내지 않고 자가 채점으로 진행해요. 단가는 공식 페이지에서 확인한 값만 써요.</p>
    </header>
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7">
      <div className="flex flex-wrap items-end justify-between gap-3"><p className="text-3xl font-black text-[#0a1837] dark:text-slate-50">{won(data.spentKrw)}</p><p className="text-sm text-slate-500">상한 {won(data.limitKrw)} · 남은 금액 {won(data.remainingKrw)}</p></div>
      <div className="mt-3 h-3 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800"><div className={`h-full rounded-full ${used >= 80 ? "bg-rose-500" : used >= 50 ? "bg-amber-500" : "bg-blue-600"}`} style={{ width: `${used}%` }}/></div>
      <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
        <div><dt className="text-xs font-bold text-slate-500">처리 중 예약</dt><dd className="font-bold text-slate-800 dark:text-slate-100">{won(data.reservedKrw)}</dd></div>
        <div><dt className="text-xs font-bold text-slate-500">최근 6개월 누적</dt><dd className="font-bold text-slate-800 dark:text-slate-100">{won(data.sixMonthSpentKrw)} / 100,000원</dd></div>
        <div><dt className="text-xs font-bold text-slate-500">모델 · 상태</dt><dd className="font-bold text-slate-800 dark:text-slate-100">{data.model} · {data.enabled ? "사용 가능" : "꺼짐"}</dd></div>
        <div><dt className="text-xs font-bold text-slate-500">단가 확인일 · 환율</dt><dd className="font-bold text-slate-800 dark:text-slate-100">{data.priceVerifiedAt ?? "단가 없음"} · {data.fxRateKrw.toLocaleString("ko-KR")}원/$</dd></div>
      </dl>
    </section>
    <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 sm:p-7">
      <h2 className="text-lg font-black text-[#0a1837] dark:text-slate-50">작업 유형별</h2>
      {data.byJobType.length ? <table className="mt-3 w-full text-sm"><thead><tr className="text-left text-xs text-slate-500"><th className="py-2">유형</th><th>호출</th><th className="text-right">비용</th></tr></thead>
        <tbody>{data.byJobType.map(row => <tr key={row.jobType} className="border-t border-slate-100 dark:border-slate-800"><td className="py-2 font-semibold text-slate-800 dark:text-slate-100">{labels[row.jobType] ?? row.jobType}</td><td>{row.calls}회</td><td className="text-right">{won(row.spentKrw)}</td></tr>)}</tbody></table>
        : <p className="mt-3 text-sm text-slate-500">이번 달 AI 호출이 아직 없어요.</p>}
      <p className="mt-4 text-xs leading-5 text-slate-500">매달 OpenAI 사용량 대시보드와 이 합계를 비교해 보세요. 10% 이상 차이 나면 단가나 환율 설정을 고쳐야 해요.</p>
    </section>
  </div>;
}
