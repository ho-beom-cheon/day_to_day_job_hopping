import { SessionGate } from "@/features/session/session-gate";
import { ProgressScreen } from "@/features/progress/progress-screen";
export default function ProgressPage(){return <SessionGate><ProgressScreen/></SessionGate>}
