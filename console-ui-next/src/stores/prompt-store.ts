import { create } from 'zustand';
import { promptApi } from '@/api/prompt';
import type {
  PromptMetaSummary,
  PromptMetaInfo,
  PromptVersionInfo,
  PromptVersionSummary,
  PromptListResponse,
  PromptVersionListResponse,
  PromptSearchMode,
} from '@/types/prompt';
import type { AxiosError } from 'axios';

interface PromptState {
  // List
  prompts: PromptMetaSummary[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;

  // Search
  searchKey: string;
  searchMode: PromptSearchMode;

  // Selection (batch operations)
  selectedKeys: Set<string>;

  // Detail
  currentMeta: PromptMetaInfo | null;
  currentVersion: PromptVersionInfo | null;
  detailLoading: boolean;
  versionList: PromptVersionSummary[];
  versionsTotal: number;
  versionsPageNo: number;
  versionsPageSize: number;

  // Error
  error: string | null;
}

interface PromptActions {
  fetchPrompts: (namespaceId: string) => Promise<void>;
  fetchPromptMetadata: (namespaceId: string, promptKey: string) => Promise<void>;
  fetchPromptDetail: (namespaceId: string, promptKey: string, version?: string, label?: string) => Promise<void>;
  fetchVersionList: (namespaceId: string, promptKey: string) => Promise<void>;
  deletePrompt: (namespaceId: string, promptKey: string) => Promise<boolean>;
  batchDelete: (namespaceId: string, keys: string[]) => Promise<boolean>;
  setSearchParams: (params: { searchKey?: string; searchMode?: PromptSearchMode }) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  setVersionsPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  toggleSelect: (key: string) => void;
  selectAll: (keys: string[]) => void;
  clearSelection: () => void;
  clearCurrentPrompt: () => void;
  clearError: () => void;
}

type PromptStore = PromptState & PromptActions;

export const usePromptStore = create<PromptStore>((set, get) => ({
  // List
  prompts: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 12,

  // Search
  searchKey: '',
  searchMode: 'blur',

  // Selection
  selectedKeys: new Set(),

  // Detail
  currentMeta: null,
  currentVersion: null,
  detailLoading: false,
  versionList: [],
  versionsTotal: 0,
  versionsPageNo: 1,
  versionsPageSize: 10,

  // Error
  error: null,

  fetchPrompts: async (namespaceId: string) => {
    const hasData = get().prompts.length > 0;
    set({ loading: !hasData, error: null });
    try {
      const { searchKey, searchMode, pageNo, pageSize } = get();
      const response = await promptApi.listPrompts({
        promptKey: searchKey || undefined,
        namespaceId,
        search: searchMode,
        pageNo,
        pageSize,
      });
      const result = response as unknown as { data: PromptListResponse };
      const data = result.data;
      set({
        prompts: data.pageItems || [],
        total: data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        loading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch prompts',
        prompts: [],
        total: 0,
      });
    }
  },

  fetchPromptMetadata: async (namespaceId: string, promptKey: string) => {
    try {
      const response = await promptApi.getPromptMetadata({ promptKey, namespaceId });
      const result = response as unknown as { data: PromptMetaInfo };
      set({ currentMeta: result.data });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to fetch prompt metadata' });
    }
  },

  fetchPromptDetail: async (namespaceId: string, promptKey: string, version?: string, label?: string) => {
    const hasVersion = get().currentVersion !== null;
    set({ detailLoading: !hasVersion, error: null });
    try {
      const response = await promptApi.getPromptDetail({ promptKey, version, label, namespaceId });
      const result = response as unknown as { data: PromptVersionInfo };
      set({
        currentVersion: result.data,
        detailLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch prompt detail',
        currentVersion: null,
      });
    }
  },

  fetchVersionList: async (namespaceId: string, promptKey: string) => {
    try {
      const { versionsPageNo, versionsPageSize } = get();
      const response = await promptApi.listVersions({
        promptKey,
        namespaceId,
        pageNo: versionsPageNo,
        pageSize: versionsPageSize,
      });
      const result = response as unknown as { data: PromptVersionListResponse };
      const data = result.data;
      set({
        versionList: data.pageItems || [],
        versionsTotal: data.totalCount || 0,
      });
    } catch {
      set({ versionList: [], versionsTotal: 0 });
    }
  },

  deletePrompt: async (namespaceId: string, promptKey: string) => {
    try {
      await promptApi.deletePrompt({ promptKey, namespaceId });
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to delete prompt' });
      return false;
    }
  },

  batchDelete: async (namespaceId: string, keys: string[]) => {
    let allSuccess = true;
    for (const key of keys) {
      try {
        await promptApi.deletePrompt({ promptKey: key, namespaceId });
      } catch {
        allSuccess = false;
      }
    }
    set({ selectedKeys: new Set() });
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

  setVersionsPage: (pageNo: number, pageSize?: number) => {
    set((state) => ({
      versionsPageNo: pageNo,
      versionsPageSize: pageSize ?? state.versionsPageSize,
    }));
  },

  resetSearch: () => {
    set({ searchKey: '', searchMode: 'blur', pageNo: 1 });
  },

  toggleSelect: (key: string) => {
    set((state) => {
      const next = new Set(state.selectedKeys);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return { selectedKeys: next };
    });
  },

  selectAll: (keys: string[]) => {
    set({ selectedKeys: new Set(keys) });
  },

  clearSelection: () => {
    set({ selectedKeys: new Set() });
  },

  clearCurrentPrompt: () => {
    set({ currentMeta: null, currentVersion: null, versionList: [], versionsTotal: 0, versionsPageNo: 1 });
  },

  clearError: () => {
    set({ error: null });
  },
}));
