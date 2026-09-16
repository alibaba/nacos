export type AgentResourceStatus = 'enable' | 'disable';

export type AgentVersionStatus = 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';

export type AgentScope = 'PUBLIC' | 'PRIVATE';

export type EndpointSource = 'RUNTIME' | 'DECLARED';

export type RuntimeEndpointState = 'AVAILABLE' | 'DISABLED' | 'UNHEALTHY';

export interface AgentProvider {
  name: string;
  url?: string | null;
}

export interface AgentVersionInfo {
  editingVersion?: string | null;
  reviewingVersion?: string | null;
  onlineVersions?: AgentVersionSummary[] | null;
  labels?: Record<string, string> | null;
}

export interface AgentSummary {
  namespaceId: string;
  agentName: string;
  displayName?: string | null;
  description?: string | null;
  iconUrl?: string | null;
  provider?: AgentProvider | null;
  tags?: string[] | null;
  extensions?: Record<string, unknown> | null;
  status: AgentResourceStatus;
  owner?: string | null;
  scope?: AgentScope | null;
  versionInfo?: AgentVersionInfo | null;
  metaVersion?: number | null;
  createTime?: number | null;
  updateTime?: number | null;
}

export interface AgentEndpoint {
  uri: string;
  transport: string;
  /** Lower values have higher priority; omitted input defaults to zero. */
  priority?: number;
  /** Omitted input defaults to one. */
  weight?: number;
  metadata?: Record<string, string> | null;
  /** Omitted input defaults to true; runtime reads reflect current health. */
  healthy?: boolean;
  bindings?: RuntimeVersionBinding[] | null;
  /** Maintained by Nacos; defaults to true. */
  enabled?: boolean;
  state?: RuntimeEndpointState | null;
}

export interface AgentCallInterface {
  protocol: string;
  protocolVersion?: string | null;
  descriptorMediaType?: string | null;
  nativeDescriptor?: unknown;
  endpointSourceOrder?: EndpointSource[] | null;
  endpointSets?: EndpointSet[] | null;
}

export interface AgentVersionSummary {
  version: string;
  labels?: string[] | null;
  protocols?: string[] | null;
  status?: AgentVersionStatus | null;
  publishPipelineInfo?: string | null;
  author?: string | null;
  changeDescription?: string | null;
  contentDigest?: string | null;
  createTime?: number | null;
  updateTime?: number | null;
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
  sourceRevision?: string | null;
  endpoints: AgentEndpoint[];
  lastUpdatedTime?: number | null;
}

export interface RuntimeEndpointSnapshot {
  namespaceId: string;
  agentName: string;
  version?: string | null;
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
