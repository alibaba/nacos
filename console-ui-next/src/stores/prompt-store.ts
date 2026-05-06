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
  PromptDraftCreateData,
  PromptDraftUpdateData,
  PromptSubmitData,
  PromptPublishData,
  PromptOnlineOfflineData,
  PromptLabelsUpdateData,
  PromptDescriptionUpdateData,
  PromptBizTagsUpdateData,
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

  // Detail (governance view)
  currentGovernance: PromptMetaInfo | null;
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
  fetchGovernanceDetail: (namespaceId: string, promptKey: string) => Promise<void>;
  fetchVersionDetail: (namespaceId: string, promptKey: string, version: string) => Promise<void>;
  fetchVersionList: (namespaceId: string, promptKey: string) => Promise<void>;

  // Lifecycle actions
  createDraft: (data: PromptDraftCreateData) => Promise<string | null>;
  updateDraft: (data: PromptDraftUpdateData) => Promise<boolean>;
  deleteDraft: (namespaceId: string, promptKey: string) => Promise<boolean>;
  submitVersion: (data: PromptSubmitData) => Promise<boolean>;
  publishVersion: (data: PromptPublishData) => Promise<boolean>;
  forcePublishVersion: (data: PromptPublishData) => Promise<boolean>;
  onlineVersion: (data: PromptOnlineOfflineData) => Promise<boolean>;
  offlineVersion: (data: PromptOnlineOfflineData) => Promise<boolean>;

  // Metadata update actions
  updateLabels: (data: PromptLabelsUpdateData) => Promise<boolean>;
  updateDescription: (data: PromptDescriptionUpdateData) => Promise<boolean>;
  updateBizTags: (data: PromptBizTagsUpdateData) => Promise<boolean>;

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
  currentGovernance: null,
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

  fetchGovernanceDetail: async (namespaceId: string, promptKey: string) => {
    set({ detailLoading: true, error: null });
    try {
      const response = await promptApi.getGovernanceDetail({ promptKey, namespaceId });
      const result = response as unknown as { data: PromptMetaInfo };
      set({ currentGovernance: result.data, detailLoading: false });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to fetch governance detail', detailLoading: false });
    }
  },

  fetchVersionDetail: async (namespaceId: string, promptKey: string, version: string) => {
    const hasVersion = get().currentVersion !== null;
    set({ detailLoading: !hasVersion, error: null });
    try {
      const response = await promptApi.getVersionDetail({ promptKey, version, namespaceId });
      const result = response as unknown as { data: PromptVersionInfo };
      set({ currentVersion: result.data, detailLoading: false });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch version detail',
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

  // --- Lifecycle actions ---

  createDraft: async (data: PromptDraftCreateData) => {
    try {
      const response = await promptApi.createDraft(data);
      const result = response as unknown as { data: string };
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return result.data;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to create draft' });
      return null;
    }
  },

  updateDraft: async (data: PromptDraftUpdateData) => {
    try {
      await promptApi.updateDraft(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to update draft' });
      return false;
    }
  },

  deleteDraft: async (namespaceId: string, promptKey: string) => {
    try {
      await promptApi.deleteDraft({ promptKey, namespaceId });
      await get().fetchGovernanceDetail(namespaceId, promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to delete draft' });
      return false;
    }
  },

  submitVersion: async (data: PromptSubmitData) => {
    try {
      await promptApi.submit(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to submit version' });
      return false;
    }
  },

  publishVersion: async (data: PromptPublishData) => {
    try {
      await promptApi.publish(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to publish version' });
      return false;
    }
  },

  forcePublishVersion: async (data: PromptPublishData) => {
    try {
      await promptApi.forcePublish(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to force publish version' });
      return false;
    }
  },

  onlineVersion: async (data: PromptOnlineOfflineData) => {
    try {
      await promptApi.online(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to online version' });
      return false;
    }
  },

  offlineVersion: async (data: PromptOnlineOfflineData) => {
    try {
      await promptApi.offline(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to offline version' });
      return false;
    }
  },

  // --- Metadata update actions ---

  updateLabels: async (data: PromptLabelsUpdateData) => {
    try {
      await promptApi.updateLabels(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to update labels' });
      return false;
    }
  },

  updateDescription: async (data: PromptDescriptionUpdateData) => {
    try {
      await promptApi.updateDescription(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to update description' });
      return false;
    }
  },

  updateBizTags: async (data: PromptBizTagsUpdateData) => {
    try {
      await promptApi.updateBizTags(data);
      const namespaceId = data.namespaceId || 'public';
      await get().fetchGovernanceDetail(namespaceId, data.promptKey);
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to update biz tags' });
      return false;
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
    set({ currentGovernance: null, currentVersion: null, versionList: [], versionsTotal: 0, versionsPageNo: 1 });
  },

  clearError: () => {
    set({ error: null });
  },
}));
