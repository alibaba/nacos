import { create } from 'zustand';
import { agentSpecApi } from '@/api/agentspec';
import i18n from '@/locales';
import type {
  AgentSpecListItem,
  AgentSpecDetail,
} from '@/types/agentspec';
import type { AxiosError } from 'axios';

interface AgentSpecState {
  // List
  items: AgentSpecListItem[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;
  searchName: string;
  /** Sort field (e.g. "download_count"). Empty = default sort. */
  orderBy: string;
  /** Filter by resource owner; empty string = no filter */
  filterOwner: string;
  /** Filter by visibility scope: "PUBLIC" | "PRIVATE" | "" (no filter) */
  filterScope: string;
  selectedNames: Set<string>;

  // Detail
  currentDetail: (AgentSpecDetail & { name: string }) | null;
  detailLoading: boolean;

  // Error
  error: string | null;
}

interface AgentSpecActions {
  fetchList: (namespaceId: string) => Promise<void>;
  fetchDetail: (namespaceId: string, name: string) => Promise<void>;
  setSearchParams: (params: { searchName?: string; orderBy?: string; filterOwner?: string; filterScope?: string }) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  toggleSelect: (name: string) => void;
  selectAll: (names: string[]) => void;
  clearSelection: () => void;
  clearDetail: () => void;
  clearError: () => void;
}

type AgentSpecStore = AgentSpecState & AgentSpecActions;

export const useAgentSpecStore = create<AgentSpecStore>((set, get) => ({
  // List
  items: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 12,
  searchName: '',
  orderBy: '',
  filterOwner: '',
  filterScope: '',
  selectedNames: new Set(),

  // Detail
  currentDetail: null,
  detailLoading: false,

  // Error
  error: null,

  fetchList: async (namespaceId: string) => {
    set({ loading: true, error: null });
    try {
      const { searchName, pageNo, pageSize, orderBy, filterOwner, filterScope } = get();
      const response = await agentSpecApi.list({
        namespaceId,
        agentSpecName: searchName || undefined,
        search: searchName ? 'blur' : undefined,
        orderBy: orderBy || undefined,
        owner: filterOwner || undefined,
        scope: filterScope || undefined,
        pageNo,
        pageSize,
      });
      const data = response.data;
      const newItems = data.pageItems || [];
      const itemNames = new Set(newItems.map((item) => item.name));
      set((state) => ({
        items: newItems,
        total: data.totalCount || 0,
        loading: false,
        // Keep selectedNames as a subset of current items
        selectedNames: new Set([...state.selectedNames].filter((n) => itemNames.has(n))),
      }));
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        loading: false,
        error: axiosError.response?.data?.message || i18n.t('agentSpec.loadListError'),
        items: [],
        total: 0,
      });
    }
  },

  fetchDetail: async (namespaceId: string, name: string) => {
    const hasDetail = get().currentDetail !== null;
    set({ detailLoading: !hasDetail, error: null });
    try {
      const response = await agentSpecApi.getDetail({
        namespaceId,
        agentSpecName: name,
      });
      set({
        currentDetail: { ...response.data, name },
        detailLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || i18n.t('agentSpec.loadError'),
        currentDetail: null,
      });
    }
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
    set({ searchName: '', orderBy: '', filterOwner: '', filterScope: '', pageNo: 1 });
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

  clearDetail: () => {
    set({ currentDetail: null });
  },

  clearError: () => {
    set({ error: null });
  },
}));
