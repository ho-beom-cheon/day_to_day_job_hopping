import type { Metadata } from "next";
import { Providers } from "./providers";
import "./globals.css";

export const metadata: Metadata = { title: "데일리 이직 | 매일 한 걸음", description: "오늘의 학습이 만드는 다음 커리어" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body><Providers>{children}</Providers></body></html>;
}
