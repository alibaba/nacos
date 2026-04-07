import { create } from 'zustand';
import { getLanguage, setLanguage as saveLanguage, getTheme, setTheme as saveTheme, getSidebarCollapsed, setSidebarCollapsed as saveSidebarCollapsed } from '@/lib/storage';

type Theme = 'light' | 'dark';
type Language = 'zh-CN' | 'en-US';

interface AppState {
  sidebarCollapsed: boolean;
  theme: Theme;
  language: Language;
}

interface AppActions {
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  setTheme: (theme: Theme) => void;
  setLanguage: (language: Language) => void;
  initFromStorage: () => void;
}

type AppStore = AppState & AppActions;

export const useAppStore = create<AppStore>((set) => ({
  // State with defaults
  sidebarCollapsed: false,
  theme: 'light',
  language: 'zh-CN',

  // Actions
  toggleSidebar: () => {
    set((state) => {
      const newCollapsed = !state.sidebarCollapsed;
      saveSidebarCollapsed(newCollapsed);
      return { sidebarCollapsed: newCollapsed };
    });
  },

  setSidebarCollapsed: (collapsed: boolean) => {
    saveSidebarCollapsed(collapsed);
    set({ sidebarCollapsed: collapsed });
  },

  setTheme: (theme: Theme) => {
    saveTheme(theme);
    // Apply theme to document
    document.documentElement.classList.remove('light', 'dark');
    document.documentElement.classList.add(theme);
    set({ theme });
  },

  setLanguage: (language: Language) => {
    saveLanguage(language);
    set({ language });
  },

  initFromStorage: () => {
    const sidebarCollapsed = getSidebarCollapsed();
    const theme = getTheme();
    const language = getLanguage();

    // Apply theme to document
    document.documentElement.classList.remove('light', 'dark');
    document.documentElement.classList.add(theme);

    set({
      sidebarCollapsed,
      theme,
      language,
    });
  },
}));
