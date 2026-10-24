import { SessionGate } from "@/features/session/session-gate";
import { CareerScreen } from "@/features/career/career-screen";

export default function CareerPage() {
  return <SessionGate><CareerScreen/></SessionGate>;
}
