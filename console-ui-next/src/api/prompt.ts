import client from './client';
import type { ApiResult } from './types';
import type {
  PromptListParams,
  PromptListResponse,
  PromptMetaInfo,
  PromptVersionInfo,
  PromptVersionListResponse,
  PromptDraftCreateData,
  PromptDraftUpdateData,
  PromptSubmitData,
  PromptPublishData,
  PromptOnlineOfflineData,
  PromptLabelsUpdateData,
  PromptDescriptionUpdateData,
  PromptBizTagsUpdateData,
} from '@/types/prompt';

const BASE = 'v3/console/ai/prompt';

/** Build form-urlencoded params from a data object, skipping undefined/null values */
function toFormParams(data: object): URLSearchParams {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(data)) {
    if (value !== undefined && value !== null) {
      params.append(key, String(value));
    }
  }
  return params;
}

const FORM_HEADERS = { 'Content-Type': 'application/x-www-form-urlencoded' };

export const promptApi = {
  // ===== Read operations =====

  /** List prompts with pagination and search */
  listPrompts: (params: PromptListParams): ApiResult<PromptListResponse> =>
    client.get(`${BASE}/list`, { params }) as ApiResult<PromptListResponse>,

  /** Get governance detail (full meta info with version details) */
  getGovernanceDetail: (params: { promptKey: string; namespaceId?: string }): ApiResult<PromptMetaInfo> =>
    client.get(`${BASE}/governance`, { params }) as ApiResult<PromptMetaInfo>,

  /** Get version detail */
  getVersionDetail: (params: { promptKey: string; version: string; namespaceId?: string }): ApiResult<PromptVersionInfo> =>
    client.get(`${BASE}/version`, { params }) as ApiResult<PromptVersionInfo>,

  /** List version history (paginated) */
  listVersions: (params: {
    promptKey: string;
    namespaceId?: string;
    pageNo?: number;
    pageSize?: number;
  }): ApiResult<PromptVersionListResponse> =>
    client.get(`${BASE}/versions`, { params }) as ApiResult<PromptVersionListResponse>,

  /**
   * Download a specific prompt version as a Markdown document (returned as a Blob).
   * Caller is responsible for creating an object URL and triggering the browser download.
   */
  downloadVersion: (params: {
    promptKey: string;
    version: string;
    namespaceId?: string;
  }): Promise<Blob> =>
    client.get(`${BASE}/version/download`, {
      params,
      responseType: 'blob',
    }) as unknown as Promise<Blob>,

  // ===== Lifecycle write operations =====

  /** Create draft */
  createDraft: (data: PromptDraftCreateData): ApiResult<string> =>
    client.post(`${BASE}/draft`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<string>,

  /** Update draft */
  updateDraft: (data: PromptDraftUpdateData): ApiResult<boolean> =>
    client.put(`${BASE}/draft`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Delete draft */
  deleteDraft: (params: { promptKey: string; namespaceId?: string }): ApiResult<boolean> =>
    client.delete(`${BASE}/draft`, { params }) as ApiResult<boolean>,

  /** Submit for review */
  submit: (data: PromptSubmitData): ApiResult<string> =>
    client.post(`${BASE}/submit`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<string>,

  /** Publish version */
  publish: (data: PromptPublishData): ApiResult<boolean> =>
    client.post(`${BASE}/publish`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Force publish version (admin) */
  forcePublish: (data: PromptPublishData): ApiResult<boolean> =>
    client.post(`${BASE}/force-publish`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Re-edit a reviewed version (transitions back to draft) */
  redraft: (data: { promptKey: string; version: string; namespaceId?: string }): ApiResult<boolean> =>
    client.post(`${BASE}/redraft`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Online version */
  online: (data: PromptOnlineOfflineData): ApiResult<boolean> =>
    client.post(`${BASE}/online`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Offline version */
  offline: (data: PromptOnlineOfflineData): ApiResult<boolean> =>
    client.post(`${BASE}/offline`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  // ===== Metadata update operations =====

  /** Update labels */
  updateLabels: (data: PromptLabelsUpdateData): ApiResult<boolean> =>
    client.put(`${BASE}/labels`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Update description */
  updateDescription: (data: PromptDescriptionUpdateData): ApiResult<boolean> =>
    client.put(`${BASE}/description`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  /** Update biz tags */
  updateBizTags: (data: PromptBizTagsUpdateData): ApiResult<boolean> =>
    client.put(`${BASE}/biz-tags`, toFormParams(data), { headers: FORM_HEADERS }) as ApiResult<boolean>,

  // ===== Delete =====

  /** Delete prompt */
  deletePrompt: (params: { promptKey: string; namespaceId?: string }): ApiResult<boolean> =>
    client.delete(`${BASE}`, { params }) as ApiResult<boolean>,
};
