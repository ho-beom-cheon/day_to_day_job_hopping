import Image from "next/image";
import { cn } from "@/lib/utils";

const iconSources = {
  achievement: "/assets/app-icons/achievement.png",
  assistant: "/assets/app-icons/assistant.png",
  calendar: "/assets/app-icons/calendar.png",
  exam: "/assets/quick-menu/exam.png",
  goal: "/assets/app-icons/goal.png",
  learning: "/assets/app-icons/learning.png",
  practice: "/assets/quick-menu/practice.png",
  progress: "/assets/quick-menu/progress.png",
  review: "/assets/quick-menu/review.png",
  roadmap: "/assets/app-icons/roadmap.png",
} as const;

export type IllustrationIconName = keyof typeof iconSources;

export function IllustrationIcon({
  name,
  size = "md",
  className,
  decorative = true,
  eager = false,
}: {
  name: IllustrationIconName;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
  decorative?: boolean;
  eager?: boolean;
}) {
  const sizes = { sm: "size-10", md: "size-14", lg: "size-20", xl: "size-28" };
  const pixels = { sm: "40px", md: "56px", lg: "80px", xl: "112px" };

  return (
    <span className={cn("relative inline-block shrink-0", sizes[size], className)} aria-hidden={decorative || undefined}>
      <Image
        src={iconSources[name]}
        alt={decorative ? "" : name}
        fill
        unoptimized
        loading={eager ? "eager" : "lazy"}
        sizes={pixels[size]}
        className="object-contain drop-shadow-[0_8px_12px_rgba(15,23,42,0.14)]"
      />
    </span>
  );
}
