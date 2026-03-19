import { create } from 'zustand';
import { namespaceApi, type Namespace } from '@/api';

interface NamespaceState {
  currentNamespace: string;
  namespaceShowName: string;
  namespaces: Namespace[];
  loading: boolean;
  error: string | null;
}

interface NamespaceActions {
  setNamespace: (id: string, showName: string) => void;
  fetchNamespaces: () => Promise<void>;
  getCurrentNamespace: () => string;
}

type NamespaceStore = NamespaceState & NamespaceActions;

const getDefaultNamespace = (): string => {
  // Try to get from URL params first
  const hash = window.location.hash;
  const match = hash.match(/[?&]namespace=([^&]*)/);
  if (match) {
    return decodeURIComponent(match[1]);
  }
  return '';
};

export const useNamespaceStore = create<NamespaceStore>((set, get) => ({
  // State
  currentNamespace: getDefaultNamespace(),
  namespaceShowName: '',
  namespaces: [],
  loading: false,
  error: null,

  // Actions
  setNamespace: (id: string, showName: string) => {
    set({
      currentNamespace: id,
      namespaceShowName: showName,
    });
  },

  fetchNamespaces: async () => {
    set({ loading: true, error: null });
    try {
      const response = await namespaceApi.list();
      // Response interceptor already unwraps response.data (returns HTTP body)
      // Body structure: { code: 0, data: [...namespaces] }
      const body = response as unknown as { code: number; data: Namespace[] };
      const namespaces = body.data || [];
      
      // If no current namespace set, use the first one or empty
      const currentNamespace = get().currentNamespace;
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
