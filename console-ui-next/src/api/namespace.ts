import client from './client';
import type { ApiResult } from './types';

export interface Namespace {
  namespace: string;
  namespaceShowName: string;
  namespaceDesc?: string;
  quota: number;
  configCount: number;
  type: number;
}

export interface NamespaceListResponse {
  data: Namespace[];
}

export interface NamespaceCreateData {
  customNamespaceId: string;
  namespaceName: string;
  namespaceDesc?: string;
}

export interface NamespaceUpdateData {
  namespaceId: string;
  namespaceName: string;
  namespaceDesc?: string;
}

export const namespaceApi = {
  list: (): ApiResult<Namespace[]> =>
    client.get('v3/console/core/namespace/list') as ApiResult<Namespace[]>,

  detail: (namespaceId: string): ApiResult<Namespace> =>
    client.get('v3/console/core/namespace', { params: { namespaceId } }) as ApiResult<Namespace>,

  create: (data: NamespaceCreateData): ApiResult<boolean> =>
    client.post('v3/console/core/namespace', data) as ApiResult<boolean>,
  
  update: (data: NamespaceUpdateData): ApiResult<boolean> =>
    client.put('v3/console/core/namespace', data) as ApiResult<boolean>,
  
  remove: (namespaceId: string): ApiResult<boolean> =>
    client.delete(`v3/console/core/namespace?namespaceId=${namespaceId}`) as ApiResult<boolean>,
};
