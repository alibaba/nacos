export type AgentResourceStatus = 'enable' | 'disable';

export type AgentVersionStatus = 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';

export type AgentScope = 'PUBLIC' | 'PRIVATE';

export type EndpointSource = 'RUNTIME' | 'DECLARED';

export type RuntimeEndpointState = 'AVAILABLE' | 'DISABLED' | 'UNHEALTHY';

export interface AgentProvider {
  name: string;
  url?: string;
}

export interface AgentVersionInfo {
  editingVersion?: string;
  reviewingVersion?: string;
  onlineVersions?: AgentVersionSummary[];
  labels?: Record<string, string>;
}

export interface AgentSummary {
  namespaceId: string;
  agentName: string;
  displayName?: string;
  description?: string;
  iconUrl?: string;
  provider?: AgentProvider;
  tags?: string[];
  extensions?: Record<string, unknown>;
  status: AgentResourceStatus;
  owner?: string;
  scope?: AgentScope;
  versionInfo?: AgentVersionInfo;
  metaVersion?: number;
  createTime?: number;
  updateTime?: number;
}

export interface AgentEndpoint {
  uri: string;
  transport: string;
  priority?: number;
  weight?: number;
  metadata?: Record<string, string>;
  healthy?: boolean;
  bindings?: RuntimeVersionBinding[];
  enabled?: boolean;
  state?: RuntimeEndpointState;
}

export interface AgentCallInterface {
  protocol: string;
  protocolVersion?: string;
  descriptorMediaType?: string;
  nativeDescriptor?: unknown;
  endpointSourceOrder?: EndpointSource[];
  endpointSets?: EndpointSet[];
}

export interface AgentVersionSummary {
  version: string;
  labels?: string[];
  protocols?: string[];
  status?: AgentVersionStatus;
  publishPipelineInfo?: string;
  author?: string;
  changeDescription?: string;
  contentDigest?: string;
  createTime?: number;
  updateTime?: number;
}

export interface AgentVersionDetail extends AgentVersionSummary {
  status: AgentVersionStatus;
  namespaceId: string;
  agentName: string;
  callInterfaces: AgentCallInterface[];
}

export interface AgentPage<T> {
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
  pageItems: T[];
}

export interface AgentOverview {
  agent: AgentSummary;
  versionPage: AgentPage<AgentVersionSummary>;
}

export interface RuntimeVersionBinding {
  runtimeVersion: string;
  versionRange: string;
}

export interface EndpointSet {
  source: EndpointSource;
  sourceRevision?: string;
  endpoints: AgentEndpoint[];
  lastUpdatedTime?: number;
}

export interface RuntimeEndpointSnapshot {
  namespaceId: string;
  agentName: string;
  version?: string;
  callInterface: AgentCallInterface;
}

export interface NamingServiceRef {
  namespaceId: string;
  groupName: string;
  serviceName: string;
}

export interface ConsoleRuntimeEndpointView {
  runtimeEndpointSnapshot: RuntimeEndpointSnapshot;
  namingServiceRef: NamingServiceRef;
}

export interface AgentListParams {
  namespaceId?: string;
  agentName?: string;
  bizTag?: string;
  scope?: AgentScope;
  owner?: string;
  orderBy?: 'download_count';
  pageNo?: number;
  pageSize?: number;
}

export interface AgentDraftCreateData {
  namespaceId?: string;
  agentName: string;
  version: string;
  displayName?: string;
  description?: string;
  iconUrl?: string;
  provider?: string;
  tags?: string;
  extensions?: string;
  callInterfaces?: string;
  author?: string;
  changeDescription?: string;
  basedOnVersion?: string;
}

export interface AgentDraftUpdateData {
  namespaceId?: string;
  agentName: string;
  version: string;
  callInterfaces: string;
  changeDescription?: string;
}

export interface AgentMetadataUpdateData {
  namespaceId?: string;
  agentName: string;
  displayName?: string;
  description?: string;
  iconUrl?: string;
  provider?: string;
  tags?: string;
  extensions?: string;
  status: AgentResourceStatus;
}

export interface AgentVersionActionData {
  namespaceId?: string;
  agentName: string;
  version: string;
}
