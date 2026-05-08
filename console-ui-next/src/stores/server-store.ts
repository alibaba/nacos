import { create } from 'zustand';
import { serverApi } from '@/api';

interface ServerState {
  version: string;
  standaloneMode: string;
  functionMode: string;
  loginPageEnabled: boolean;
  authEnabled: boolean;
  consoleUiEnable: boolean;
  startupMode: string;
  configRetentionDays: number;
  authSystemType: string;
  authAdminRequest: boolean;
  copilotEnabled: boolean;
  aiEnabled: boolean;
  notice: string;
  guideMsg: string;
  loading: boolean;
  stateLoaded: boolean;
  error: string | null;
}

interface ServerActions {
  fetchState: () => Promise<void>;
  fetchNotice: (language: string) => Promise<void>;
  fetchGuide: () => Promise<void>;
}

type ServerStore = ServerState & ServerActions;

const initialState: ServerState = {
  version: '',
  standaloneMode: '',
  functionMode: '',
  loginPageEnabled: true,
  authEnabled: false,
  consoleUiEnable: true,
  startupMode: '',
  configRetentionDays: 0,
  authSystemType: '',
  authAdminRequest: false,
  copilotEnabled: false,
  aiEnabled: true,
  notice: '',
  guideMsg: '',
  loading: false,
  stateLoaded: false,
  error: null,
};

export const useServerStore = create<ServerStore>((set) => ({
  ...initialState,

  fetchState: async () => {
    set({ loading: true, error: null });
    try {
      const response = await serverApi.getState();
      // Response interceptor already unwraps response.data (returns HTTP body)
      // Server state API returns a flat object directly (no {code, data} wrapper)
      const data = response as unknown as {
        version?: string;
        standalone_mode?: string;
        function_mode?: string;
        login_page_enabled?: string | boolean;
        auth_enabled?: string | boolean;
        console_ui_enabled?: string | boolean;
        startup_mode?: string;
        config_retention_days?: string | number;
        auth_admin_request?: string | boolean;
        auth_system_type?: string;
        copilot_enabled?: string | boolean;
        ai_enabled?: string | boolean;
      };
      
      set({
        version: data.version || '',
        standaloneMode: data.standalone_mode || '',
        functionMode: data.function_mode || '',
        loginPageEnabled: String(data.login_page_enabled) === 'true',
        authEnabled: String(data.auth_enabled) === 'true',
        consoleUiEnable: String(data.console_ui_enabled) === 'true',
        startupMode: data.startup_mode || '',
        configRetentionDays: Number(data.config_retention_days) || 0,
        authSystemType: data.auth_system_type || '',
        authAdminRequest: String(data.auth_admin_request) === 'true',
        copilotEnabled: String(data.copilot_enabled) === 'true',
        aiEnabled: data.ai_enabled === undefined ? true : String(data.ai_enabled) === 'true',
        loading: false,
        stateLoaded: true,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to fetch server state';
      set({ loading: false, error: message });
    }
  },

  fetchNotice: async (language: string) => {
    try {
      const response = await serverApi.getAnnouncement(language);
      // Body: { code: 0, data: "..." }
      const body = response as unknown as { code: number; data: string };
      set({ notice: body.data || '' });
    } catch {
      set({ notice: '' });
    }
  },

  fetchGuide: async () => {
    try {
      const response = await serverApi.getGuide();
      // Body: { code: 0, data: "..." }
      const body = response as unknown as { code: number; data: string };
      set({ guideMsg: body.data || '' });
    } catch {
      set({ guideMsg: '' });
    }
  },
}));
