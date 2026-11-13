export type CopilotConnectionSource = 'DEPLOYMENT' | 'LEGACY';

export type CopilotCredentialSource = 'ENVIRONMENT' | 'DEPLOYMENT' | 'CONSOLE';

export type CopilotSelectionSource = 'DEPLOYMENT' | 'STORED' | 'NONE';

export interface CopilotConnectionInfo {
  name: string;
  providerId?: string;
  displayName?: string;
  model?: string;
  source?: CopilotConnectionSource;
  enabled?: boolean;
  available?: boolean;
  unavailableReason?: string;
  apiKeyConfigured?: boolean;
  actualSource?: CopilotCredentialSource | null;
}

export interface CopilotConfigView {
  apiKey?: string;
  model?: string;
  studioUrl?: string;
  studioProject?: string;
  selectedConnection?: string;
  effectiveSelectedConnection?: string;
  selectedConnectionSource?: CopilotSelectionSource;
  activeConnection?: string;
  connections?: CopilotConnectionInfo[];
}

export interface CopilotConfigPayload {
  selectedConnection: string;
  apiKey?: string;
  model?: string;
}

interface CopilotConfigPayloadInput {
  selectedConnection: string;
  connections?: CopilotConnectionInfo[];
  apiKey?: string;
  model?: string;
}

export function hasLegacyConnection(connections?: CopilotConnectionInfo[]): boolean {
  return (connections ?? []).some(
    (connection) => (connection.source ?? '').toUpperCase() === 'LEGACY'
  );
}

export function isConnectionUsable(connection: CopilotConnectionInfo): boolean {
  return connection.enabled !== false && connection.available !== false;
}

export function isSelectionOverriddenByDeployment(config: CopilotConfigView): boolean {
  return (config.selectedConnectionSource ?? 'NONE').toUpperCase() === 'DEPLOYMENT';
}

export function buildCopilotConfigPayload({
  selectedConnection,
  connections,
  apiKey,
  model,
}: CopilotConfigPayloadInput): CopilotConfigPayload {
  const payload: CopilotConfigPayload = { selectedConnection: selectedConnection.trim() };
  // Without a legacy connection the backend keeps the stored key/model, so submitting them
  // could overwrite a value the operator can no longer see.
  if (hasLegacyConnection(connections)) {
    if (apiKey !== undefined) {
      payload.apiKey = apiKey;
    }
    if (model !== undefined) {
      payload.model = model;
    }
  }
  return payload;
}
