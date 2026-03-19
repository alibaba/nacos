import client from './client';
import type { AxiosPromise } from 'axios';

export interface ClusterNode {
  ip: string;
  port: number;
  state: string;
  address: string;
  failAccessCnt?: number;
  abilities?: Record<string, unknown>;
  extendInfo?: Record<string, string>;
}

export interface ClusterNodeListResponse {
  pageItems: ClusterNode[];
  totalCount: number;
  pageNumber: number;
  pagesAvailable: number;
}

export const clusterApi = {
  list: (params: {
    keyword?: string;
    pageNo?: number;
    pageSize?: number;
    withInstances?: boolean;
  }): AxiosPromise<ClusterNodeListResponse> =>
    client.get('v3/console/core/cluster/nodes', { params }),

  leave: (addresses: string[]): AxiosPromise<void> =>
    client.post('v3/console/core/cluster/server/leave', addresses, {
      headers: { 'Content-Type': 'application/json' },
    }),
};
