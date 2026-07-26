import { create } from 'zustand';
import { agentApi } from '@/api/agent';
import type {
  AgentBasicInfo,
  AgentDetailInfo,
  AgentListResponse,
  AgentSearchMode,
  AgentVersionDetail,
} from '@/types/agent';
import type { AxiosError } from 'axios';

interface AgentState {
  // List
  agents: AgentBasicInfo[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;

  // Search
  searchName: string;
  searchMode: AgentSearchMode;

  // Selection (batch operations)
  selectedNames: Set<string>;

  // Detail
  currentAgent: AgentDetailInfo | null;
  detailLoading: boolean;
  versionList: AgentVersionDetail[];

  // Error
  error: string | null;
}

interface AgentActions {
  fetchAgents: (namespaceId: string) => Promise<void>;
  fetchAgentDetail: (namespaceId: string, agentName: string, version?: string) => Promise<void>;
  fetchVersionList: (namespaceId: string, agentName: string) => Promise<void>;
  deleteAgent: (namespaceId: string, agentName: string) => Promise<boolean>;
  batchDelete: (namespaceId: string, names: string[]) => Promise<boolean>;
  setSearchParams: (params: { searchName?: string; searchMode?: AgentSearchMode }) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  toggleSelect: (name: string) => void;
  selectAll: (names: string[]) => void;
  clearSelection: () => void;
  clearCurrentAgent: () => void;
  clearError: () => void;
}

type AgentStore = AgentState & AgentActions;

export const useAgentStore = create<AgentStore>((set, get) => ({
  // List
  agents: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 12,

  // Search
  searchName: '',
  searchMode: 'blur',

  // Selection
  selectedNames: new Set(),

  // Detail
  currentAgent: null,
  detailLoading: false,
  versionList: [],

  // Error
  error: null,

  fetchAgents: async (namespaceId: string) => {
    const hasData = get().agents.length > 0;
    set({ loading: !hasData, error: null });
    try {
      const { searchName, searchMode, pageNo, pageSize } = get();
      const response = await agentApi.listAgents({
        agentName: searchName || '',
        namespaceId,
        search: searchMode,
        pageNo,
        pageSize,
      });
      const result = response as unknown as { data: AgentListResponse };
      const data = result.data;
      set({
        agents: data.pageItems || [],
        total: data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        loading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch agents',
        agents: [],
        total: 0,
      });
    }
  },

  fetchAgentDetail: async (namespaceId: string, agentName: string, version?: string) => {
    const hasAgent = get().currentAgent !== null;
    set({ detailLoading: !hasAgent, error: null });
    try {
      const response = await agentApi.getAgent({
        agentName,
        version,
        namespaceId,
      });
      const result = response as unknown as { data: AgentDetailInfo };
      set({
        currentAgent: result.data,
        detailLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch agent detail',
        currentAgent: null,
      });
    }
  },

  fetchVersionList: async (namespaceId: string, agentName: string) => {
    try {
      const response = await agentApi.getVersionList({
        agentName,
        namespaceId,
      });
      const result = response as unknown as { data: AgentVersionDetail[] };
      set({ versionList: result.data || [] });
    } catch {
      set({ versionList: [] });
    }
  },

  deleteAgent: async (namespaceId: string, agentName: string) => {
    try {
      await agentApi.deleteAgent({ agentName, namespaceId });
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to delete agent' });
      return false;
    }
  },

  batchDelete: async (namespaceId: string, names: string[]) => {
    let allSuccess = true;
    for (const name of names) {
      try {
        await agentApi.deleteAgent({ agentName: name, namespaceId });
      } catch {
        allSuccess = false;
      }
    }
    set({ selectedNames: new Set() });
    return allSuccess;
  },

  setSearchParams: (params) => {
    set((state) => ({ ...state, ...params, pageNo: 1 }));
  },

  setPage: (pageNo: number, pageSize?: number) => {
    set((state) => ({
      pageNo,
      pageSize: pageSize ?? state.pageSize,
    }));
  },

  resetSearch: () => {
    set({ searchName: '', searchMode: 'blur', pageNo: 1 });
  },

  toggleSelect: (name: string) => {
    set((state) => {
      const next = new Set(state.selectedNames);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return { selectedNames: next };
    });
  },

  selectAll: (names: string[]) => {
    set({ selectedNames: new Set(names) });
  },

  clearSelection: () => {
    set({ selectedNames: new Set() });
  },

  clearCurrentAgent: () => {
    set({ currentAgent: null, versionList: [] });
  },

  clearError: () => {
    set({ error: null });
  },
}));
