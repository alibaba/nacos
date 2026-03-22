import { create } from 'zustand';
import { skillApi } from '@/api/skill';
import type {
  SkillListItem,
  SkillAdminDetail,
} from '@/types/skill';
import type { AxiosError } from 'axios';

interface SkillState {
  // List
  items: SkillListItem[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;
  searchName: string;
  orderBy: string;
  selectedNames: Set<string>;

  // Detail
  currentDetail: (SkillAdminDetail & { name: string }) | null;
  detailLoading: boolean;

  // Error
  error: string | null;
}

interface SkillActions {
  fetchList: (namespaceId: string) => Promise<void>;
  fetchDetail: (namespaceId: string, name: string) => Promise<void>;
  setSearchParams: (params: { searchName?: string; orderBy?: string }) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  toggleSelect: (name: string) => void;
  selectAll: (names: string[]) => void;
  clearSelection: () => void;
  clearDetail: () => void;
  clearError: () => void;
}

type SkillStore = SkillState & SkillActions;

export const useSkillStore = create<SkillStore>((set, get) => ({
  // List
  items: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 12,
  searchName: '',
  orderBy: '',
  selectedNames: new Set(),

  // Detail
  currentDetail: null,
  detailLoading: false,

  // Error
  error: null,

  fetchList: async (namespaceId: string) => {
    set({ loading: true, error: null });
    try {
      const { searchName, pageNo, pageSize, orderBy } = get();
      const response = await skillApi.list({
        namespaceId,
        skillName: searchName || undefined,
        search: searchName ? 'blur' : undefined,
        orderBy: orderBy || undefined,
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
        selectedNames: new Set([...state.selectedNames].filter((n) => itemNames.has(n))),
      }));
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        loading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch skills',
        items: [],
        total: 0,
      });
    }
  },

  fetchDetail: async (namespaceId: string, name: string) => {
    const hasDetail = get().currentDetail !== null;
    set({ detailLoading: !hasDetail, error: null });
    try {
      const response = await skillApi.getDetail({ namespaceId, skillName: name });
      set({
        currentDetail: { ...response.data, name },
        detailLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch skill detail',
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
    set({ searchName: '', orderBy: '', pageNo: 1 });
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
