// ===== Agent (A2A) Types =====

export type AgentSearchMode = 'accurate' | 'blur';

// ===== Skill =====

export interface AgentSkill {
  id?: string;
  name: string;
  description?: string;
  tags?: string[];
  inputModes?: string[];
  outputModes?: string[];
  examples?: string[];
}

// ===== Capabilities =====

export interface AgentCapabilities {
  streaming?: boolean;
  pushNotifications?: boolean;
  stateTransitionHistory?: boolean;
  extendedAgentCard?: boolean;
}

// ===== Provider =====

export interface AgentProvider {
  organization?: string;
  url?: string;
}

// ===== Additional Interface =====

export interface AgentAdditionalInterface {
  name?: string;
  url?: string;
  description?: string;
  transport?: string;
  uri?: string;
  protocolBinding?: string;
  protocolVersion?: string;
  tenant?: string;
}

export interface AgentInterface {
  url?: string;
  transport?: string;
  protocolBinding?: string;
  protocolVersion?: string;
  tenant?: string;
}

// ===== Version =====

export interface AgentVersionDetail {
  version: string;
  latest?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// ===== Agent Basic Info (list item — matches AgentCardVersionInfo) =====

export interface AgentBasicInfo {
  name: string;
  description?: string;
  version?: string;
  latestPublishedVersion?: string;
  iconUrl?: string;
  protocolVersion?: string;
  capabilities?: AgentCapabilities;
  skills?: AgentSkill[];
  versionDetails?: AgentVersionDetail[];
  registrationType?: string;
}

// ===== Agent Detail Info (matches AgentCardDetailInfo) =====

export interface AgentDetailInfo extends AgentBasicInfo {
  url?: string;
  preferredTransport?: string;
  defaultInputModes?: string[];
  defaultOutputModes?: string[];
  provider?: AgentProvider;
  documentationUrl?: string;
  security?: unknown;
  securitySchemes?: unknown;
  supportedInterfaces?: AgentInterface[];
  additionalInterfaces?: AgentAdditionalInterface[];
  supportsAuthenticatedExtendedCard?: boolean;
  signatures?: Array<Record<string, unknown>>;
  latestVersion?: boolean;
}

// ===== List Request/Response =====

export interface AgentListParams {
  agentName?: string;
  namespaceId?: string;
  search?: AgentSearchMode;
  pageNo?: number;
  pageSize?: number;
}

export interface AgentListResponse {
  totalCount: number;
  pageItems: AgentBasicInfo[];
}

// ===== Create/Update =====

export interface AgentCreateData {
  namespaceId?: string;
  agentName: string;
  version: string;
  registrationType?: string;
  agentCard: string; // JSON string of AgentDetailInfo
}

export interface AgentUpdateData {
  namespaceId?: string;
  agentName: string;
  version: string;
  agentCard: string;
  setAsLatest?: boolean;
}
