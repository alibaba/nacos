import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import qs from 'qs';
import { toast } from 'sonner';

// Auth endpoints that don't need username param
const AUTH_ENDPOINTS = [
  'v3/auth/',
  'v1/auth',
];

// Endpoints that should fail silently (no toast) when backend is unavailable
const SILENT_ENDPOINTS = [
  'v3/console/server/state',
  'v3/console/server/guide',
  'v3/console/server/announcement',
  'v3/console/core/namespace',
  'v3/auth/user/login',
  'v3/auth/user/admin',
];
const SESSION_EXPIRED_MESSAGES = [
  'unknown user!',
  'token invalid!',
  'token expired!',
  'session expired!',
];

/**
 * Compute the console servlet context path from current URL.
 * Strips /next/ or /legacy/ suffix to get the context path prefix.
 */
function getContextPath(): string {
  return window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
}

const client = axios.create({
  baseURL: getContextPath(),
  timeout: 10000,
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
});

// Request interceptor
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add accessToken header from localStorage.token
    try {
      const tokenStr = localStorage.getItem('token');
      if (tokenStr) {
        const tokenData = JSON.parse(tokenStr);
        if (tokenData.accessToken) {
          config.headers.accessToken = tokenData.accessToken;
        }
      }
    } catch {
      // Ignore JSON parse errors
    }

    // Add username param for non-auth endpoints
    const url = config.url || '';
    const isAuthEndpoint = AUTH_ENDPOINTS.some(endpoint => url.includes(endpoint));
    if (!isAuthEndpoint) {
      try {
        const tokenStr = localStorage.getItem('token');
        if (tokenStr) {
          const tokenData = JSON.parse(tokenStr);
          if (tokenData.username) {
            config.params = {
              ...config.params,
              username: tokenData.username,
            };
          }
        }
      } catch {
        // Ignore JSON parse errors
      }
    }

    // Serialize POST/PUT plain object data to application/x-www-form-urlencoded
    if (
      (config.method === 'post' || config.method === 'put') &&
      config.data &&
      typeof config.data === 'object' &&
      !(config.data instanceof FormData) &&
      !(config.data instanceof URLSearchParams) &&
      !config.headers?.['Content-Type']?.toString().includes('application/json')
    ) {
      config.data = qs.stringify(config.data);
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
client.interceptors.response.use(
  (response) => {
    // Check for auth_admin_request in server state responses
    if (response.data && typeof response.data === 'object') {
      // Handle auth_admin_request flag
      if ('auth_admin_request' in response.data) {
        // This is handled by the server store
      }
    }
    return response.data;
  },
  (error: AxiosError<{ message?: string; data?: string }>) => {
    const status = error.response?.status;
    const message = error.response?.data?.message?.toLowerCase() || '';
    const dataStr = typeof error.response?.data?.data === 'string'
      ? error.response.data.data.toLowerCase()
      : '';
    
    // Handle 401/403 with session expired messages
    // Check both `message` and `data` fields since the server may wrap
    // the real reason (e.g. "token expired!") inside the `data` field
    // while `message` is a generic "access denied".
    if (status === 401 || status === 403) {
      const isSessionExpired = SESSION_EXPIRED_MESSAGES.some(
        msg => message.includes(msg) || dataStr.includes(msg)
      );
      if (isSessionExpired) {
        localStorage.removeItem('token');
        window.location.hash = '#/login';
        toast.error('Session expired, please login again');
        return Promise.reject(error);
      }
    }

    // Show error toast (skip for silent endpoints)
    const requestUrl = error.config?.url || '';
    const isSilent = SILENT_ENDPOINTS.some(ep => requestUrl.includes(ep));
    if (!isSilent) {
      if (status === 403) {
        toast.error('无权限执行此操作 / No permission');
      } else {
        const errorMessage = error.response?.data?.message || error.message || 'Request failed';
        toast.error(errorMessage);
      }
    }

    return Promise.reject(error);
  }
);

export default client;
