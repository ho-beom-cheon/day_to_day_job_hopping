import { SessionGate } from "@/features/session/session-gate";
import { CodingScreen } from "@/features/coding/coding-screen";

export default function CodingLogPage() {
  return <SessionGate><CodingScreen/></SessionGate>;
}
