import client from './client';
import type {
  SkillListParams,
  SkillListResponse,
  SkillAdminDetail,
  SkillDocument,
} from '@/types/skill';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

type ApiResult<T> = Promise<ApiResponse<T>>;

const BASE = 'v3/console/ai/skills';

export const skillApi = {
  /** List skills with pagination and search */
  list: (params: SkillListParams): ApiResult<SkillListResponse> =>
    client.get(`${BASE}/list`, { params }) as ApiResult<SkillListResponse>,

  /** Get skill detail (governance info + version summaries) */
  getDetail: (params: {
    namespaceId?: string;
    skillName: string;
  }): ApiResult<SkillAdminDetail> =>
    client.get(BASE, { params }) as ApiResult<SkillAdminDetail>,

  /** Get specific version detail */
  getVersion: (params: {
    namespaceId?: string;
    skillName: string;
    version: string;
  }): ApiResult<SkillDocument> =>
    client.get(`${BASE}/version`, { params }) as ApiResult<SkillDocument>,

  /** Download skill version as ZIP */
  downloadVersion: (params: {
    namespaceId?: string;
    skillName: string;
    version: string;
  }): Promise<Blob> =>
    client.get(`${BASE}/version/download`, {
      params,
      responseType: 'blob',
    }) as unknown as Promise<Blob>,

  /** Upload skill from ZIP */
  upload: (namespaceId: string, file: File): ApiResult<string> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('namespaceId', namespaceId);
    return client.post(`${BASE}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }) as ApiResult<string>;
  },

  /** Delete skill */
  delete: (params: {
    namespaceId?: string;
    skillName: string;
  }): ApiResult<string> =>
    client.delete(BASE, { params }) as ApiResult<string>,

  /** Create draft version */
  createDraft: (data: {
    namespaceId?: string;
    skillName: string;
    basedOnVersion?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/draft`, data) as ApiResult<string>,

  /** Update draft content */
  updateDraft: (data: {
    namespaceId?: string;
    skillCard: string;
    setAsLatest?: boolean;
  }): ApiResult<string> =>
    client.put(`${BASE}/draft`, data) as ApiResult<string>,

  /** Delete draft */
  deleteDraft: (params: {
    namespaceId?: string;
    skillName: string;
  }): ApiResult<string> =>
    client.delete(`${BASE}/draft`, { params }) as ApiResult<string>,

  /** Submit for pipeline review */
  submit: (data: {
    namespaceId?: string;
    skillName: string;
    version?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/submit`, data) as ApiResult<string>,

  /** Publish approved version */
  publish: (data: {
    namespaceId?: string;
    skillName: string;
    version: string;
    updateLatestLabel?: boolean;
  }): ApiResult<string> =>
    client.post(`${BASE}/publish`, data) as ApiResult<string>,

  /** Update labels */
  updateLabels: (data: {
    namespaceId?: string;
    skillName: string;
    labels: string;
  }): ApiResult<string> =>
    client.put(`${BASE}/labels`, data) as ApiResult<string>,

  /** Online (skill-level or version-level) */
  online: (data: {
    namespaceId?: string;
    skillName: string;
    scope?: string;
    version?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/online`, data) as ApiResult<string>,

  /** Offline (skill-level or version-level) */
  offline: (data: {
    namespaceId?: string;
    skillName: string;
    scope?: string;
    version?: string;
  }): ApiResult<string> =>
    client.post(`${BASE}/offline`, data) as ApiResult<string>,

  /** Update visibility scope */
  updateScope: (data: {
    namespaceId?: string;
    skillName: string;
    scope: string;
  }): ApiResult<string> =>
    client.put(`${BASE}/scope`, data) as ApiResult<string>,
};
