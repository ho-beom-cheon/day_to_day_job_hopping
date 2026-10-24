import { SessionGate } from "@/features/session/session-gate";
import { SettingsScreen } from "@/features/settings/settings-screen";

export default function SettingsPage() {
  return <SessionGate><SettingsScreen /></SessionGate>;
}
