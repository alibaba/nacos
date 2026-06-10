import client from './client';
import type { ApiResult } from './types';
import type {
  AgentSpecListParams,
  AgentSpecListResponse,
  AgentSpecDetail,
  AgentSpecDocument,
} from '@/types/agentspec';

const BASE = 'v3/console/ai/agentspecs';

export const agentSpecApi = {
  /** 列表查询 */
  list: (params: AgentSpecListParams): ApiResult<AgentSpecListResponse> =>
    client.get(`${BASE}/list`, { params }) as ApiResult<AgentSpecListResponse>,

  /** 获取详情 */
  getDetail: (params: {
    namespaceId?: string;
    agentSpecName: string;
  }): ApiResult<AgentSpecDetail> =>
    client.get(BASE, { params }) as ApiResult<AgentSpecDetail>,

  /** 获取指定版本内容 */
  getVersion: (params: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
  }): ApiResult<AgentSpecDocument> =>
    client.get(`${BASE}/version`, { params }) as ApiResult<AgentSpecDocument>,

  /** 删除 AgentSpec */
  delete: (params: {
    namespaceId?: string;
    agentSpecName: string;
  }): ApiResult<string> =>
    client.delete(BASE, { params }) as ApiResult<string>,

  /** 上传 zip */
  upload: (namespaceId: string, file: File): ApiResult<string> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('namespaceId', namespaceId);
    return client.post(`${BASE}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    }) as ApiResult<string>;
  },

  /** 创建草稿 */
  createDraft: (params: {
    namespaceId?: string;
    agentSpecName: string;
    basedOnVersion?: string;
    targetVersion?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/draft`, params) as ApiResult<string>,

  /** 更新草稿 */
  updateDraft: (data: {
    namespaceId?: string;
    agentSpecCard: string;
    setAsLatest?: boolean;
  }): ApiResult<string> =>
    client.put(`${BASE}/draft`, data) as ApiResult<string>,

  /** 删除草稿 */
  deleteDraft: (params: {
    namespaceId?: string;
    agentSpecName: string;
  }): ApiResult<string> =>
    client.delete(`${BASE}/draft`, { params }) as ApiResult<string>,

  /** 提交审核 */
  submit: (data: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/submit`, data) as ApiResult<string>,

  /** 发布 */
  publish: (data: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/publish`, data) as ApiResult<string>,

  /** 强制发布（跳过 pipeline 校验，仅限管理员） */
  forcePublish: (data: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/force-publish`, data) as ApiResult<string>,

  /** 重新编辑已审核版本（回退到草稿状态） */
  redraft: (data: {
    namespaceId?: string;
    agentSpecName: string;
    version: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/redraft`, data) as ApiResult<string>,

  /** 更新标签 */
  updateLabels: (data: {
    namespaceId?: string;
    agentSpecName: string;
    labels: string;
  }): ApiResult<string> =>
    client.put(`${BASE}/labels`, data) as ApiResult<string>,

  /** 更新业务标签 */
  updateBizTags: (data: {
    namespaceId?: string;
    agentSpecName: string;
    bizTags: string;
  }): ApiResult<string> =>
    client.put(`${BASE}/biz-tags`, data) as ApiResult<string>,

  /** 更新可见范围 */
  updateScope: (data: {
    namespaceId?: string;
    agentSpecName: string;
    scope: string;
  }): ApiResult<string> =>
    client.put(`${BASE}/scope`, data) as ApiResult<string>,

  /** 上线 */
  online: (data: {
    namespaceId?: string;
    agentSpecName: string;
    scope?: string;
    version?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/online`, data) as ApiResult<string>,

  /** 下线 */
  offline: (data: {
    namespaceId?: string;
    agentSpecName: string;
    scope?: string;
    version?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/offline`, data) as ApiResult<string>,
};
