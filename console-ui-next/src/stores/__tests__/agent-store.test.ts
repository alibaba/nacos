/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  AgentOverview,
  AgentPage,
  AgentSummary,
  AgentVersionDetail,
  AgentVersionSummary,
  ConsoleRuntimeEndpointView,
} from '@/types/agent';

const page: AgentPage<AgentSummary> = {
  totalCount: 1,
  pageNumber: 1,
  pagesAvailable: 1,
  pageItems: [{ namespaceId: 'public', agentName: 'demo', status: 'enable' }],
};
const versionPage: AgentPage<AgentVersionSummary> = {
  totalCount: 1,
  pageNumber: 1,
  pagesAvailable: 1,
  pageItems: [{ version: '1.0.0', status: 'online' }],
};
const overview: AgentOverview = {
  agent: { namespaceId: 'public', agentName: 'demo', status: 'enable' },
  versionPage,
};
const version: AgentVersionDetail = {
  namespaceId: 'public',
  agentName: 'demo',
  version: '1.0.0',
  status: 'online',
  callInterfaces: [],
};
const runtime: ConsoleRuntimeEndpointView = {
  runtimeEndpointSnapshot: {
    namespaceId: 'public',
    agentName: 'demo',
    version: '1.0.0',
    protocol: 'A2A',
    items: [],
  },
  namingServiceRef: {
    namespaceId: 'public',
    groupName: 'AI_GROUP',
    serviceName: 'demo',
  },
};

const mockAgentApi = {
  listAgents: vi.fn().mockResolvedValue({ data: page }),
  getAgent: vi.fn().mockResolvedValue({ data: overview }),
  listVersions: vi.fn().mockResolvedValue({ data: versionPage }),
  getVersion: vi.fn().mockResolvedValue({ data: version }),
  getRuntimeEndpoints: vi.fn().mockResolvedValue({ data: runtime }),
  deleteAgent: vi.fn().mockResolvedValue({ data: undefined }),
};

vi.mock('@/api/agent', () => ({ agentApi: mockAgentApi }));

const { useAgentStore } = await import('../agent-store');

function resetStore(): void {
  useAgentStore.setState({
    agents: [],
    loading: false,
    total: 0,
    pageNo: 1,
    pageSize: 12,
    searchName: '',
    bizTag: '',
    scope: undefined,
    owner: '',
    selectedNames: new Set(),
    currentOverview: null,
    currentVersion: null,
    versionPage: null,
    runtimeCache: {},
    detailLoading: false,
    runtimeLoading: false,
    error: null,
  });
}

