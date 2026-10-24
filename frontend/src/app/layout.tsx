import type { Metadata } from "next";
import "@fontsource-variable/noto-sans-kr";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = { title: "Daily Leap | 매일 한 걸음", description: "오늘의 학습이 만드는 다음 커리어" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{__html:`(()=>{try{const t=localStorage.getItem("daily-career-theme");const v=t==="dark"||t==="light"?t:(matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light");document.documentElement.dataset.theme=v;document.documentElement.style.colorScheme=v}catch{}})()`}}/></head><body className="font-sans"><Providers>{children}</Providers></body></html>;
}
