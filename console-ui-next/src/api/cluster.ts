import client from './client';
import type { ApiResult } from './types';

export interface ClusterNode {
  ip: string;
  port: number;
  state: string;
  address: string;
  failAccessCnt?: number;
  abilities?: Record<string, unknown>;
  extendInfo?: Record<string, string>;
}

export const clusterApi = {
  /**
   * List cluster members.
   * Backend returns Result<Collection<NacosMember>> — data is a flat array, NOT paginated.
   * The `keyword` param filters by IP; pageNo/pageSize are NOT supported by backend.
   */
  list: (params: {
    keyword?: string;
  }): ApiResult<ClusterNode[]> =>
    client.get('v3/console/core/cluster/nodes', { params }) as ApiResult<ClusterNode[]>,

  leave: (addresses: string[]): ApiResult<string> =>
    client.post('v3/console/core/cluster/server/leave', addresses, {
      headers: { 'Content-Type': 'application/json' },
    }) as ApiResult<string>,
};
