import type { ReactNode } from "react";import { SessionGate } from "@/features/session/session-gate";
export default function TestsLayout({children}:{children:ReactNode}){return <SessionGate>{children}</SessionGate>}
