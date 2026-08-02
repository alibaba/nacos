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
  onlineCnt?: number;
  labels?: Record<string, string>;
}

export interface AgentVersionCatalogEntry {
  version: string;
  labels?: string[];
  protocols?: string[];
}

export interface AgentVersionCatalog {
  latestVersion?: string;
  onlineVersions?: AgentVersionCatalogEntry[];
}

export interface AgentMetadata {
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
  versionCatalog?: AgentVersionCatalog;
  metaVersion?: number;
  createTime?: number;
  updateTime?: number;
}

export type AgentSummary = Omit<AgentMetadata, 'extensions'>;

export interface AgentEndpoint {
  uri: string;
  transport: string;
  priority?: number;
  weight?: number;
  metadata?: Record<string, string>;
  healthy?: boolean;
}

export interface AgentCallInterface {
  protocol: string;
  protocolVersion?: string;
  descriptorMediaType: string;
  nativeDescriptor: unknown;
  endpointSourceOrder: EndpointSource[];
  declaredEndpoints?: AgentEndpoint[];
}

export interface AgentVersionSummary {
  version: string;
  status: AgentVersionStatus;
  author?: string;
  changeDescription?: string;
  contentDigest?: string;
  createTime?: number;
  updateTime?: number;
}

export interface AgentVersionDetail extends AgentVersionSummary {
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
  agent: AgentMetadata;
  versionPage: AgentPage<AgentVersionSummary>;
}

export interface RuntimeVersionBinding {
  runtimeVersion: string;
  versionRange: string;
}

export interface RuntimeEndpointSnapshotItem {
  endpoint: AgentEndpoint;
  bindings: RuntimeVersionBinding[];
  state: RuntimeEndpointState;
  enabled: boolean;
  healthy: boolean;
  lastUpdatedTime: number;
}

export interface RuntimeEndpointSnapshot {
  namespaceId: string;
  agentName: string;
  protocol: string;
  version?: string;
  items: RuntimeEndpointSnapshotItem[];
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
