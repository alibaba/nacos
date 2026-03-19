import { create } from 'zustand';
import { authApi } from '@/api';
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
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

interface AuthActions {
  login: (username: string, password: string) => Promise<boolean>;
  logout: () => void;
  loadFromStorage: () => void;
  clearError: () => void;
}

type AuthStore = AuthState & AuthActions;

export const useAuthStore = create<AuthStore>((set) => ({
  // State
  token: null,
  username: null,
  globalAdmin: false,
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
    localStorage.removeItem('token');
    set({
      token: null,
      username: null,
      globalAdmin: false,
      isAuthenticated: false,
      error: null,
    });
    window.location.hash = '#/login';
  },

  loadFromStorage: () => {
    try {
      const tokenStr = localStorage.getItem('token');
      if (tokenStr) {
        const tokenData: TokenData = JSON.parse(tokenStr);
        set({
          token: tokenData.accessToken,
          username: tokenData.username,
          globalAdmin: tokenData.globalAdmin,
          isAuthenticated: !!tokenData.accessToken,
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
}));
