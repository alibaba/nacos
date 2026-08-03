import client from './client';
import type { ApiResult } from './types';
import type {
  AgentDraftCreateData,
  AgentDraftUpdateData,
  AgentListParams,
  AgentMetadata,
  AgentMetadataUpdateData,
  AgentOverview,
  AgentPage,
  AgentSummary,
  AgentVersionActionData,
  AgentVersionDetail,
  AgentVersionStatus,
  AgentVersionSummary,
  ConsoleRuntimeEndpointView,
} from '@/types/agent';

const BASE = 'v3/console/ai/agents';
const FORM_HEADERS = { 'Content-Type': 'application/x-www-form-urlencoded' };

function toFormParams(data: object): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(data)) {
    if (value !== undefined && value !== null) {
      params.append(key, String(value));
    }
  }
  return params;
}

function postVersionAction(
  path: string,
  data: AgentVersionActionData,
): ApiResult<AgentVersionSummary> {
  return client.post(`${BASE}/${path}`, toFormParams(data), {
    headers: FORM_HEADERS,
  }) as ApiResult<AgentVersionSummary>;
}

export const agentApi = {
  listAgents: (params: AgentListParams): ApiResult<AgentPage<AgentSummary>> =>
    client.get(`${BASE}/list`, { params }) as ApiResult<AgentPage<AgentSummary>>,

  getAgent: (params: {
    namespaceId?: string;
    agentName: string;
  }): ApiResult<AgentOverview> =>
    client.get(BASE, { params }) as ApiResult<AgentOverview>,

  updateAgent: (data: AgentMetadataUpdateData): ApiResult<AgentMetadata> =>
    client.put(BASE, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<AgentMetadata>,

  deleteAgent: (params: {
    namespaceId?: string;
    agentName: string;
  }): ApiResult<void> =>
    client.delete(BASE, { params }) as ApiResult<void>,

  listVersions: (params: {
    namespaceId?: string;
    agentName: string;
    status?: AgentVersionStatus;
    pageNo?: number;
    pageSize?: number;
  }): ApiResult<AgentPage<AgentVersionSummary>> =>
    client.get(`${BASE}/versions`, { params }) as ApiResult<AgentPage<AgentVersionSummary>>,

  getVersion: (params: AgentVersionActionData): ApiResult<AgentVersionDetail> =>
    client.get(`${BASE}/version`, { params }) as ApiResult<AgentVersionDetail>,

  getRuntimeEndpoints: (params: AgentVersionActionData & {
    protocol: string;
  }): ApiResult<ConsoleRuntimeEndpointView> =>
    client.get(`${BASE}/runtime-endpoints`, { params }) as ApiResult<ConsoleRuntimeEndpointView>,

  createDraft: (data: AgentDraftCreateData): ApiResult<AgentVersionDetail> =>
    client.post(`${BASE}/draft`, toFormParams(data), {
      headers: FORM_HEADERS,
    }) as ApiResult<AgentVersionDetail>,

  updateDraft: (data: AgentDraftUpdateData): ApiResult<AgentVersionDetail> =>
    client.put(`${BASE}/draft`, toFormParams(data), {
      headers: FORM_HEADERS,
    }) as ApiResult<AgentVersionDetail>,

  deleteDraft: (params: AgentVersionActionData): ApiResult<void> =>
    client.delete(`${BASE}/draft`, { params }) as ApiResult<void>,

  submit: (data: AgentVersionActionData): ApiResult<AgentVersionSummary> =>
    postVersionAction('submit', data),

  publish: (data: AgentVersionActionData): ApiResult<AgentVersionSummary> =>
    postVersionAction('publish', data),

  forcePublish: (data: AgentVersionActionData): ApiResult<AgentVersionSummary> =>
    postVersionAction('force-publish', data),

  redraft: (data: AgentVersionActionData): ApiResult<AgentVersionSummary> =>
    postVersionAction('redraft', data),

  online: (data: AgentVersionActionData): ApiResult<AgentVersionSummary> =>
    postVersionAction('online', data),

  offline: (data: AgentVersionActionData): ApiResult<AgentVersionSummary> =>
    postVersionAction('offline', data),

  updateLabels: (data: {
    namespaceId?: string;
    agentName: string;
    labels: string;
  }): ApiResult<AgentMetadata> =>
    client.put(`${BASE}/labels`, toFormParams(data), {
      headers: FORM_HEADERS,
    }) as ApiResult<AgentMetadata>,
};

export { toFormParams };
