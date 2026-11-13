import type { McpServerDetailInfo } from '@/types/mcp';
import client from './client';
import { HUB_SYSTEM_NAMESPACE, validHubId } from '@/lib/hub-skill-utils';
import type { HubShareState, HubShareResult, HubTransferResult } from '@/types/hub-skill';

export type HubResourceType = 'mcp' | 'agent' | 'prompt' | 'agentspec';
export const hubResourceConfig = {
  mcp: { base: 'v3/console/ai/mcp', identity: 'mcpName', responseIdentity: 'name', path: '/mcpServerManagement', title: 'mcp.title' },
  agent: { base: 'v3/console/ai/agents', identity: 'agentName', responseIdentity: 'agentName', path: '/agentManagement', title: 'agent.title' },
  prompt: { base: 'v3/console/ai/prompt', identity: 'promptKey', responseIdentity: 'promptKey', path: '/promptManagement', title: 'prompt.title' },
  agentspec: { base: 'v3/console/ai/agentspecs', identity: 'agentSpecName', responseIdentity: 'name', path: '/agentspec', title: 'agentSpec.title' },
} as const;

export interface SharedResource {
  hubEntryId: string;
  resourceType: HubResourceType;
  resourceName: string;
  version: string;
  protocol?: string;
  frontProtocol?: string;
  sourceDisplayName?: string;
  sourceReadable?: boolean;
  sourceNamespaceId?: string;
  sourceName?: string;
  author?: string;
  downloadCount?: number;
  updateTime?: number;
  description?: string;
  tags?: string[];
  versionLabels?: string[];
  provider?: string;
  mcp?: McpServerDetailInfo;
  metadata?: Record<string, unknown>;
}
export interface SharedResourcePage { totalCount: number; pageItems: SharedResource[] }

export interface ResourceImport {
  hubEntryId: string;
  targetNamespaceId: string;
  targetName: string;
  targetVersion: string;
  mcpRemoteServerConfig?: Record<string, unknown>;
}
export type ResourceImportResult = HubTransferResult & { resourceType: HubResourceType };

export function resourceListPath(type: HubResourceType) { return `${hubResourceConfig[type].path}/shared/list`; }
export function resourceDetailPath(type: HubResourceType, id: string) {
  if (!validHubId(id)) throw new Error('Invalid Hub entry identity');
  return `${hubResourceConfig[type].path}/shared/detail/${id}`;
}
export function sourceResourcePath(detail: SharedResource): string | null {
  if (detail.sourceReadable !== true || !detail.sourceNamespaceId || !detail.sourceName ||
      detail.sourceNamespaceId === HUB_SYSTEM_NAMESPACE || !detail.version.trim()) return null;
  return ordinaryResourcePath(detail.resourceType, detail.sourceNamespaceId, detail.sourceName, detail.version);
}
export function resourceTypeForPath(path: string): HubResourceType {
  const entry = (Object.keys(hubResourceConfig) as HubResourceType[])
    .find(type => path === hubResourceConfig[type].path || path.startsWith(`${hubResourceConfig[type].path}/`));
  if (!entry) throw new Error('Unknown resource management page');
  return entry;
}
export function ordinaryResourcePath(type: HubResourceType, namespace: string, name: string, version?: string) {
  const config = hubResourceConfig[type];
  const params = new URLSearchParams({ namespaceId: namespace });
  if (version) params.set('version', version);
  if (type === 'agentspec') return `/agentspec/${encodeURIComponent(name)}?${params}`;
  // Agent's Console route uses name, while its HTTP API uses agentName.
  params.set(type === 'agent' ? 'name' : config.identity, name);
  return `${type === 'mcp' ? '/mcpServerDetail' : type === 'agent' ? '/agentDetail' : '/promptDetail'}?${params}`;
}

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid resource response');
  return value as Record<string, unknown>;
}
async function success<T>(request: Promise<unknown>, allowNull = false): Promise<T> {
  const result = object(await request);
  if (result.code !== 0) throw new Error(typeof result.message === 'string' ? result.message : 'Resource request failed');
  if (result.data == null && !allowNull) throw new Error('Missing resource response');
  return result.data as T;
}
function params(type: HubResourceType, id: string) {
  if (!validHubId(id)) throw new Error('Invalid Hub entry identity');
  return { namespaceId: HUB_SYSTEM_NAMESPACE, [hubResourceConfig[type].identity]: id };
}
function sharedTags(value: unknown): string[] {
  if (typeof value === 'string') {
    try { value = JSON.parse(value); } catch { return []; }
  }
  return Array.isArray(value) ? value.filter((tag): tag is string => typeof tag === 'string') : [];
}

