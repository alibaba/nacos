import { create } from 'zustand';
import { mcpApi } from '@/api/mcp';
import type {
  McpServerBasicInfo,
  McpServerDetailInfo,
  McpListResponse,
  McpSearchMode,
} from '@/types/mcp';
import type { AxiosError } from 'axios';

interface McpState {
  // List
  mcpServers: McpServerBasicInfo[];
  loading: boolean;
  total: number;
  pageNo: number;
  pageSize: number;

  // Search
  searchName: string;
  searchMode: McpSearchMode;

  // Selection (batch operations)
  selectedNames: Set<string>;

  // Detail
  currentMcp: McpServerDetailInfo | null;
  detailLoading: boolean;
  selectedVersion: string | null;

  // Import
  importDialogOpen: boolean;

  // Error
  error: string | null;
}

interface McpActions {
  fetchMcpServers: (namespaceId: string) => Promise<void>;
  fetchMcpDetail: (namespaceId: string, mcpName: string, version?: string) => Promise<void>;
  deleteMcpServer: (namespaceId: string, mcpName: string) => Promise<boolean>;
  batchDelete: (namespaceId: string, names: string[]) => Promise<boolean>;
  toggleEnabled: (namespaceId: string, mcp: McpServerDetailInfo) => Promise<boolean>;
  setSearchParams: (params: { searchName?: string; searchMode?: McpSearchMode }) => void;
  setPage: (pageNo: number, pageSize?: number) => void;
  resetSearch: () => void;
  setSelectedVersion: (version: string | null) => void;
  toggleSelect: (name: string) => void;
  selectAll: (names: string[]) => void;
  clearSelection: () => void;
  openImportDialog: () => void;
  closeImportDialog: () => void;
  clearCurrentMcp: () => void;
  clearError: () => void;
}

type McpStore = McpState & McpActions;

export const useMcpStore = create<McpStore>((set, get) => ({
  // List
  mcpServers: [],
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
  currentMcp: null,
  detailLoading: false,
  selectedVersion: null,

  // Import
  importDialogOpen: false,

  // Error
  error: null,

  fetchMcpServers: async (namespaceId: string) => {
    // Only show loading skeleton on initial empty state; keep existing data visible during re-fetches
    const hasData = get().mcpServers.length > 0;
    set({ loading: !hasData, error: null });
    try {
      const { searchName, searchMode, pageNo, pageSize } = get();
      const response = await mcpApi.listMcpServers({
        mcpName: searchName || undefined,
        namespaceId,
        search: searchMode,
        pageNo,
        pageSize,
      });
      const result = response as unknown as { data: McpListResponse };
      const data = result.data;
      set({
        mcpServers: data.pageItems || [],
        total: data.totalCount || 0,
        loading: false,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        loading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch MCP servers',
        mcpServers: [],
        total: 0,
      });
    }
  },

  fetchMcpDetail: async (namespaceId: string, mcpName: string, version?: string) => {
    // Only show loading skeleton when no data; keep current data visible during re-fetches
    const hasMcp = get().currentMcp !== null;
    set({ detailLoading: !hasMcp, error: null });
    try {
      const response = await mcpApi.getMcpServer({
        mcpName,
        version,
        namespaceId,
      });
      const result = response as unknown as { data: McpServerDetailInfo };
      set({
        currentMcp: result.data,
        detailLoading: false,
        selectedVersion: version || result.data?.versionDetail?.version || null,
      });
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({
        detailLoading: false,
        error: axiosError.response?.data?.message || 'Failed to fetch MCP server detail',
        currentMcp: null,
      });
    }
  },

  deleteMcpServer: async (namespaceId: string, mcpName: string) => {
    try {
      await mcpApi.deleteMcpServer({ mcpName, namespaceId });
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to delete MCP server' });
      return false;
    }
  },

  batchDelete: async (namespaceId: string, names: string[]) => {
    let allSuccess = true;
    for (const name of names) {
      try {
        await mcpApi.deleteMcpServer({ mcpName: name, namespaceId });
      } catch {
        allSuccess = false;
      }
    }
    set({ selectedNames: new Set() });
    return allSuccess;
  },

  toggleEnabled: async (namespaceId: string, mcp: McpServerDetailInfo) => {
    try {
      const toggled = { ...mcp, enabled: !mcp.enabled };
      const serverSpec = { ...toggled };
      // Remove detail-only fields before serializing as serverSpecification
      const { toolSpec, backendEndpoints, frontendEndpoints, allVersions, ...basicInfo } = serverSpec;
      await mcpApi.updateMcpServer({
        mcpName: mcp.name,
        namespaceId,
        serverSpecification: JSON.stringify(basicInfo),
        toolSpecification: toolSpec ? JSON.stringify(toolSpec) : undefined,
        endpointSpecification: backendEndpoints
          ? JSON.stringify({
              type: mcp.remoteServerConfig?.serviceRef ? 'REF' : 'DIRECT',
              data: mcp.remoteServerConfig?.serviceRef || (backendEndpoints[0] ?? {}),
            })
          : undefined,
        latest: true,
      });
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      set({ error: axiosError.response?.data?.message || 'Failed to toggle MCP server' });
      return false;
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
    set({ searchName: '', searchMode: 'blur', pageNo: 1 });
  },

  setSelectedVersion: (version) => {
    set({ selectedVersion: version });
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

  openImportDialog: () => set({ importDialogOpen: true }),
  closeImportDialog: () => set({ importDialogOpen: false }),

  clearCurrentMcp: () => {
    set({ currentMcp: null, selectedVersion: null });
  },

  clearError: () => {
    set({ error: null });
  },
}));
