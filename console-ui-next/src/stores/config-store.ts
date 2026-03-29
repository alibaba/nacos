import { create } from 'zustand';
import { configApi } from '@/api/config';
import type { Config, ConfigListResponse } from '@/types/config';
import type { AxiosError } from 'axios';

interface SearchParams {
  dataId: string;
  groupName: string;
  appName: string;
  configTags: string;
  configType: string;
  searchMode: 'blur' | 'accurate';
  configDetail: string;
}

interface ConfigState {
  // List state
  configs: Config[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;

  // Search params
  dataId: string;
  groupName: string;
  appName: string;
  configTags: string;
  configType: string;
  searchMode: 'blur' | 'accurate';
  configDetail: string;

  // Selection state
  selectedIds: Set<string>;

  // Current config
  currentConfig: Config | null;
  detailLoading: boolean;

  // Error
  error: string | null;
}

interface ConfigActions {
  fetchConfigs: (namespaceId: string) => Promise<void>;
  setSearchParams: (params: Partial<SearchParams>) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  fetchConfig: (dataId: string, groupName: string, namespaceId: string) => Promise<void>;
  deleteConfig: (dataId: string, groupName: string, namespaceId: string) => Promise<boolean>;
  resetSearch: () => void;
  clearCurrentConfig: () => void;
  clearError: () => void;
  // Selection actions
  toggleSelect: (id: string) => void;
  selectAll: () => void;
  clearSelection: () => void;
}

type ConfigStore = ConfigState & ConfigActions;

const defaultSearchParams: SearchParams = {
  dataId: '',
  groupName: '',
  appName: '',
  configTags: '',
  configType: '',
  searchMode: 'blur',
  configDetail: '',
};

export const useConfigStore = create<ConfigStore>((set, get) => ({
  // List state
  configs: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 10,

  // Search params
  ...defaultSearchParams,

  // Selection state
  selectedIds: new Set<string>(),

  // Current config
  currentConfig: null,
  detailLoading: false,

  // Error
  error: null,

  // Actions
  fetchConfigs: async (namespaceId: string) => {
    set({ loading: true, error: null });

    try {
      const {
        dataId,
        groupName,
        appName,
        configTags,
        configType,
        searchMode,
        configDetail,
        pageNo,
        pageSize,
      } = get();

      // In blur mode, wrap search terms with * wildcards for backend LIKE matching
      const isBlur = searchMode === 'blur';
      const wrapBlur = (v: string) => (isBlur && v ? `*${v}*` : v || undefined);

      const params = {
        dataId: wrapBlur(dataId),
        groupName: wrapBlur(groupName),
        appName: appName || undefined,
        configTags: configTags || undefined,
        type: configType || undefined,
        search: searchMode,
        configDetail: wrapBlur(configDetail),
        pageNo,
        pageSize,
        namespaceId,
      };

      // Use searchDetail if configDetail is set, otherwise use list
      const response = configDetail
        ? await configApi.searchDetail(params)
        : await configApi.list(params);

      // Response interceptor already unwraps response.data
      const result = response as unknown as { data: ConfigListResponse };
      const data = result.data;

      set({
        configs: data.pageItems || [],
        total: data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      const errorMessage = axiosError.response?.data?.message || 'Failed to fetch configs';
      set({
        loading: false,
        error: errorMessage,
        configs: [],
        total: 0,
      });
    }
  },

  setSearchParams: (params: Partial<SearchParams>) => {
    set((state) => ({
      ...state,
      ...params,
      pageNo: 1, // Reset to first page when search params change
    }));
  },

  setPage: (pageNo: number, pageSize?: number) => {
    set((state) => ({
      pageNo,
      pageSize: pageSize ?? state.pageSize,
      selectedIds: new Set<string>(),
    }));
  },

  fetchConfig: async (dataId: string, groupName: string, namespaceId: string) => {
    set({ detailLoading: true, error: null });

    try {
      const response = await configApi.get({
        dataId,
        groupName,
        namespaceId,
      });

      // Response interceptor already unwraps response.data
      const result = response as unknown as { data: Config };

      set({
        currentConfig: result.data,
        detailLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      const errorMessage = axiosError.response?.data?.message || 'Failed to fetch config';
      set({
        detailLoading: false,
        error: errorMessage,
        currentConfig: null,
      });
    }
  },

  deleteConfig: async (dataId: string, groupName: string, namespaceId: string) => {
    try {
      await configApi.delete({
        dataId,
        groupName,
        namespaceId,
      });
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      const errorMessage = axiosError.response?.data?.message || 'Failed to delete config';
      set({ error: errorMessage });
      return false;
    }
  },

  resetSearch: () => {
    set({
      ...defaultSearchParams,
      pageNo: 1,
      selectedIds: new Set<string>(),
    });
  },

  clearCurrentConfig: () => {
    set({ currentConfig: null });
  },

  clearError: () => {
    set({ error: null });
  },

  // Selection actions
  toggleSelect: (id: string) => {
    set((state) => {
      const next = new Set(state.selectedIds);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return { selectedIds: next };
    });
  },

  selectAll: () => {
    set((state) => {
      const allIds = state.configs
        .filter((c) => c.id)
        .map((c) => c.id!);
      const allSelected = allIds.length > 0 && allIds.every((id) => state.selectedIds.has(id));
      return { selectedIds: allSelected ? new Set<string>() : new Set(allIds) };
    });
  },

  clearSelection: () => {
    set({ selectedIds: new Set<string>() });
  },
}));
