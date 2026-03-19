import client from './client';
import type { AxiosPromise } from 'axios';
import type {
  AgentSpecListParams,
  AgentSpecListResponse,
  AgentSpecDetail,
} from '@/types/agentspec';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

const BASE = 'v3/console/ai/agentspecs';

export const agentSpecApi = {
  /** 列表查询 */
  list: (params: AgentSpecListParams): AxiosPromise<ApiResponse<AgentSpecListResponse>> =>
    client.get(`${BASE}/list`, { params }),

  /** 获取详情 */
  getDetail: (params: {
    namespaceId?: string;
    agentSpecName: string;
    version?: string;
  }): AxiosPromise<ApiResponse<AgentSpecDetail>> =>
    client.get(BASE, { params }),

  /** 删除 AgentSpec */
  delete: (params: {
    namespaceId?: string;
    agentSpecName: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.delete(BASE, { params }),

  /** 上传 zip */
  upload: (namespaceId: string, file: File): AxiosPromise<ApiResponse<string>> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('namespaceId', namespaceId);
    return client.post(`${BASE}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  /** 创建草稿 */
  createDraft: (params: {
    namespaceId?: string;
    agentSpecName: string;
    basedOnVersion?: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.post(`${BASE}/draft`, params),

  /** 更新草稿 */
  updateDraft: (data: {
    namespaceId?: string;
    agentSpecName: string;
    content?: string;
    resource?: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.put(`${BASE}/draft`, data),

  /** 删除草稿 */
  deleteDraft: (params: {
    namespaceId?: string;
    agentSpecName: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.delete(`${BASE}/draft`, { params }),

  /** 提交审核 */
  submit: (data: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.post(`${BASE}/submit`, data),

  /** 发布 */
  publish: (data: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
    updateLatestLabel?: boolean;
  }): AxiosPromise<ApiResponse<string>> =>
    client.post(`${BASE}/publish`, data),

  /** 更新标签 */
  updateLabels: (data: {
    namespaceId?: string;
    agentSpecName: string;
    labels: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.put(`${BASE}/labels`, data),

  /** 上线 */
  online: (data: {
    namespaceId?: string;
    agentSpecName: string;
    scope?: string;
    version?: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.post(`${BASE}/online`, data),

  /** 下线 */
  offline: (data: {
    namespaceId?: string;
    agentSpecName: string;
    scope?: string;
    version?: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.post(`${BASE}/offline`, data),
};
