import { ArrowUpRight, BookOpen, CalendarDays, GraduationCap, Sparkles } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function Home() {
  return <main className="mx-auto min-h-screen max-w-5xl px-5 py-7 sm:px-10 sm:py-10">
    <header className="flex items-center justify-between border-b border-blue-100 pb-6">
      <Link href="/" className="flex items-center gap-3" aria-label="데일리 이직 홈">
        <span className="rounded-2xl bg-blue-600 p-3 text-white"><GraduationCap size={23} /></span>
        <div><p className="text-lg font-extrabold tracking-tight">데일리 이직</p><p className="text-xs text-slate-500">매일 한 걸음, 다음 커리어로</p></div>
      </Link>
      <span className="rounded-full bg-blue-50 px-3 py-1.5 text-xs font-semibold text-blue-700">학습 공간</span>
    </header>
    <section className="mt-8 rounded-3xl border border-white bg-gradient-to-br from-blue-100 via-white to-purple-100 p-7 shadow-sm sm:p-12">
      <p className="flex items-center gap-2 text-sm font-semibold text-blue-600"><Sparkles size={16} /> 작은 습관이 만드는 큰 변화</p>
      <h1 className="mt-5 text-3xl leading-snug font-extrabold tracking-tight sm:text-5xl">오늘의 배움이,<br />내일의 가능성으로.</h1>
      <p className="mt-5 max-w-md text-sm leading-7 text-slate-600 sm:text-base">6개월 동안 차근차근 쌓아가는 실력.<br />학습부터 문제 풀이, 시험과 복습까지 함께해요.</p>
      <Button className="mt-7" disabled aria-describedby="availability">학습 시작하기 <ArrowUpRight size={16} /></Button>
      <p id="availability" className="mt-3 text-xs text-slate-500">학습 서비스 준비 중이에요.</p>
    </section>
    <section aria-label="학습 과정 안내" className="mt-6 grid gap-4 sm:grid-cols-3">
      {[{ icon: CalendarDays, title: "나에게 맞는 일정", text: "하루의 학습과 휴식을 차근차근 계획해요.", color: "text-blue-600 bg-blue-50" },
        { icon: BookOpen, title: "매일 쌓이는 실력", text: "개념을 배우고 문제를 풀며 이해를 넓혀요.", color: "text-violet-600 bg-violet-50" },
        { icon: GraduationCap, title: "돌아보며 성장하기", text: "시험과 복습으로 배운 내용을 확인해요.", color: "text-emerald-600 bg-emerald-50" }].map(({icon: Icon, title, text, color}) =>
        <article key={title} className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
          <span className={`inline-flex rounded-xl p-3 ${color}`}><Icon size={21} /></span>
          <h2 className="mt-5 font-bold">{title}</h2><p className="mt-2 text-sm leading-6 text-slate-500">{text}</p>
        </article>)}
    </section>
    <footer className="py-8 text-center text-xs text-slate-400">서두르지 않아도 괜찮아요. 꾸준함이 실력이 됩니다.</footer>
  </main>;
}
