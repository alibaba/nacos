import client from './client';
import type { ApiResult } from './types';
import type {
  AgentListParams,
  AgentListResponse,
  AgentDetailInfo,
  AgentCreateData,
  AgentUpdateData,
  AgentVersionDetail,
} from '@/types/agent';

export const agentApi = {
  /** List agents with pagination and search */
  listAgents: (params: AgentListParams): ApiResult<AgentListResponse> =>
    client.get('v3/console/ai/a2a/list', { params }) as ApiResult<AgentListResponse>,

  /** Get agent detail */
  getAgent: (params: {
    agentName: string;
    version?: string;
    namespaceId?: string;
  }): ApiResult<AgentDetailInfo> =>
    client.get('v3/console/ai/a2a', { params }) as ApiResult<AgentDetailInfo>,

  /** Create a new agent */
  createAgent: (data: AgentCreateData): ApiResult<string> =>
    client.post('v3/console/ai/a2a', data) as ApiResult<string>,

  /** Update an existing agent */
  updateAgent: (data: AgentUpdateData): ApiResult<string> =>
    client.put('v3/console/ai/a2a', data) as ApiResult<string>,

  /** Delete an agent */
  deleteAgent: (params: {
    agentName: string;
    namespaceId?: string;
  }): ApiResult<string> =>
    client.delete('v3/console/ai/a2a', { params }) as ApiResult<string>,

  /** Get version list for an agent */
  getVersionList: (params: {
    agentName: string;
    namespaceId?: string;
  }): ApiResult<AgentVersionDetail[]> =>
    client.get('v3/console/ai/a2a/version/list', { params }) as ApiResult<AgentVersionDetail[]>,
};
