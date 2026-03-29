import client from './client';
import type { ApiResult } from './types';
import type {
  PromptListParams,
  PromptListResponse,
  PromptMetaInfo,
  PromptVersionInfo,
  PromptVersionListResponse,
  PromptPublishData,
  PromptUpdateMetadataData,
  PromptLabelBindData,
} from '@/types/prompt';

export const promptApi = {
  /** List prompts with pagination and search */
  listPrompts: (params: PromptListParams): ApiResult<PromptListResponse> =>
    client.get('v3/console/ai/prompt/list', { params }) as ApiResult<PromptListResponse>,

  /** Get prompt metadata (versions, labels, description, bizTags) */
  getPromptMetadata: (params: {
    promptKey: string;
    namespaceId?: string;
  }): ApiResult<PromptMetaInfo> =>
    client.get('v3/console/ai/prompt/metadata', { params }) as ApiResult<PromptMetaInfo>,

  /** Get prompt version detail */
  getPromptDetail: (params: {
    promptKey: string;
    version?: string;
    label?: string;
    namespaceId?: string;
  }): ApiResult<PromptVersionInfo> =>
    client.get('v3/console/ai/prompt/detail', { params }) as ApiResult<PromptVersionInfo>,

  /** List version history (paginated) */
  listVersions: (params: {
    promptKey: string;
    namespaceId?: string;
    pageNo?: number;
    pageSize?: number;
  }): ApiResult<PromptVersionListResponse> =>
    client.get('v3/console/ai/prompt/versions', { params }) as ApiResult<PromptVersionListResponse>,

  /** Publish new prompt version (form-urlencoded) */
  publishVersion: (data: PromptPublishData): ApiResult<boolean> => {
    const params = new URLSearchParams();
    params.append('promptKey', data.promptKey);
    params.append('version', data.version);
    params.append('template', data.template);
    if (data.commitMsg) params.append('commitMsg', data.commitMsg);
    if (data.description) params.append('description', data.description);
    if (data.bizTags) params.append('bizTags', data.bizTags);
    if (data.variables) params.append('variables', data.variables);
    if (data.namespaceId) params.append('namespaceId', data.namespaceId);
    return client.post('v3/console/ai/prompt', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    }) as ApiResult<boolean>;
  },

  /** Update prompt metadata (description/bizTags) */
  updateMetadata: (data: PromptUpdateMetadataData): ApiResult<boolean> => {
    const params = new URLSearchParams();
    params.append('promptKey', data.promptKey);
    if (data.description !== undefined) params.append('description', data.description);
    if (data.bizTags !== undefined) params.append('bizTags', data.bizTags);
    if (data.namespaceId) params.append('namespaceId', data.namespaceId);
    return client.put('v3/console/ai/prompt/metadata', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    }) as ApiResult<boolean>;
  },

  /** Bind label to version */
  bindLabel: (data: PromptLabelBindData): ApiResult<boolean> => {
    const params = new URLSearchParams();
    params.append('promptKey', data.promptKey);
    params.append('label', data.label);
    params.append('version', data.version);
    if (data.namespaceId) params.append('namespaceId', data.namespaceId);
    return client.put('v3/console/ai/prompt/label', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    }) as ApiResult<boolean>;
  },

  /** Unbind label */
  unbindLabel: (params: {
    promptKey: string;
    label: string;
    namespaceId?: string;
  }): ApiResult<boolean> =>
    client.delete('v3/console/ai/prompt/label', { params }) as ApiResult<boolean>,

  /** Delete prompt */
  deletePrompt: (params: {
    promptKey: string;
    namespaceId?: string;
  }): ApiResult<boolean> =>
    client.delete('v3/console/ai/prompt', { params }) as ApiResult<boolean>,
};
