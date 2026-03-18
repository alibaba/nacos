const LANGUAGE_KEY = 'docsite_language';
const THEME_KEY = 'nacos_theme';
const SIDEBAR_KEY = 'nacos_sidebar_collapsed';
const TOKEN_KEY = 'token';
const LOGIN_PAGE_ENABLED_KEY = 'login_page_enabled';

// Language
export function getLanguage(): 'zh-CN' | 'en-US' {
  const lang = localStorage.getItem(LANGUAGE_KEY);
  if (lang === 'zh-CN' || lang === 'en-US') {
    return lang;
  }
  // Detect browser language
  const browserLang = navigator.language || (navigator as { userLanguage?: string }).userLanguage;
  if (browserLang?.startsWith('zh')) {
    return 'zh-CN';
  }
  return 'en-US';
}

export function setLanguage(language: 'zh-CN' | 'en-US'): void {
  localStorage.setItem(LANGUAGE_KEY, language);
}

// Theme
export function getTheme(): 'light' | 'dark' {
  const theme = localStorage.getItem(THEME_KEY);
  if (theme === 'light' || theme === 'dark') {
    return theme;
  }
  return 'light';
}

export function setTheme(theme: 'light' | 'dark'): void {
  localStorage.setItem(THEME_KEY, theme);
}

// Sidebar
export function getSidebarCollapsed(): boolean {
  const collapsed = localStorage.getItem(SIDEBAR_KEY);
  return collapsed === 'true';
}

export function setSidebarCollapsed(collapsed: boolean): void {
  localStorage.setItem(SIDEBAR_KEY, String(collapsed));
}

// Token
export interface TokenData {
  accessToken: string;
  username: string;
  globalAdmin: boolean;
}

export function getToken(): TokenData | null {
  try {
    const tokenStr = localStorage.getItem(TOKEN_KEY);
    if (tokenStr) {
      return JSON.parse(tokenStr) as TokenData;
    }
  } catch {
    // Ignore parse errors
  }
  return null;
}

export function setToken(data: TokenData): void {
  localStorage.setItem(TOKEN_KEY, JSON.stringify(data));
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

// Login Page Enabled
export function getLoginPageEnabled(): boolean {
  return localStorage.getItem(LOGIN_PAGE_ENABLED_KEY) === 'true';
}

export function setLoginPageEnabled(enabled: boolean): void {
  localStorage.setItem(LOGIN_PAGE_ENABLED_KEY, String(enabled));
}

// Clear all storage
export function clearStorage(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(LOGIN_PAGE_ENABLED_KEY);
  // Keep language and theme preferences
}
