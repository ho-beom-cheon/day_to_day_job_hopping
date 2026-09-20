import type { ReactNode } from "react";
import { SessionGate } from "@/features/session/session-gate";

export default function WorkspaceLayout({ children }: { children: ReactNode }) { return <SessionGate>{children}</SessionGate>; }
