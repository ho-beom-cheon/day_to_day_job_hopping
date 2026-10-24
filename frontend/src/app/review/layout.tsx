import type { ReactNode } from "react";
import { SessionGate } from "@/features/session/session-gate";
export default function ReviewLayout({children}:{children:ReactNode}){return <SessionGate>{children}</SessionGate>}
