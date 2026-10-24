import { AlertCircle, ArrowLeft, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { BrandLogo } from "@/components/brand-logo";
import { Button } from "@/components/ui/button";
import { loginFailureCopy } from "./login-copy";

export function LoginFailure({ errorCode }: { errorCode?: string }) {
  const copy = loginFailureCopy(errorCode);
  return <main className="theme-app grid min-h-screen place-items-center px-5 py-10">
    <section className="w-full max-w-md rounded-[2rem] border border-slate-200 bg-white p-7 shadow-xl shadow-blue-950/5 sm:p-9">
      <Link href="/" aria-label="Daily Leap 홈"><BrandLogo/></Link>
      <div className="mt-8 grid size-12 place-items-center rounded-2xl bg-rose-50 text-rose-600"><AlertCircle size={24}/></div>
      <h1 className="mt-5 text-2xl font-extrabold tracking-tight text-slate-950">{copy.title}</h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">{copy.message}</p>
      <Button asChild className="mt-7 w-full"><a href="/oauth2/authorization/google">Google로 다시 시도</a></Button>
      <Link href="/" className="mt-4 flex min-h-11 items-center justify-center gap-2 text-sm font-semibold text-slate-500 hover:text-slate-900"><ArrowLeft size={16}/>처음 화면으로</Link>
      <p className="mt-5 flex items-start gap-2 border-t border-slate-100 pt-5 text-xs leading-5 text-slate-500"><ShieldCheck className="mt-0.5 shrink-0 text-emerald-600" size={15}/>Google 비밀번호나 액세스 토큰은 Daily Leap에 저장하지 않아요.</p>
    </section>
  </main>;
}
