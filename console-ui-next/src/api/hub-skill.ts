import type { AxiosRequestConfig } from 'axios';
import { validHubId } from '@/lib/hub-skill-utils';
import client from './client';
import type { ApiResult } from './types';
import type {
  HubSkillPage, HubSkillDetail, HubSkillContent, HubShareState,
  HubShareResult, HubTransferResult,
} from '@/types/hub-skill';

const BASE = 'v3/console/ai/hub/skills';

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
  list: (params: { query?: string; tagsAll?: string[]; pageNo: number; pageSize: number }) => {
    const query: Record<string, string | number | undefined> = {
      query: params.query, pageNo: params.pageNo, pageSize: params.pageSize,
    };
    params.tagsAll?.forEach((tag, index) => { query[`tagsAll[${index}]`] = tag; });
    return requireSuccess(client.get(`${BASE}/list`, { params: query }) as ApiResult<HubSkillPage>);
  },
  detail: (hubEntryId: string) =>
    requireSuccess(client.get(`${BASE}/detail`, { params: { hubEntryId } }) as ApiResult<HubSkillDetail>),
  content: (hubEntryId: string) =>
    requireSuccess(client.get(`${BASE}/content`, { params: { hubEntryId } }) as ApiResult<HubSkillContent>),
  /** Compare the returned package identity with the page snapshot before saving. */
  download: async (hubEntryId: string, sharedVersion: string): Promise<Blob> => {
    const response = await client.get(`${BASE}/download`, {
      params: { hubEntryId }, responseType: 'blob', timeout: 60000,
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
  import: (data: {
    hubEntryId: string; targetNamespaceId: string; targetName: string; targetVersion?: string;
  }) => requireSuccess(client.post(`${BASE}/import`, data, { timeout: 60000 }) as ApiResult<HubTransferResult>),
};
