import { create } from 'zustand';
import { namespaceApi, type Namespace } from '@/api';
import { HUB_SYSTEM_NAMESPACE } from '@/lib/hub-skill-utils';

export type NamespaceChangeGuard = (namespaceId: string, namespaceShowName: string) => boolean;

interface NamespaceState {
  currentNamespace: string;
  namespaceShowName: string;
  namespaces: Namespace[];
  loading: boolean;
  error: string | null;
  namespaceChangeGuard: NamespaceChangeGuard | null;
}

interface NamespaceActions {
  setNamespace: (id: string, showName: string) => void;
  syncNamespaceFromSearch: (search: string) => void;
  setNamespaceChangeGuard: (guard: NamespaceChangeGuard | null) => void;
  getNamespaceChangeGuard: () => NamespaceChangeGuard | null;
  fetchNamespaces: () => Promise<void>;
  getCurrentNamespace: () => string;
}

type NamespaceStore = NamespaceState & NamespaceActions;

export const getDefaultNamespaceFromHash = (hash: string): string => {
  const queryString = hash.split('?')[1];
  if (!queryString) {
    return '';
  }
  const params = new URLSearchParams(queryString);
  const namespace = params.get('namespace') || params.get('namespaceId') || '';
  return namespace === HUB_SYSTEM_NAMESPACE ? '' : namespace;
};

export const getNamespaceSearchAfterSwitch = (
  search: string,
  namespaceId: string,
  namespaceShowName: string,
): string | null => {
  const params = new URLSearchParams(search);
  const hasNamespaceParam = params.has('namespace') || params.has('namespaceId');
  if (!hasNamespaceParam) {
    return null;
  }
  if (params.has('namespace')) {
    params.set('namespace', namespaceId);
  }
  if (params.has('namespaceId')) {
    params.set('namespaceId', namespaceId);
  }
  if (params.has('namespaceShowName')) {
    params.set('namespaceShowName', namespaceShowName);
  }
  const queryString = params.toString();
  return queryString ? `?${queryString}` : '';
};

const getDefaultNamespace = (): string => {
  // Try to get from URL params first.
  if (typeof window === 'undefined') {
    return '';
  }
  return getDefaultNamespaceFromHash(window.location.hash);
};

export const useNamespaceStore = create<NamespaceStore>((set, get) => ({
  // State
  currentNamespace: getDefaultNamespace(),
  namespaceShowName: '',
  namespaces: [],
  loading: false,
  error: null,
  namespaceChangeGuard: null,

  // Actions
  setNamespace: (id: string, showName: string) => {
    if (id === HUB_SYSTEM_NAMESPACE) return;
    set({
      currentNamespace: id,
      namespaceShowName: showName,
    });
  },

  // Ordinary detail routes own their explicit namespace; shared routes do not call this.
  syncNamespaceFromSearch: (search: string) => {
    const params = new URLSearchParams(search);
    const namespaceId = params.get('namespaceId') || params.get('namespace');
    if (!namespaceId || namespaceId === HUB_SYSTEM_NAMESPACE) return;
    const state = get();
    const showName = state.namespaces.find(ns => ns.namespace === namespaceId)?.namespaceShowName
      || namespaceId;
    if (state.currentNamespace !== namespaceId || state.namespaceShowName !== showName) {
      state.setNamespace(namespaceId, showName);
    }
  },

  setNamespaceChangeGuard: (guard: NamespaceChangeGuard | null) => {
    set({
      namespaceChangeGuard: guard,
    });
  },

  getNamespaceChangeGuard: () => {
    return get().namespaceChangeGuard;
  },

  fetchNamespaces: async () => {
    set({ loading: true, error: null });
    try {
      const response = await namespaceApi.list();
      // Response interceptor already unwraps response.data (returns HTTP body)
      // Body structure: { code: 0, data: [...namespaces] }
      const body = response as unknown as { code: number; data: Namespace[] };
      const namespaces = (body.data || []).filter(ns => ns.namespace !== HUB_SYSTEM_NAMESPACE);
      
      // If no current namespace set, use the first one or empty
      const currentNamespace = get().currentNamespace === HUB_SYSTEM_NAMESPACE ? '' : get().currentNamespace;
      if (!currentNamespace && namespaces.length > 0) {
        const defaultNs = namespaces.find(ns => ns.namespace === 'public') || namespaces[0];
        set({
          namespaces,
          currentNamespace: defaultNs.namespace,
          namespaceShowName: defaultNs.namespaceShowName,
          loading: false,
        });
      } else {
        set({
          namespaces,
          currentNamespace,
          namespaceShowName: currentNamespace ? get().namespaceShowName : '',
          loading: false,
        });
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to fetch namespaces';
      set({ loading: false, error: message });
    }
  },

  getCurrentNamespace: () => {
    return get().currentNamespace;
  },
}));
