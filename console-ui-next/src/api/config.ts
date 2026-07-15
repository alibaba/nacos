import client from './client';
import type { ApiResult } from './types';
import type {
  Config,
  ConfigListParams,
  ConfigListResponse,
  ConfigCreateData,
  ConfigHistoryListResponse,
  ConfigHistory,
  ConflictPolicy,
  ConfigCloneItem,
  ConfigListenerInfo,
  ConfigBetaInfo,
} from '@/types/config';

export const configApi = {
  // List configs
  list: (params: ConfigListParams): ApiResult<ConfigListResponse> =>
    client.get('v3/console/cs/config/list', { params }) as ApiResult<ConfigListResponse>,

  // Advanced search with content
  searchDetail: (params: ConfigListParams): ApiResult<ConfigListResponse> =>
    client.get('v3/console/cs/config/searchDetail', { params }) as ApiResult<ConfigListResponse>,

  // Get single config
  get: (params: { dataId: string; groupName: string; namespaceId?: string }): ApiResult<Config> =>
    client.get('v3/console/cs/config', { params }) as ApiResult<Config>,

  // Create or update config
  publish: (data: ConfigCreateData): ApiResult<boolean> =>
    client.post('v3/console/cs/config', data) as ApiResult<boolean>,

  // Delete config
  delete: (params: { dataId: string; groupName: string; namespaceId?: string }): ApiResult<boolean> =>
    client.delete('v3/console/cs/config', { params }) as ApiResult<boolean>,

  // Batch delete
  batchDelete: (params: { ids: string; namespaceId: string }): ApiResult<boolean> =>
    client.delete('v3/console/cs/config/batchDelete', { params }) as ApiResult<boolean>,

  // History list
  historyList: (params: {
    dataId: string;
    groupName: string;
    namespaceId?: string;
    pageNo: number;
    pageSize: number;
  }): ApiResult<ConfigHistoryListResponse> =>
    client.get('v3/console/cs/history/list', { params }) as ApiResult<ConfigHistoryListResponse>,

  // History detail
  historyDetail: (params: {
    nid: string;
    dataId: string;
    groupName: string;
    namespaceId?: string;
  }): ApiResult<ConfigHistory> =>
    client.get('v3/console/cs/history', { params }) as ApiResult<ConfigHistory>,

  // Previous version
  historyPrevious: (params: {
    id: string;
    dataId: string;
    groupName: string;
    namespaceId?: string;
  }): ApiResult<ConfigHistory> =>
    client.get('v3/console/cs/history/previous', { params }) as ApiResult<ConfigHistory>,

  // Listeners by config
  listenersByConfig: (params: { dataId: string; groupName: string; namespaceId?: string }): ApiResult<ConfigListenerInfo> =>
    client.get('v3/console/cs/config/listener', { params }) as ApiResult<ConfigListenerInfo>,

  // Listeners by IP
  listenersByIp: (params: { ip: string; namespaceId?: string }): ApiResult<ConfigListenerInfo> =>
    client.get('v3/console/cs/config/listener/ip', { params }) as ApiResult<ConfigListenerInfo>,

  // Get beta config
  getBeta: (params: { dataId: string; groupName: string; namespaceId?: string }): ApiResult<ConfigBetaInfo> =>
    client.get('v3/console/cs/config/beta', { params }) as ApiResult<ConfigBetaInfo>,

  // Publish beta config (same endpoint, with betaIps header)
  publishBeta: (data: ConfigCreateData, betaIps: string): ApiResult<boolean> =>
    client.post('v3/console/cs/config', data, {
      headers: { betaIps },
    }) as ApiResult<boolean>,

  // Stop beta
  stopBeta: (params: { dataId: string; groupName: string; namespaceId?: string }): ApiResult<boolean> =>
    client.delete('v3/console/cs/config/beta', { params }) as ApiResult<boolean>,

  // Export - returns URL string for window.open() browser download
  exportUrl: (params: { namespaceId: string; ids?: string; groupName?: string; appName?: string; dataId?: string }): string => {
    const baseURL = client.defaults.baseURL || '';
    const queryParams = new URLSearchParams();
    queryParams.set('namespaceId', params.namespaceId);
    if (params.ids !== undefined) queryParams.set('ids', params.ids);
    if (params.groupName) queryParams.set('groupName', params.groupName);
    if (params.appName) queryParams.set('appName', params.appName);
    if (params.dataId) queryParams.set('dataId', params.dataId);
    // Add auth params from localStorage
    try {
      const tokenStr = localStorage.getItem('token');
      if (tokenStr) {
        const tokenData = JSON.parse(tokenStr);
        if (tokenData.accessToken) queryParams.set('accessToken', tokenData.accessToken);
        if (tokenData.username) queryParams.set('username', tokenData.username);
      }
    } catch { /* ignore */ }
    return `${baseURL}v3/console/cs/config/export2?${queryParams.toString()}`;
  },

  // Import - POST multipart/form-data (ZIP file)
  importFile: (namespaceId: string, policy: ConflictPolicy, file: File): ApiResult<unknown> => {
    const formData = new FormData();
    formData.append('file', file);
    // Add auth params
    let username = '';
    let accessToken = '';
    try {
      const tokenStr = localStorage.getItem('token');
      if (tokenStr) {
        const tokenData = JSON.parse(tokenStr);
        accessToken = tokenData.accessToken || '';
        username = tokenData.username || '';
      }
    } catch { /* ignore */ }
    return client.post('v3/console/cs/config/import', formData, {
      params: { namespaceId, policy, accessToken, username },
      headers: { 'Content-Type': 'multipart/form-data' },
    }) as ApiResult<unknown>;
  },

  // Clone - POST JSON body
  clone: (params: { namespaceId: string; targetNamespaceId: string; policy: ConflictPolicy }, configs: ConfigCloneItem[]): ApiResult<unknown> =>
    client.post('v3/console/cs/config/clone', JSON.stringify(configs), {
      params: {
        targetNamespaceId: params.targetNamespaceId,
        policy: params.policy,
        namespaceId: params.namespaceId,
      },
      headers: { 'Content-Type': 'application/json' },
    }) as ApiResult<unknown>,
};