export function sharedResourceSummary(type: HubResourceType, value: unknown): SharedResource {
  const row = object(value);
  const hub = object(row.hub);
  if (!validHubId(hub.hubEntryId) || hub.resourceType !== type ||
      typeof hub.resourceName !== 'string' || !hub.resourceName.trim() ||
      typeof hub.version !== 'string' || !hub.version.trim() ||
      row[hubResourceConfig[type].responseIdentity] !== hub.hubEntryId ||
      (row.namespaceId != null && row.namespaceId !== HUB_SYSTEM_NAMESPACE)) {
    throw new Error('Mismatched shared resource identity');
  }
  return {
    hubEntryId: hub.hubEntryId, resourceType: type, resourceName: hub.resourceName, version: hub.version,
    protocol: type === 'mcp' && typeof row.protocol === 'string' ? row.protocol : undefined,
    frontProtocol: type === 'mcp' && typeof row.frontProtocol === 'string' ? row.frontProtocol : undefined,
    tags: sharedTags(row.bizTags ?? row.tags),
    versionLabels: Object.entries((row.labels ?? (row.versionInfo as { labels?: Record<string, string> } | undefined)?.labels ?? {}) as Record<string, unknown>)
      .filter(([, version]) => version === hub.version).map(([label]) => label),
    provider: row.provider && typeof row.provider === 'object' && typeof (row.provider as { name?: unknown }).name === 'string'
      ? (row.provider as { name: string }).name : undefined,
    description: typeof row.description === 'string' ? row.description : undefined,
    sourceReadable: hub.sourceReadable === true,
    sourceNamespaceId: hub.sourceReadable === true && typeof hub.sourceNamespaceId === 'string' ? hub.sourceNamespaceId : undefined,
    sourceName: hub.sourceReadable === true && typeof hub.sourceName === 'string' ? hub.sourceName : undefined,
    sourceDisplayName: typeof hub.sourceDisplayName === 'string' ? hub.sourceDisplayName : undefined,
    author: typeof hub.author === 'string' ? hub.author : undefined,
    downloadCount: typeof hub.downloadCount === 'number' ? hub.downloadCount : 0,
    updateTime: typeof hub.updateTime === 'number' ? hub.updateTime :
      typeof row.updateTime === 'number' ? row.updateTime : typeof row.gmtModified === 'number' ? row.gmtModified : undefined,
  };
}

