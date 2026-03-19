import client from './client';
import type { AxiosPromise } from 'axios';
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

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export const promptApi = {
  /** List prompts with pagination and search */
  listPrompts: (params: PromptListParams): AxiosPromise<ApiResponse<PromptListResponse>> =>
    client.get('v3/console/ai/prompt/list', { params }),

  /** Get prompt metadata (versions, labels, description, bizTags) */
  getPromptMetadata: (params: {
    promptKey: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<PromptMetaInfo>> =>
    client.get('v3/console/ai/prompt/metadata', { params }),

  /** Get prompt version detail */
  getPromptDetail: (params: {
    promptKey: string;
    version?: string;
    label?: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<PromptVersionInfo>> =>
    client.get('v3/console/ai/prompt/detail', { params }),

  /** List version history (paginated) */
  listVersions: (params: {
    promptKey: string;
    namespaceId?: string;
    pageNo?: number;
    pageSize?: number;
  }): AxiosPromise<ApiResponse<PromptVersionListResponse>> =>
    client.get('v3/console/ai/prompt/versions', { params }),

  /** Publish new prompt version (form-urlencoded) */
  publishVersion: (data: PromptPublishData): AxiosPromise<ApiResponse<boolean>> => {
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
    });
  },

  /** Update prompt metadata (description/bizTags) */
  updateMetadata: (data: PromptUpdateMetadataData): AxiosPromise<ApiResponse<boolean>> => {
    const params = new URLSearchParams();
    params.append('promptKey', data.promptKey);
    if (data.description !== undefined) params.append('description', data.description);
    if (data.bizTags !== undefined) params.append('bizTags', data.bizTags);
    if (data.namespaceId) params.append('namespaceId', data.namespaceId);
    return client.put('v3/console/ai/prompt/metadata', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
  },

  /** Bind label to version */
  bindLabel: (data: PromptLabelBindData): AxiosPromise<ApiResponse<boolean>> => {
    const params = new URLSearchParams();
    params.append('promptKey', data.promptKey);
    params.append('label', data.label);
    params.append('version', data.version);
    if (data.namespaceId) params.append('namespaceId', data.namespaceId);
    return client.put('v3/console/ai/prompt/label', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
  },

  /** Unbind label */
  unbindLabel: (params: {
    promptKey: string;
    label: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<boolean>> =>
    client.delete('v3/console/ai/prompt/label', { params }),

  /** Delete prompt */
  deletePrompt: (params: {
    promptKey: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<boolean>> =>
    client.delete('v3/console/ai/prompt', { params }),
};
