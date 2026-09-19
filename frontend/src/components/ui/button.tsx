import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const variants = cva("inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-5 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600 disabled:pointer-events-none disabled:opacity-45", {
  variants: { variant: { default: "bg-blue-600 text-white hover:bg-blue-700", outline: "border border-slate-200 bg-white text-slate-700 hover:bg-slate-50" } },
  defaultVariants: { variant: "default" },
});

export function Button({ className, variant, asChild = false, type, ...props }: React.ComponentProps<"button"> & VariantProps<typeof variants> & { asChild?: boolean }) {
  const Component = asChild ? Slot : "button";
  return <Component type={asChild ? undefined : (type ?? "button")} className={cn(variants({ variant }), className)} {...props} />;
}
