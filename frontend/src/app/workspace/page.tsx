import { ArrowRight, CalendarCheck2, CircleCheckBig } from "lucide-react";

export default function WorkspacePage() {
  return <div>
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-sm font-bold text-blue-700">오늘의 학습</p><h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-950 sm:text-3xl">학습 공간을 준비했어요</h1><p className="mt-2 text-sm leading-6 text-slate-600">이제 과정 배정과 오늘 학습 데이터를 이 공통 화면에 연결할 수 있어요.</p></div><span className="inline-flex w-fit items-center gap-2 rounded-full bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700"><CircleCheckBig size={15}/>공통 기반 연결됨</span></div>
    <section className="mt-7 grid gap-4 md:grid-cols-[1.4fr_1fr]">
      <article className="rounded-3xl bg-gradient-to-br from-blue-600 to-indigo-600 p-6 text-white shadow-sm sm:p-8"><CalendarCheck2 size={23}/><h2 className="mt-6 text-xl font-extrabold">다음 단계에서 실제 학습을 연결해요</h2><p className="mt-2 max-w-lg text-sm leading-6 text-blue-100">인증 상태, 공통 API 해석, 반응형 내비게이션은 준비되었습니다. 과정이 배정되면 오늘 할 일을 이 자리에 표시합니다.</p><span className="mt-6 inline-flex min-h-11 items-center gap-2 rounded-xl bg-white/15 px-4 text-sm font-bold text-white">과정 연결 대기 <ArrowRight size={16}/></span></article>
      <article className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"><p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">Foundation</p><h2 className="mt-3 text-lg font-extrabold text-slate-950">일관된 사용자 흐름</h2><ul className="mt-5 grid gap-3 text-sm text-slate-600"><li>DTO → ViewModel 경계</li><li>세션·CSRF 자동 복원</li><li>로딩·오류·만료 상태</li><li>데스크톱·모바일 레이아웃</li></ul></article>
    </section>
  </div>;
}
