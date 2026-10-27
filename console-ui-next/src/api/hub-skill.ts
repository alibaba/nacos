import type { AxiosRequestConfig } from 'axios';
import { HUB_SYSTEM_NAMESPACE, validHubId } from '@/lib/hub-skill-utils';
import { parseBizTags } from '@/types/skill';
import type { SkillListItem, SkillAdminDetail, SkillDocument } from '@/types/skill';
import client from './client';
import type { ApiResult } from './types';
import type {
  HubSkillSummary, HubSkillPage, HubSkillDetail, HubSkillContent, HubShareState,
  HubShareResult, HubTransferResult,
} from '@/types/hub-skill';

const BASE = 'v3/console/ai/hub/skills';
const SKILLS = 'v3/console/ai/skills';

/** Shared discovery uses the ordinary Skill response model and provenance. */
type SharedSkillSummary = SkillListItem & {
  discoverySource: 'HUB';
  hubEntryId: string;
  displayName: string;
  sourceDisplayName?: string | null;
  version: string;
};
type SharedSkillDetail = SkillAdminDetail & SharedSkillSummary & {
  sourceReadable?: boolean;
  sourceNamespaceId?: string | null;
  sourceName?: string | null;
};

function sharedParams(hubEntryId: string) {
  if (!validHubId(hubEntryId)) throw new Error('Invalid Hub entry ID');
  return { namespaceId: HUB_SYSTEM_NAMESPACE, skillName: hubEntryId };
}

function toSharedSummary(skill: SharedSkillSummary): HubSkillSummary {
  if (skill.namespaceId !== HUB_SYSTEM_NAMESPACE || skill.discoverySource !== 'HUB' ||
      !validHubId(skill.hubEntryId) || skill.name !== skill.hubEntryId ||
      !skill.displayName?.trim() || !skill.version?.trim()) {
    throw new Error('Invalid shared Skill identity');
  }
  return {
    hubEntryId: skill.hubEntryId, displayName: skill.displayName,
    sourceDisplayName: skill.sourceDisplayName, author: skill.owner,
    description: skill.description, sharedVersion: skill.version,
    tags: parseBizTags(skill.bizTags), sharedAt: skill.updateTime,
    downloadCount: skill.downloadCount, origin: 'HUB',
  };
}

/* Axios interceptor unwraps HTTP response but not Nacos Result.code. */
async function requireSuccess<T>(request: ApiResult<T>, allowNull = false): Promise<{ data: T }> {
  const response = await request;
  const result = response as { code?: number; message?: string; data?: T };
  if (result.code !== 0) {
    const failure = new Error(result.message || 'Hub request failed') as Error & { code?: number };
    failure.code = result.code;
    throw failure;
  }
  if (result.data == null && !allowNull) throw new Error('Missing Hub response data');
  return { data: (result.data ?? null) as T };
}

function validateShareState(state: HubShareState): void {
  if (!validHubId(state.hubEntryId) || !validHubId(state.metaVersion) ||
      !state.sharedVersion?.trim() || typeof state.active !== 'boolean') {
    throw new Error('Invalid Hub share state');
  }
}

