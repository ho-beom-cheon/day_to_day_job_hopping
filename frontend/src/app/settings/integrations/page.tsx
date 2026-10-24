import { SessionGate } from "@/features/session/session-gate";
import { IntegrationSettingsScreen } from "@/features/integrations/integration-settings-screen";

export default function IntegrationsPage() {
  return <SessionGate><IntegrationSettingsScreen /></SessionGate>;
}
