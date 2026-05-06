import { create } from 'zustand';
import { authApi } from '@/api';
import { getContextPath } from '@/lib/sse-utils';
import type { AxiosError } from 'axios';

interface TokenData {
  accessToken: string;
  username: string;
  globalAdmin: boolean;
}

interface AuthState {
  token: string | null;
  username: string | null;
  globalAdmin: boolean;
  oidc: boolean;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

interface AuthActions {
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  loadFromStorage: () => void;
  clearError: () => void;
  isOidcUser: () => boolean;
}

type AuthStore = AuthState & AuthActions;

export const useAuthStore = create<AuthStore>((set) => ({
  // State
  token: null,
  username: null,
  globalAdmin: false,
  oidc: false,
  isAuthenticated: false,
  loading: false,
  error: null,

  // Actions
  login: async (username: string, password: string) => {
    set({ loading: true, error: null });
    try {
      const response = await authApi.login({ username, password });
      // Response interceptor already unwraps response.data
      const data = response as unknown as TokenData;
      
      const tokenData: TokenData = {
        accessToken: data.accessToken,
        username: data.username || username,
        globalAdmin: data.globalAdmin || false,
      };
      
      localStorage.setItem('token', JSON.stringify(tokenData));
      
      set({
        token: data.accessToken,
        username: tokenData.username,
        globalAdmin: tokenData.globalAdmin,
        oidc: false,
        isAuthenticated: true,
        loading: false,
        error: null,
      });
      
      return true;
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string }>;
      const errorMessage = axiosError.response?.data?.message || 'Login failed';
      set({
        loading: false,
        error: errorMessage,
        isAuthenticated: false,
      });
      return false;
    }
  },

  logout: () => {
    const isOidc = useAuthStore.getState().oidc;
    localStorage.removeItem('token');
    sessionStorage.clear();
    set({
      token: null,
      username: null,
      globalAdmin: false,
      oidc: false,
      isAuthenticated: false,
      error: null,
    });
    if (isOidc) {
      window.location.href = getContextPath() + 'v1/auth/oidc/logout?redirect=true';
    } else {
      window.location.hash = '#/login';
    }
  },

  loadFromStorage: () => {
    try {
      const tokenStr = localStorage.getItem('token');
      if (tokenStr) {
        const tokenData: TokenData = JSON.parse(tokenStr);
        const parsed = tokenData as TokenData & { oidc?: boolean };
        set({
          token: parsed.accessToken,
          username: parsed.username,
          globalAdmin: parsed.globalAdmin,
          oidc: !!parsed.oidc,
          isAuthenticated: !!parsed.accessToken,
        });
      }
    } catch {
      set({
        token: null,
        username: null,
        globalAdmin: false,
        isAuthenticated: false,
      });
    }
  },

  clearError: () => {
    set({ error: null });
  },

  isOidcUser: (): boolean => {
    return useAuthStore.getState().oidc;
  },
}));
