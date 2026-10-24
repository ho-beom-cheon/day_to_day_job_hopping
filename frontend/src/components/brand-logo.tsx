import Image from "next/image";
import { cn } from "@/lib/utils";

export function BrandLogo({ compact = false, inverse = false, className }: { compact?: boolean; inverse?: boolean; className?: string }) {
  return <span className={cn("inline-flex items-center gap-2.5", className)}>
    <span className={cn("relative shrink-0", compact ? "size-9" : "size-11")}>
      <Image src="/assets/brand/daily-leap-mark.png" alt="" fill unoptimized sizes={compact ? "36px" : "44px"} className="scale-[1.28] object-contain"/>
    </span>
    <span className={cn("font-black tracking-[-0.045em]", compact ? "text-lg" : "text-xl", inverse ? "text-white" : "text-[#0a1837] dark:text-white")}>
      Daily <span className="text-blue-600 dark:text-blue-400">Leap</span>
    </span>
  </span>;
}
