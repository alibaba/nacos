import { create } from 'zustand';
import { skillApi } from '@/api/skill';
import type {
  SkillListItem,
  SkillAdminDetail,
  SkillSubscription,
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
  /** Filter by resource owner; empty string = no filter */
  filterOwner: string;
  /** Filter by visibility scope: "PUBLIC" | "PRIVATE" | "" (no filter) */
  filterScope: string;
  /** Filter by business tag (fuzzy match); empty string = no filter */
  filterBizTag: string;
  selectedNames: Set<string>;

  // Subscriptions
  subscriptions: SkillSubscription[];
  subscriptionMap: Record<string, SkillSubscription>;
  subscriptionLoading: boolean;
  subscriptionSaving: boolean;

  // Detail
  currentDetail: (SkillAdminDetail & { name: string }) | null;
  detailLoading: boolean;

  // Error
  error: string | null;
}

interface SkillActions {
  fetchList: (namespaceId: string) => Promise<void>;
  fetchSubscriptions: (namespaceId: string) => Promise<void>;
  subscribeSkills: (namespaceId: string, names: string[]) => Promise<void>;
  unsubscribeSkills: (namespaceId: string, names: string[]) => Promise<void>;
  fetchDetail: (namespaceId: string, name: string) => Promise<void>;
  setSearchParams: (params: { searchName?: string; orderBy?: string; filterOwner?: string; filterScope?: string; filterBizTag?: string }) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  toggleSelect: (name: string) => void;
  selectAll: (names: string[]) => void;
  clearSelection: () => void;
  clearDetail: () => void;
  clearError: () => void;
}

type SkillStore = SkillState & SkillActions;

const getSubscriptionsFromResponse = (response: unknown): {
  found: boolean;
  subscriptions: SkillSubscription[];
} => {
  const body = response as {
    data?: { subscriptions?: SkillSubscription[] };
    subscriptions?: SkillSubscription[];
  };
  if (Array.isArray(body.data?.subscriptions)) {
    return { found: true, subscriptions: body.data.subscriptions };
  }
  if (Array.isArray(body.subscriptions)) {
    return { found: true, subscriptions: body.subscriptions };
  }
  return { found: false, subscriptions: [] };
};

const toSubscriptionMap = (
  subscriptions: SkillSubscription[],
): Record<string, SkillSubscription> => subscriptions.reduce<Record<string, SkillSubscription>>(
  (result, item) => {
    if (item.name) {
      result[item.name] = item;
    }
    return result;
  },
  {},
);

export const useSkillStore = create<SkillStore>((set, get) => ({
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
  filterBizTag: '',
  selectedNames: new Set(),

  // Subscriptions
  subscriptions: [],
  subscriptionMap: {},
  subscriptionLoading: false,
  subscriptionSaving: false,

  // Detail
  currentDetail: null,
  detailLoading: false,

  // Error
  error: null,

  fetchList: async (namespaceId: string) => {
    set({ loading: true, error: null });
    try {
      const { searchName, pageNo, pageSize, orderBy, filterOwner, filterScope, filterBizTag } = get();
      const response = await skillApi.list({
        namespaceId,
        skillName: searchName || undefined,
        search: searchName ? 'blur' : undefined,
        orderBy: orderBy || undefined,
        owner: filterOwner || undefined,
        scope: filterScope || undefined,
        bizTag: filterBizTag || undefined,
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

  fetchSubscriptions: async (namespaceId: string) => {
    set({
      subscriptionLoading: true,
      subscriptions: [],
      subscriptionMap: {},
      error: null,
    });
    try {
      const response = await skillApi.listSubscriptions(namespaceId);
      const { subscriptions } = getSubscriptionsFromResponse(response);
      set({
        subscriptions,
        subscriptionMap: toSubscriptionMap(subscriptions),
        subscriptionLoading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        subscriptionLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch skill subscriptions',
      });
    }
  },

  subscribeSkills: async (namespaceId: string, names: string[]) => {
    const subscriptions = names.filter(Boolean).map((name) => ({ name }));
    if (subscriptions.length === 0) return;
    set({ subscriptionSaving: true, error: null });
    try {
      const response = await skillApi.subscribe(namespaceId, subscriptions);
      const parsed = getSubscriptionsFromResponse(response);
      const nextSubscriptions = parsed.found
        ? parsed.subscriptions
        : Array.from(
          new Map(
            [...get().subscriptions, ...subscriptions].map((item) => [item.name, item]),
          ).values(),
        );
      set({
        subscriptions: nextSubscriptions,
        subscriptionMap: toSubscriptionMap(nextSubscriptions),
        subscriptionSaving: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        subscriptionSaving: false,
        error: axiosError.response?.data?.message || 'Failed to subscribe skills',
      });
      throw error;
    }
  },

  unsubscribeSkills: async (namespaceId: string, names: string[]) => {
    const skillNames = names.filter(Boolean);
    if (skillNames.length === 0) return;
    set({ subscriptionSaving: true, error: null });
    try {
      const response = await skillApi.unsubscribe(namespaceId, skillNames);
      const parsed = getSubscriptionsFromResponse(response);
      let subscriptions = parsed.found
        ? parsed.subscriptions
        : get().subscriptions.filter((item) => !skillNames.includes(item.name));
      const latest = getSubscriptionsFromResponse(await skillApi.listSubscriptions(namespaceId));
      if (latest.found) {
        subscriptions = latest.subscriptions;
      }
      set({
        subscriptions,
        subscriptionMap: toSubscriptionMap(subscriptions),
        subscriptionSaving: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        subscriptionSaving: false,
        error: axiosError.response?.data?.message || 'Failed to unsubscribe skills',
      });
      throw error;
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
    set({ searchName: '', orderBy: '', filterOwner: '', filterScope: '', filterBizTag: '', pageNo: 1 });
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
