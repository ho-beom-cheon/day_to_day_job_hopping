"use client";

import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CircleHelp, X } from "lucide-react";

export type HelpItem = { term: string; description: string };
export type HelpContent = { summary?: string; text?: string; items?: HelpItem[]; note?: string };
type HelpHintProps = HelpContent & { label: string; align?: "left" | "right"; inline?: boolean };

export function HelpHint({ label, summary, text, items, note, align = "left", inline = false }: HelpHintProps) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const id = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const place = () => {
      const trigger = triggerRef.current?.getBoundingClientRect();
      const panel = panelRef.current;
      if (!trigger || !panel) return;
      const width = panel.offsetWidth;
      const height = panel.offsetHeight;
      if (window.innerWidth < 640) {
        setPosition({ left: 16, top: Math.max(16, window.innerHeight - height - 80) });
        return;
      }
      const rightSide = trigger.right + 12;
      const leftSide = trigger.left - width - 12;
      const preferred = align === "right" ? leftSide : rightSide;
      const alternate = align === "right" ? rightSide : leftSide;
      const fits = (left: number) => left >= 16 && left + width <= window.innerWidth - 16;
      const left = fits(preferred) ? preferred : fits(alternate) ? alternate : Math.max(16, Math.min(preferred, window.innerWidth - width - 16));
      setPosition({ left, top: Math.max(16, Math.min(trigger.top - 12, window.innerHeight - height - 16)) });
    };
    const closeOutside = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!triggerRef.current?.contains(target) && !panelRef.current?.contains(target)) setOpen(false);
    };
    const closeEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    if (!inline) place();
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeEscape);
    if (!inline) {
      window.addEventListener("scroll", place, true);
      window.addEventListener("resize", place);
    }
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeEscape);
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [open, align, inline]);

  const content = <>
    <div className="flex items-start justify-between gap-3 border-b border-slate-100 pb-3 dark:border-slate-600">
      <div><p className="text-[11px] font-extrabold tracking-wide text-blue-600 dark:text-blue-300">도움말</p><h3 className="mt-1 text-base font-extrabold leading-6 text-slate-950 dark:text-white">{label}</h3></div>
      <button type="button" aria-label={`${label} 도움말 닫기`} onClick={() => { setOpen(false); triggerRef.current?.focus(); }}
        className="grid size-8 shrink-0 place-items-center rounded-lg text-slate-500 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-600 dark:text-slate-300 dark:hover:bg-slate-700"><X size={18} aria-hidden="true" /></button>
    </div>
    <p className="pt-3 text-sm leading-6 text-slate-700 dark:text-slate-200">{summary ?? text}</p>
    {items && items.length > 0 && <ul className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-100 dark:divide-slate-600 dark:border-slate-600">{items.map(item =>
      <li key={item.term} className="grid grid-cols-[78px_minmax(0,1fr)] gap-2 px-3 py-2.5 text-[13px] leading-5 sm:grid-cols-[88px_minmax(0,1fr)]">
        <strong className="font-extrabold text-blue-700 dark:text-blue-300">{item.term}</strong><span className="text-slate-700 dark:text-slate-200">{item.description}</span>
      </li>)}</ul>}
    {note && <p className="mt-3 rounded-xl bg-blue-50 px-3 py-2.5 text-xs font-semibold leading-5 text-blue-900 dark:bg-blue-500/15 dark:text-blue-100">{note}</p>}
  </>;

  return <>
    <button ref={triggerRef} type="button" aria-label={`${label} 도움말`} aria-expanded={open} aria-controls={open ? id : undefined}
      onClick={() => { setPosition(null); setOpen(value => !value); }}
      className="inline-grid size-7 shrink-0 place-items-center rounded-full text-blue-600 transition hover:bg-blue-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 dark:text-blue-300 dark:hover:bg-blue-900/40">
      <CircleHelp size={19} aria-hidden="true" />
    </button>
    {open && inline && <div className="basis-full"><div ref={panelRef} id={id} role="note" aria-label={`${label} 도움말`}
      className="w-full max-w-[680px] rounded-2xl border border-blue-200 bg-white p-4 text-left shadow-sm dark:border-slate-600 dark:bg-[#17243a]">{content}</div></div>}
    {open && !inline && createPortal(<>
      <div aria-hidden="true" className="fixed inset-0 z-[70] bg-slate-950/45 sm:hidden" />
      <div ref={panelRef} id={id} role="note" aria-label={`${label} 도움말`} style={{ ...position, visibility: position ? "visible" : "hidden" }}
        className="fixed z-[80] max-h-[min(65vh,420px)] w-[360px] max-w-[calc(100vw-32px)] overflow-y-auto rounded-2xl border border-blue-200 bg-white p-4 text-left shadow-2xl shadow-slate-950/20 dark:border-slate-600 dark:bg-[#17243a] dark:shadow-black/40 sm:p-5">
        {content}
      </div>
    </>, document.body)}
  </>;
}
