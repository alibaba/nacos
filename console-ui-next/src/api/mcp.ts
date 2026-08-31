import client from './client';
import type { ApiResult } from './types';
import type {
  McpListParams,
  McpListResponse,
  McpServerDetailInfo,
  McpTool,
  McpDraftData,
  McpPage,
  McpServerVersionDetail,
  McpServerVersionSummary,
  McpVersionIdentity,
  McpVersionStatus,
} from '@/types/mcp';

const BASE = 'v3/console/ai/mcp';
const FORM_HEADERS = { 'Content-Type': 'application/x-www-form-urlencoded' };

export function toMcpFormParams(data: object): URLSearchParams {
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
  data: McpVersionIdentity,
): ApiResult<McpServerVersionSummary> {
  return client.post(`${BASE}/${path}`, toMcpFormParams(data), {
    headers: FORM_HEADERS,
  }) as ApiResult<McpServerVersionSummary>;
}

export const mcpApi = {
  /** List MCP servers with pagination and search */
  listMcpServers: (params: McpListParams): ApiResult<McpListResponse> =>
    client.get(`${BASE}/list`, { params }) as ApiResult<McpListResponse>,

  /** Get MCP server detail */
  getMcpServer: (params: {
    mcpId?: string;
    mcpName?: string;
    version?: string;
    namespaceId?: string;
  }): ApiResult<McpServerDetailInfo> =>
    client.get(BASE, { params }) as ApiResult<McpServerDetailInfo>,

  /** Delete an MCP server */
  deleteMcpServer: (params: {
    mcpId?: string;
    mcpName?: string;
    namespaceId?: string;
  }): ApiResult<string> =>
    client.delete(BASE, { params }) as ApiResult<string>,

  /** List lifecycle versions for one canonical MCP server. */
  listVersions: (params: {
    namespaceId?: string;
    mcpName: string;
    status?: McpVersionStatus;
    pageNo?: number;
    pageSize?: number;
  }): ApiResult<McpPage<McpServerVersionSummary>> =>
    client.get(`${BASE}/versions`, { params }) as ApiResult<McpPage<McpServerVersionSummary>>,

  /** Read one exact lifecycle version. */
  getVersion: (params: McpVersionIdentity): ApiResult<McpServerVersionDetail> =>
    client.get(`${BASE}/version`, { params }) as ApiResult<McpServerVersionDetail>,

  /** Create one lifecycle draft. */
  createDraft: (data: McpDraftData): ApiResult<McpServerVersionDetail> =>
    client.post(`${BASE}/draft`, toMcpFormParams(data), {
      headers: FORM_HEADERS,
    }) as ApiResult<McpServerVersionDetail>,

  /** Replace one exact current lifecycle draft. */
  updateDraft: (data: McpDraftData): ApiResult<McpServerVersionDetail> =>
    client.put(`${BASE}/draft`, toMcpFormParams(data), {
      headers: FORM_HEADERS,
    }) as ApiResult<McpServerVersionDetail>,

  /** Delete one exact current lifecycle draft. */
  deleteDraft: (params: McpVersionIdentity): ApiResult<void> =>
    client.delete(`${BASE}/draft`, { params }) as ApiResult<void>,

  submit: (data: McpVersionIdentity): ApiResult<McpServerVersionSummary> =>
    postVersionAction('submit', data),

  publish: (data: McpVersionIdentity): ApiResult<McpServerVersionSummary> =>
    postVersionAction('publish', data),

  forcePublish: (data: McpVersionIdentity): ApiResult<McpServerVersionSummary> =>
    postVersionAction('force-publish', data),

  redraft: (data: McpVersionIdentity): ApiResult<McpServerVersionSummary> =>
    postVersionAction('redraft', data),

  online: (data: McpVersionIdentity): ApiResult<McpServerVersionSummary> =>
    postVersionAction('online', data),

  offline: (data: McpVersionIdentity): ApiResult<McpServerVersionSummary> =>
    postVersionAction('offline', data),

  /** Replace custom labels while preserving the server-managed latest label. */
  updateLabels: (data: {
    namespaceId?: string;
    mcpName: string;
    labels: string;
  }): ApiResult<Record<string, string>> =>
    client.put(`${BASE}/labels`, toMcpFormParams(data), {
      headers: FORM_HEADERS,
    }) as ApiResult<Record<string, string>>,

  /** Import tools from an external MCP server endpoint */
  importToolsFromMcp: (params: {
    transportType: string;
    baseUrl: string;
    endpoint?: string;
    authToken?: string;
  }): ApiResult<McpTool[]> =>
    client.get(`${BASE}/importToolsFromMcp`, { params }) as ApiResult<McpTool[]>,
};
