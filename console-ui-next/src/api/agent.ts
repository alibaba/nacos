import client from './client';
import type { AxiosPromise } from 'axios';
import type {
  AgentListParams,
  AgentListResponse,
  AgentDetailInfo,
  AgentCreateData,
  AgentUpdateData,
  AgentVersionDetail,
} from '@/types/agent';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export const agentApi = {
  /** List agents with pagination and search */
  listAgents: (params: AgentListParams): AxiosPromise<ApiResponse<AgentListResponse>> =>
    client.get('v3/console/ai/a2a/list', { params }),

  /** Get agent detail */
  getAgent: (params: {
    agentName: string;
    version?: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<AgentDetailInfo>> =>
    client.get('v3/console/ai/a2a', { params }),

  /** Create a new agent */
  createAgent: (data: AgentCreateData): AxiosPromise<ApiResponse<string>> =>
    client.post('v3/console/ai/a2a', data),

  /** Update an existing agent */
  updateAgent: (data: AgentUpdateData): AxiosPromise<ApiResponse<string>> =>
    client.put('v3/console/ai/a2a', data),

  /** Delete an agent */
  deleteAgent: (params: {
    agentName: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<string>> =>
    client.delete('v3/console/ai/a2a', { params }),

  /** Get version list for an agent */
  getVersionList: (params: {
    agentName: string;
    namespaceId?: string;
  }): AxiosPromise<ApiResponse<AgentVersionDetail[]>> =>
    client.get('v3/console/ai/a2a/version/list', { params }),
};
