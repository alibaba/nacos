import { create } from 'zustand';
import type { AxiosError } from 'axios';
import { agentApi } from '@/api/agent';
import { runtimeCacheKey } from '@/pages/newAgent/agent-console-model';
import type {
  AgentOverview,
  AgentPage,
  AgentScope,
  AgentSummary,
  AgentVersionDetail,
  AgentVersionStatus,
  AgentVersionSummary,
  ConsoleRuntimeEndpointView,
} from '@/types/agent';

interface AgentState {
  agents: AgentSummary[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;
  searchName: string;
  bizTag: string;
  scope?: AgentScope;
  owner: string;
  selectedNames: Set<string>;
  currentOverview: AgentOverview | null;
  currentVersion: AgentVersionDetail | null;
  versionPage: AgentPage<AgentVersionSummary> | null;
  runtimeCache: Record<string, ConsoleRuntimeEndpointView>;
  detailLoading: boolean;
  runtimeLoading: boolean;
  error: string | null;
}

interface AgentActions {
  fetchAgents: (namespaceId: string) => Promise<void>;
  fetchOverview: (namespaceId: string, agentName: string) => Promise<AgentOverview | null>;
  fetchVersionPage: (
    namespaceId: string,
    agentName: string,
    status?: AgentVersionStatus,
    pageNo?: number,
    pageSize?: number,
  ) => Promise<AgentPage<AgentVersionSummary> | null>;
  fetchVersion: (
    namespaceId: string,
    agentName: string,
    version: string,
  ) => Promise<AgentVersionDetail | null>;
  fetchRuntime: (
    namespaceId: string,
    agentName: string,
    version: string,
    protocol: string,
    force?: boolean,
  ) => Promise<ConsoleRuntimeEndpointView | null>;
  deleteAgent: (namespaceId: string, agentName: string) => Promise<boolean>;
  batchDelete: (namespaceId: string, names: string[]) => Promise<boolean>;
  setFilters: (filters: Partial<Pick<
    AgentState,
    'searchName' | 'bizTag' | 'scope' | 'owner'
  >>) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetFilters: () => void;
  toggleSelect: (name: string) => void;
  selectAll: (names: string[]) => void;
  clearSelection: () => void;
  clearDetail: () => void;
  clearError: () => void;
}

type AgentStore = AgentState & AgentActions;

function errorMessage(error: unknown, fallback: string): string {
  const axiosError = error as AxiosError<{ message?: string }>;
  return axiosError.response?.data?.message || fallback;
}

export const useAgentStore = create<AgentStore>((set, get) => ({
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

  fetchAgents: async (namespaceId) => {
    set({ loading: true, error: null });
    try {
      const { searchName, bizTag, scope, owner, pageNo, pageSize } = get();
      const response = await agentApi.listAgents({
        namespaceId,
        agentName: searchName || undefined,
        bizTag: bizTag || undefined,
        scope,
        owner: owner || undefined,
        pageNo,
        pageSize,
      });
      set({
        agents: response.data.pageItems || [],
        total: response.data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      set({
        agents: [],
        total: 0,
        loading: false,
        error: errorMessage(error, 'Failed to fetch agents'),
      });
    }
  },

  fetchOverview: async (namespaceId, agentName) => {
    set({ detailLoading: true, error: null });
    try {
      const response = await agentApi.getAgent({ namespaceId, agentName });
      set({
        currentOverview: response.data,
        versionPage: response.data.versionPage,
        detailLoading: false,
      });
      return response.data;
    } catch (error) {
      set({
        currentOverview: null,
        currentVersion: null,
        versionPage: null,
        detailLoading: false,
        error: errorMessage(error, 'Failed to fetch agent detail'),
      });
      return null;
    }
  },

  fetchVersionPage: async (namespaceId, agentName, status, pageNo = 1, pageSize = 20) => {
    try {
      const response = await agentApi.listVersions({
        namespaceId,
        agentName,
        status,
        pageNo,
        pageSize,
      });
      set({ versionPage: response.data });
      return response.data;
    } catch (error) {
      set({ error: errorMessage(error, 'Failed to fetch Agent versions') });
      return null;
    }
  },

  fetchVersion: async (namespaceId, agentName, version) => {
    set({ detailLoading: true, error: null, currentVersion: null, runtimeCache: {} });
    try {
      const response = await agentApi.getVersion({ namespaceId, agentName, version });
      set({ currentVersion: response.data, detailLoading: false });
      return response.data;
    } catch (error) {
      set({
        currentVersion: null,
        detailLoading: false,
        error: errorMessage(error, 'Failed to fetch Agent version'),
      });
      return null;
    }
  },

  fetchRuntime: async (namespaceId, agentName, version, protocol, force = false) => {
    const key = runtimeCacheKey(version, protocol);
    const cached = get().runtimeCache[key];
    if (cached && !force) {
      return cached;
    }
    set({ runtimeLoading: true, error: null });
    try {
      const response = await agentApi.getRuntimeEndpoints({
        namespaceId,
        agentName,
        version,
        protocol,
      });
      set((state) => ({
        runtimeCache: { ...state.runtimeCache, [key]: response.data },
        runtimeLoading: false,
      }));
      return response.data;
    } catch (error) {
      set({
        runtimeLoading: false,
        error: errorMessage(error, 'Failed to fetch Runtime Endpoints'),
      });
      return null;
    }
  },

  deleteAgent: async (namespaceId, agentName) => {
    try {
      await agentApi.deleteAgent({ namespaceId, agentName });
      return true;
    } catch (error) {
      set({ error: errorMessage(error, 'Failed to delete agent') });
      return false;
    }
  },

  batchDelete: async (namespaceId, names) => {
    const results = await Promise.allSettled(
      names.map((agentName) => agentApi.deleteAgent({ namespaceId, agentName })),
    );
    set({ selectedNames: new Set() });
    return results.every((result) => result.status === 'fulfilled');
  },

  setFilters: (filters) => {
    set({ ...filters, pageNo: 1 });
  },

  setPage: (pageNo, pageSize) => {
    set((state) => ({ pageNo, pageSize: pageSize ?? state.pageSize }));
  },

  resetFilters: () => {
    set({ searchName: '', bizTag: '', scope: undefined, owner: '', pageNo: 1 });
  },

  toggleSelect: (name) => {
    set((state) => {
      const selectedNames = new Set(state.selectedNames);
      if (selectedNames.has(name)) {
        selectedNames.delete(name);
      } else {
        selectedNames.add(name);
      }
      return { selectedNames };
    });
  },

  selectAll: (names) => {
    set({ selectedNames: new Set(names) });
  },

  clearSelection: () => {
    set({ selectedNames: new Set() });
  },

  clearDetail: () => {
    set({
      currentOverview: null,
      currentVersion: null,
      versionPage: null,
      runtimeCache: {},
      error: null,
    });
  },

  clearError: () => {
    set({ error: null });
  },
}));