export const hubSkillApi = {
  list: async (params: { query?: string; bizTag?: string; orderBy?: 'download_count'; pageNo: number; pageSize: number }) => {
    const response = await requireSuccess(client.get(`${SKILLS}/list`, { params: {
      namespaceId: HUB_SYSTEM_NAMESPACE, skillName: params.query, search: 'blur',
      bizTag: params.bizTag, pageNo: params.pageNo, pageSize: params.pageSize,
      orderBy: params.orderBy,
    } }) as ApiResult<{ totalCount: number; pageItems: SharedSkillSummary[] }>);
    return { data: {
      totalCount: response.data.totalCount,
      pageItems: response.data.pageItems.map(toSharedSummary),
    } satisfies HubSkillPage };
  },
  detail: async (hubEntryId: string) => {
    const { data } = await requireSuccess(client.get(SKILLS, {
      params: sharedParams(hubEntryId),
    }) as ApiResult<SharedSkillDetail>);
    const summary = toSharedSummary(data);
    if (summary.hubEntryId !== hubEntryId || data.versions?.length !== 1 ||
        data.versions[0].version !== summary.sharedVersion) {
      throw new Error('Mismatched shared Skill version');
    }
    const version = data.versions[0];
    return { data: {
      ...summary, versionStatus: version.status, versionDownloadCount: version.downloadCount,
      commitMsg: version.commitMsg,
      versionLabels: Object.keys(data.labels ?? {}).filter(label => data.labels[label] === summary.sharedVersion),
      sourceReadable: data.sourceReadable === true,
      sourceNamespaceId: data.sourceNamespaceId, sourceName: data.sourceName,
    } satisfies HubSkillDetail };
  },
  content: async (hubEntryId: string, sharedVersion: string, displayName: string) => {
    if (!sharedVersion.trim()) throw new Error('Missing shared Skill version');
    const { data } = await requireSuccess(client.get(`${SKILLS}/version`, {
      params: { ...sharedParams(hubEntryId), version: sharedVersion },
    }) as ApiResult<SkillDocument>);
    if (data.namespaceId !== HUB_SYSTEM_NAMESPACE || data.name !== hubEntryId) {
      throw new Error('Mismatched shared Skill content');
    }
    return { data: {
      hubEntryId, displayName, sharedVersion, skillMd: data.skillMd, resource: data.resource,
    } satisfies HubSkillContent };
  },
  /** Compare the returned package identity with the page snapshot before saving. */
  download: async (hubEntryId: string, sharedVersion: string): Promise<Blob> => {
    if (!sharedVersion.trim()) throw new Error('Missing shared Skill version');
    const response = await client.get(`${SKILLS}/version/download`, {
      params: { ...sharedParams(hubEntryId), version: sharedVersion }, responseType: 'blob', timeout: 60000,
      transformResponse: [(data, headers) => ({
        blob: data,
        hubEntryId: headers['x-nacos-hub-entry-id'],
        sharedVersion: headers['x-nacos-skill-resolved-version'],
      })],
    }) as unknown as { blob: Blob; hubEntryId?: string; sharedVersion?: string };
    if (response.blob instanceof Blob && response.blob.type.includes('json')) {
      const result = JSON.parse(await response.blob.text()) as { code?: number; message?: string };
      const error = new Error(result.message || 'Hub download failed') as Error & { code?: number };
      error.code = result.code;
      throw error;
    }
    if (response.hubEntryId !== hubEntryId || response.sharedVersion !== sharedVersion) {
      const error = new Error('Hub shared version changed') as Error & { code?: number };
      error.code = 20005;
      throw error;
    }
    if (!(response.blob instanceof Blob) || response.blob.type !== 'application/zip' ||
        response.blob.size === 0) {
      throw new Error('Invalid Hub ZIP response');
    }
    return response.blob;
  },
  /** Requires source WRITE; never infer this from a name-only Hub list search. */
  sourceShare: (sourceNamespaceId: string, sourceName: string) =>
    requireSuccess(client.get(`${BASE}/source-share`, {
      params: { sourceNamespaceId, sourceName }, silentError: true,
    } as AxiosRequestConfig & { silentError: boolean }) as ApiResult<HubShareState | null>, true)
      .then(response => { if (response.data) validateShareState(response.data); return response; }),
  governanceState: (hubEntryId: string) =>
    requireSuccess(client.get(`${BASE}/governance-state`, {
      params: { hubEntryId }, silentError: true,
    } as AxiosRequestConfig & { silentError: boolean }) as ApiResult<HubShareState>)
      .then(response => {
        validateShareState(response.data);
        if (response.data.hubEntryId !== hubEntryId) throw new Error('Mismatched Hub share state');
        return response;
      }),
  share: (data: { sourceNamespaceId: string; sourceName: string; sourceVersion: string }) =>
    requireSuccess(client.post(`${BASE}/share`, data) as ApiResult<HubShareResult>),
  adjust: (data: { hubEntryId: string; expectedMetaVersion: string; targetVersion: string }) =>
    requireSuccess(client.post(`${BASE}/adjust`, data) as ApiResult<HubShareResult>),
  unshare: (data: { hubEntryId: string; expectedMetaVersion: string }) =>
    requireSuccess(client.post(`${BASE}/unshare`, data) as ApiResult<HubShareResult>),
  forceUnshare: (data: { hubEntryId: string; expectedMetaVersion: string; reason: string }) =>
    requireSuccess(client.post(`${BASE}/force-unshare`, data) as ApiResult<HubShareResult>),
  copy: async (data: { sourceNamespaceId: string; sourceName: string; sourceVersion: string;
    targetNamespaceId: string; targetName: string; targetVersion: string }) => {
    const response = await requireSuccess(client.post(`${BASE}/copy`, data, { timeout: 60000 }) as ApiResult<HubTransferResult>);
    const result = response.data;
    if (result.targetNamespaceId !== data.targetNamespaceId || result.targetName !== data.targetName ||
        result.draftVersion !== data.targetVersion || result.status !== 'draft' || !validHubId(result.targetResourceId) || result.hubEntryId != null) {
      throw new Error('Mismatched copied Skill identity');
    }
    return response;
  },
  import: (data: {
    hubEntryId: string; targetNamespaceId: string; targetName: string; targetVersion?: string;
  }) => requireSuccess(client.post(`${BASE}/import`, data, { timeout: 60000 }) as ApiResult<HubTransferResult>),
};
