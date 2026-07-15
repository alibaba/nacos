import { create } from 'zustand';
import { serviceApi } from '@/api/service';
import client from '@/api/client';
import type { ServiceView, ServiceListResponse, ServiceDetailInfo } from '@/types/service';
import type { AxiosError } from 'axios';

interface SearchParams {
  serviceNameParam: string;
  groupNameParam: string;
  ignoreEmptyService: boolean;
}

interface ServiceState {
  // List
  services: ServiceView[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;

  // Search
  serviceNameParam: string;
  groupNameParam: string;
  ignoreEmptyService: boolean;

  // Detail
  currentService: ServiceDetailInfo | null;
  detailLoading: boolean;

  // Selector types
  selectorTypes: string[];

  // Error
  error: string | null;
}

interface ServiceActions {
  fetchServices: (namespaceId: string) => Promise<void>;
  setSearchParams: (params: Partial<SearchParams>) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  fetchServiceDetail: (namespaceId: string, serviceName: string, groupName: string) => Promise<void>;
  deleteService: (namespaceId: string, serviceName: string, groupName: string) => Promise<{ ok: boolean; reason?: string }>;
  fetchSelectorTypes: () => Promise<void>;
  clearCurrentService: () => void;
  clearError: () => void;
}

type ServiceStore = ServiceState & ServiceActions;

const defaultSearchParams: SearchParams = {
  serviceNameParam: '',
  groupNameParam: '',
  ignoreEmptyService: true,
};

export const useServiceStore = create<ServiceStore>((set, get) => ({
  // List
  services: [],
  loading: false,
  total: 0,
  pageNo: 1,
  pageSize: 10,

  // Search
  ...defaultSearchParams,

  // Detail
  currentService: null,
  detailLoading: false,

  // Selector types
  selectorTypes: [],

  // Error
  error: null,

  fetchServices: async (namespaceId: string) => {
    set({ loading: true, error: null });
    try {
      const { serviceNameParam, groupNameParam, ignoreEmptyService, pageNo, pageSize } = get();
      const response = await serviceApi.listServices({
        namespaceId,
        serviceNameParam: serviceNameParam || undefined,
        groupNameParam: groupNameParam || undefined,
        ignoreEmptyService,
        pageNo,
        pageSize,
      });
      const result = response as unknown as { data: ServiceListResponse };
      const data = result.data;
      set({
        services: data.pageItems || [],
        total: data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        loading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch services',
        services: [],
        total: 0,
      });
    }
  },

  setSearchParams: (params: Partial<SearchParams>) => {
    set((state) => ({ ...state, ...params, pageNo: 1 }));
  },

  setPage: (pageNo: number, pageSize?: number) => {
    set((state) => ({
      pageNo,
      pageSize: pageSize ?? state.pageSize,
    }));
  },

  resetSearch: () => {
    set({ ...defaultSearchParams, pageNo: 1 });
  },

  fetchServiceDetail: async (namespaceId: string, serviceName: string, groupName: string) => {
    set({ detailLoading: true, error: null, currentService: null });
    try {
      const response = await serviceApi.getService({ namespaceId, serviceName, groupName });
      const result = response as unknown as { data: ServiceDetailInfo };
      set({ currentService: result.data, detailLoading: false });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch service detail',
        currentService: null,
      });
    }
  },

  deleteService: async (namespaceId: string, serviceName: string, groupName: string) => {
    try {
      await client.delete('v3/console/ns/service', {
        params: { namespaceId, serviceName, groupName },
        silentError: true,
      } as never);
      return { ok: true };
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string; data?: string }>;
      const detail = typeof axiosError.response?.data?.data === 'string' ? axiosError.response.data.data : '';
      const reason = detail || axiosError.response?.data?.message || '';
      set({ error: reason });
      return { ok: false, reason };
    }
  },

  fetchSelectorTypes: async () => {
    try {
      const response = await serviceApi.getSelectorTypes();
      const result = response as unknown as { data: string[] };
      set({ selectorTypes: result.data || [] });
    } catch {
      // fallback
      set({ selectorTypes: ['none', 'label'] });
    }
  },

  clearCurrentService: () => {
    set({ currentService: null });
  },

  clearError: () => {
    set({ error: null });
  },
}));
