import { SessionGate } from "@/features/session/session-gate";
import { AiUsageScreen } from "@/features/ai-usage/ai-usage-screen";

export default function AiUsagePage() {
  return <SessionGate><AiUsageScreen/></SessionGate>;
}
