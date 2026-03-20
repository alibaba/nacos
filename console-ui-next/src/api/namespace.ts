import client from './client';
import type { AxiosPromise } from 'axios';

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
  namespaceId: string;
  namespaceName: string;
  namespaceDesc?: string;
}

export interface NamespaceUpdateData {
  namespace: string;
  namespaceShowName: string;
  namespaceDesc?: string;
}

export const namespaceApi = {
  list: (): AxiosPromise<NamespaceListResponse> =>
    client.get('v3/console/core/namespace/list'),

  detail: (namespaceId: string): AxiosPromise<Namespace> =>
    client.get('v3/console/core/namespace', { params: { namespaceId } }),

  create: (data: NamespaceCreateData): AxiosPromise<void> =>
    client.post('v3/console/core/namespace', data),
  
  update: (data: NamespaceUpdateData): AxiosPromise<void> =>
    client.put('v3/console/core/namespace', data),
  
  remove: (namespaceId: string): AxiosPromise<void> =>
    client.delete(`v3/console/core/namespace?namespaceId=${namespaceId}`),
};
