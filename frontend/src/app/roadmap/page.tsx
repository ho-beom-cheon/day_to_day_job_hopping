import { SessionGate } from "@/features/session/session-gate";
import { RoadmapScreen } from "@/features/roadmap/roadmap-screen";

export default function RoadmapPage() { return <SessionGate><RoadmapScreen /></SessionGate>; }
