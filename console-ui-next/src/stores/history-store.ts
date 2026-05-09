import { create } from 'zustand';
import { configApi } from '@/api/config';
import type { ConfigHistory, ConfigHistoryListResponse } from '@/types/config';
import type { AxiosError } from 'axios';

interface HistoryState {
  historyList: ConfigHistory[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;
  currentHistory: ConfigHistory | null;
  detailLoading: boolean;
  error: string | null;
}

interface HistoryActions {
  fetchHistoryList: (dataId: string, groupName: string, namespaceId?: string) => Promise<void>;
  fetchHistoryDetail: (
    nid: string,
    dataId: string,
    groupName: string,
    namespaceId?: string
  ) => Promise<void>;
  setPage: (pageNo: number, pageSize?: number) => void;
  clearCurrentHistory: () => void;
  clearError: () => void;
}

type HistoryStore = HistoryState & HistoryActions;

export const useHistoryStore = create<HistoryStore>((set, get) => ({
  historyList: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 10,
  currentHistory: null,
  detailLoading: false,
  error: null,

  fetchHistoryList: async (dataId: string, groupName: string, namespaceId?: string) => {
    set({ loading: true, error: null });

    try {
      const { pageNo, pageSize } = get();
      const response = await configApi.historyList({
        dataId,
        groupName,
        namespaceId,
        pageNo,
        pageSize,
      });

      const result = response as unknown as { data: ConfigHistoryListResponse };
      const data = result.data;

      set({
        historyList: data.pageItems || [],
        total: data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      const errorMessage = axiosError.response?.data?.message || 'Failed to fetch history list';
      set({
        loading: false,
        error: errorMessage,
        historyList: [],
        total: 0,
      });
    }
  },

  fetchHistoryDetail: async (
    nid: string,
    dataId: string,
    groupName: string,
    namespaceId?: string
  ) => {
    set({ detailLoading: true, error: null });

    try {
      const response = await configApi.historyDetail({
        nid,
        dataId,
        groupName,
        namespaceId,
      });

      const result = response as unknown as { data: ConfigHistory };

      set({
        currentHistory: result.data,
        detailLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      const errorMessage = axiosError.response?.data?.message || 'Failed to fetch history detail';
      set({
        detailLoading: false,
        error: errorMessage,
        currentHistory: null,
      });
    }
  },

  setPage: (pageNo: number, pageSize?: number) => {
    set((state) => ({
      pageNo,
      pageSize: pageSize ?? state.pageSize,
    }));
  },

  clearCurrentHistory: () => {
    set({ currentHistory: null });
  },

  clearError: () => {
    set({ error: null });
  },
}));