describe('Agent Console store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAgentApi.listAgents.mockResolvedValue({ data: page });
    mockAgentApi.getAgent.mockResolvedValue({ data: overview });
    mockAgentApi.listVersions.mockResolvedValue({ data: versionPage });
    mockAgentApi.getVersion.mockResolvedValue({ data: version });
    mockAgentApi.getRuntimeEndpoints.mockResolvedValue({ data: runtime });
    mockAgentApi.deleteAgent.mockResolvedValue({ data: undefined });
    resetStore();
  });

  it('fetches the Agent page with only active filters', async () => {
    useAgentStore.setState({
      searchName: 'demo',
      bizTag: 'tag',
      scope: 'PRIVATE',
      owner: 'owner',
      pageNo: 2,
      pageSize: 24,
    });
    await useAgentStore.getState().fetchAgents('tenant');
    expect(mockAgentApi.listAgents).toHaveBeenCalledWith({
      namespaceId: 'tenant',
      agentName: 'demo',
      bizTag: 'tag',
      scope: 'PRIVATE',
      owner: 'owner',
      pageNo: 2,
      pageSize: 24,
    });
    expect(useAgentStore.getState()).toMatchObject({
      agents: page.pageItems,
      total: 1,
      loading: false,
      error: null,
    });
  });

  it('normalizes missing list fields and omits empty filters', async () => {
    mockAgentApi.listAgents.mockResolvedValueOnce({ data: {} });
    await useAgentStore.getState().fetchAgents('public');
    expect(mockAgentApi.listAgents).toHaveBeenCalledWith({
      namespaceId: 'public',
      agentName: undefined,
      bizTag: undefined,
      scope: undefined,
      owner: undefined,
      pageNo: 1,
      pageSize: 12,
    });
    expect(useAgentStore.getState()).toMatchObject({ agents: [], total: 0 });
  });

  it('maps list errors and clears stale results', async () => {
    useAgentStore.setState({ agents: page.pageItems, total: 1 });
    mockAgentApi.listAgents.mockRejectedValueOnce({
      response: { data: { message: 'list denied' } },
    });
    await useAgentStore.getState().fetchAgents('public');
    expect(useAgentStore.getState()).toMatchObject({
      agents: [],
      total: 0,
      loading: false,
      error: 'list denied',
    });

    mockAgentApi.listAgents.mockRejectedValueOnce(new Error('network'));
    await useAgentStore.getState().fetchAgents('public');
    expect(useAgentStore.getState().error).toBe('Failed to fetch agents');
  });

  it('loads and clears the complete overview consistently', async () => {
    await expect(useAgentStore.getState().fetchOverview('public', 'demo'))
      .resolves.toEqual(overview);
    expect(useAgentStore.getState()).toMatchObject({
      currentOverview: overview,
      versionPage,
      detailLoading: false,
    });

    mockAgentApi.getAgent.mockRejectedValueOnce({
      response: { data: { message: 'missing' } },
    });
    await expect(useAgentStore.getState().fetchOverview('public', 'missing'))
      .resolves.toBeNull();
    expect(useAgentStore.getState()).toMatchObject({
      currentOverview: null,
      currentVersion: null,
      versionPage: null,
      detailLoading: false,
      error: 'missing',
    });
  });

  it('loads a filtered Version page and reports its failures', async () => {
    await expect(useAgentStore.getState().fetchVersionPage(
      'public',
      'demo',
      'online',
      3,
      5,
    )).resolves.toEqual(versionPage);
    expect(mockAgentApi.listVersions).toHaveBeenCalledWith({
      namespaceId: 'public',
      agentName: 'demo',
      status: 'online',
      pageNo: 3,
      pageSize: 5,
    });

    mockAgentApi.listVersions.mockRejectedValueOnce(new Error('network'));
    await expect(useAgentStore.getState().fetchVersionPage('public', 'demo'))
      .resolves.toBeNull();
    expect(useAgentStore.getState().error).toBe('Failed to fetch Agent versions');
  });

  it('loads one exact Version and resets Version-scoped Runtime state', async () => {
    useAgentStore.setState({ runtimeCache: { stale: runtime } });
    await expect(useAgentStore.getState().fetchVersion('public', 'demo', '1.0.0'))
      .resolves.toEqual(version);
    expect(useAgentStore.getState()).toMatchObject({
      currentVersion: version,
      runtimeCache: {},
      detailLoading: false,
    });

    mockAgentApi.getVersion.mockRejectedValueOnce(new Error('network'));
    await expect(useAgentStore.getState().fetchVersion('public', 'demo', 'missing'))
      .resolves.toBeNull();
    expect(useAgentStore.getState()).toMatchObject({
      currentVersion: null,
      detailLoading: false,
      error: 'Failed to fetch Agent version',
    });
  });

  it('caches Runtime by exact Version and protocol and supports forced refresh', async () => {
    await expect(useAgentStore.getState().fetchRuntime(
      'public',
      'demo',
      '1.0.0',
      'A2A',
    )).resolves.toEqual(runtime);
    expect(mockAgentApi.getRuntimeEndpoints).toHaveBeenCalledTimes(1);
    await expect(useAgentStore.getState().fetchRuntime(
      'public',
      'demo',
      '1.0.0',
      'A2A',
    )).resolves.toEqual(runtime);
    expect(mockAgentApi.getRuntimeEndpoints).toHaveBeenCalledTimes(1);
    await useAgentStore.getState().fetchRuntime('public', 'demo', '1.0.0', 'A2A', true);
    expect(mockAgentApi.getRuntimeEndpoints).toHaveBeenCalledTimes(2);
    expect(useAgentStore.getState().runtimeCache['1.0.0@@A2A']).toEqual(runtime);

    mockAgentApi.getRuntimeEndpoints.mockRejectedValueOnce({
      response: { data: { message: 'runtime denied' } },
    });
    await expect(useAgentStore.getState().fetchRuntime(
      'public',
      'demo',
      '1.0.0',
      'MCP',
    )).resolves.toBeNull();
    expect(useAgentStore.getState()).toMatchObject({
      runtimeLoading: false,
      error: 'runtime denied',
    });
  });

  it('returns success and failure for single and batch deletion', async () => {
    await expect(useAgentStore.getState().deleteAgent('public', 'demo')).resolves.toBe(true);
    mockAgentApi.deleteAgent.mockRejectedValueOnce({
      response: { data: { message: 'delete denied' } },
    });
    await expect(useAgentStore.getState().deleteAgent('public', 'demo')).resolves.toBe(false);
    expect(useAgentStore.getState().error).toBe('delete denied');

    useAgentStore.setState({ selectedNames: new Set(['a', 'b']) });
    await expect(useAgentStore.getState().batchDelete('public', ['a', 'b']))
      .resolves.toBe(true);
    expect(useAgentStore.getState().selectedNames.size).toBe(0);

    mockAgentApi.deleteAgent
      .mockResolvedValueOnce({ data: undefined })
      .mockRejectedValueOnce(new Error('failed'));
    await expect(useAgentStore.getState().batchDelete('public', ['a', 'b']))
      .resolves.toBe(false);
  });

  it('updates filters, paging, selection, detail and errors predictably', () => {
    useAgentStore.setState({ pageNo: 4, pageSize: 12, error: 'old' });
    useAgentStore.getState().setFilters({ searchName: 'demo', scope: 'PUBLIC' });
    expect(useAgentStore.getState()).toMatchObject({
      searchName: 'demo',
      scope: 'PUBLIC',
      pageNo: 1,
    });
    useAgentStore.getState().setPage(2);
    expect(useAgentStore.getState()).toMatchObject({ pageNo: 2, pageSize: 12 });
    useAgentStore.getState().setPage(3, 30);
    expect(useAgentStore.getState()).toMatchObject({ pageNo: 3, pageSize: 30 });
    useAgentStore.getState().toggleSelect('demo');
    expect(useAgentStore.getState().selectedNames.has('demo')).toBe(true);
    useAgentStore.getState().toggleSelect('demo');
    expect(useAgentStore.getState().selectedNames.has('demo')).toBe(false);
    useAgentStore.getState().selectAll(['a', 'b']);
    expect([...useAgentStore.getState().selectedNames]).toEqual(['a', 'b']);
    useAgentStore.getState().clearSelection();
    expect(useAgentStore.getState().selectedNames.size).toBe(0);

    useAgentStore.setState({
      currentOverview: overview,
      currentVersion: version,
      versionPage,
      runtimeCache: { runtime },
    });
    useAgentStore.getState().clearDetail();
    expect(useAgentStore.getState()).toMatchObject({
      currentOverview: null,
      currentVersion: null,
      versionPage: null,
      runtimeCache: {},
      error: null,
    });

    useAgentStore.setState({
      searchName: 'demo',
      bizTag: 'tag',
      scope: 'PRIVATE',
      owner: 'owner',
      pageNo: 4,
      error: 'old',
    });
    useAgentStore.getState().resetFilters();
    expect(useAgentStore.getState()).toMatchObject({
      searchName: '',
      bizTag: '',
      scope: undefined,
      owner: '',
      pageNo: 1,
    });
    useAgentStore.getState().clearError();
    expect(useAgentStore.getState().error).toBeNull();
  });
});