const management = (type: HubResourceType) => `v3/console/ai/hub/${type}`;
const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
const form = (values: Record<string, string | undefined>) => {
  const result = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => { if (value != null) result.set(key, value); });
  return result;
};
export const hubResourceApi = {
  list: async (type: HubResourceType, query: { name: string; pageNo: number; pageSize: number; orderBy?: string }) => {
    const page = await success<Record<string, unknown>>(client.get(`${hubResourceConfig[type].base}/list`, { params: {
      namespaceId: HUB_SYSTEM_NAMESPACE, [hubResourceConfig[type].identity]: query.name,
      search: 'blur', pageNo: query.pageNo, pageSize: query.pageSize, orderBy: query.orderBy,
    } }));
    if (!Number.isSafeInteger(page.totalCount) || (page.totalCount as number) < 0 || !Array.isArray(page.pageItems)) {
      throw new Error('Invalid shared directory page');
    }
    const items = page.pageItems.map(row => sharedResourceSummary(type, row));
    if (new Set(items.map(row => row.hubEntryId)).size !== items.length) throw new Error('Duplicate shared resource identity');
    return { totalCount: page.totalCount as number, pageItems: items };
  },
  detail: async (type: HubResourceType, id: string) => {
    const raw = object(await success(client.get(`${hubResourceConfig[type].base}${type === 'prompt' ? '/governance' : ''}`,
      { params: params(type, id) })));
    const row = type === 'agent' ? object(raw.agent) : raw;
    const detail = sharedResourceSummary(type, row);
    detail.metadata = row;
    if (detail.hubEntryId !== id) throw new Error('Mismatched shared detail identity');
    if (type === 'mcp') detail.mcp = { ...row, name: detail.resourceName, version: detail.version } as unknown as McpServerDetailInfo;
    return detail;
  },
  content: async (type: HubResourceType, id: string, version: string) => {
    if (!version.trim()) throw new Error('Missing pinned resource version');
    const row = object(await success(client.get(`${hubResourceConfig[type].base}/version`,
      { params: { ...params(type, id), version } })));
    const identity = type === 'mcp' ? row.mcpName : row[hubResourceConfig[type].responseIdentity];
    if (identity !== id || (row.namespaceId != null && row.namespaceId !== HUB_SYSTEM_NAMESPACE) ||
        (row.version != null && row.version !== version)) throw new Error('Mismatched shared content identity');
    if (row.hub != null && object(row.hub).version !== version) throw new Error('Mismatched pinned resource version');
    return row;
  },
  downloadPrompt: async (id: string, version: string): Promise<Blob> => {
    if (!version.trim()) throw new Error('Missing pinned resource version');
    const blob = await client.get('v3/console/ai/prompt/version/download', {
      params: { ...params('prompt', id), version }, responseType: 'blob',
    }) as unknown;
    if (!(blob instanceof Blob) || !blob.type.toLowerCase().startsWith('text/markdown')) throw new Error('Invalid Prompt download response');
    return blob;
  },
  sourceState: (type: HubResourceType, namespace: string, name: string) =>
    success<HubShareState | null>(client.get(`${management(type)}/source-share`,
      { params: { sourceNamespaceId: namespace, sourceName: name } }), true),
  share: (type: HubResourceType, namespace: string, name: string, version: string) =>
    success<HubShareResult>(client.post(`${management(type)}/share`, form({ sourceNamespaceId: namespace, sourceName: name,
      sourceVersion: version }), { headers })),
  adjust: (type: HubResourceType, state: HubShareState, version: string) =>
    success<HubShareResult>(client.post(`${management(type)}/adjust`, form({ hubEntryId: state.hubEntryId,
      expectedMetaVersion: state.metaVersion, targetVersion: version }), { headers })),
  unshare: (type: HubResourceType, state: HubShareState) =>
    success<HubShareResult>(client.post(`${management(type)}/unshare`, form({ hubEntryId: state.hubEntryId,
      expectedMetaVersion: state.metaVersion }), { headers })),
  governance: (type: HubResourceType, id: string) =>
    success<HubShareState>(client.get(`${management(type)}/governance-state`, { params: { hubEntryId: id } })),
  forceUnshare: (type: HubResourceType, state: HubShareState, reason: string) =>
    success<HubShareResult>(client.post(`${management(type)}/force-unshare`, form({ hubEntryId: state.hubEntryId,
      expectedMetaVersion: state.metaVersion, reason }), { headers })),
  import: async (type: HubResourceType, input: ResourceImport) => {
    if (!validHubId(input.hubEntryId)) throw new Error('Invalid Hub import identity');
    const result = await success<ResourceImportResult>(client.post(`${management(type)}/import`, form({
      hubEntryId: input.hubEntryId, targetNamespaceId: input.targetNamespaceId,
      targetName: input.targetName, targetVersion: input.targetVersion,
      mcpRemoteServerConfig: input.mcpRemoteServerConfig == null ? undefined : JSON.stringify(input.mcpRemoteServerConfig),
    }), { headers }));
    if (result.resourceType !== type || result.targetNamespaceId !== input.targetNamespaceId ||
        result.targetName !== input.targetName || result.draftVersion !== input.targetVersion ||
        result.status !== 'draft' || !validHubId(result.targetResourceId) ||
        result.hubEntryId !== input.hubEntryId) throw new Error('Mismatched imported resource identity');
    return result;
  },

};

export function resourceErrorMessage(error: unknown, fallback: string): string {
  const response = (error as { response?: { data?: { message?: unknown; data?: unknown } } })?.response?.data;
  if (typeof response?.data === 'string' && response.data.trim()) return response.data;
  if (typeof response?.message === 'string' && response.message.trim()) return response.message;
  return error instanceof Error && error.message ? error.message : fallback;
}
