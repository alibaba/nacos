import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import qs from 'qs';
import { toast } from 'sonner';
import i18n from '@/locales';

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
  'user not found',
  'token invalid!',
  'token expired!',
  'expired token',
  'session expired!',
  'invalid signature',
  'unsupported signature algorithm',
  'invalid token',
  'token is required',
  'token is empty',
  'token has expired',
  'token signature verification failed',
  'no valid oidc token found',
  'token audience validation failed',
  'token issuer mismatch',
  'token is not yet valid',
  'token processing error',
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
    // Add auth headers from localStorage.token
    // Send both Authorization (standard) and accessToken (Nacos custom) headers
    // to ensure compatibility with reverse proxies and different backend auth paths.
    try {
      const tokenStr = localStorage.getItem('token');
      if (tokenStr) {
        const tokenData = JSON.parse(tokenStr);
        if (tokenData.accessToken) {
          config.headers.set('Authorization', `Bearer ${tokenData.accessToken}`);
          config.headers.set('accessToken', tokenData.accessToken);
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

      // Handle session expired in success responses (HTTP 200 with business error code).
      // Some auth failures are returned with HTTP 200 but contain access denied info in the body,
      // e.g. when the filter writes the response body before setting the HTTP status code.
      const data = response.data as Record<string, unknown>;
      if (data.code && data.code !== 0) {
        const message = typeof data.message === 'string' ? data.message.toLowerCase() : '';
        const dataStr = typeof data.data === 'string' ? data.data.toLowerCase() : '';
        const combined = message + ' ' + dataStr;
        const isSessionExpired = SESSION_EXPIRED_MESSAGES.some(msg => combined.includes(msg));
        if (isSessionExpired) {
          localStorage.removeItem('token');
          window.location.hash = '#/login';
          toast.error(i18n.t('common.sessionExpired'));
          return Promise.reject(new Error('session expired'));
        }
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
        toast.error(i18n.t('common.sessionExpired'));
        return Promise.reject(error);
      }
    }

    // Show error toast (skip for silent endpoints)
    const requestUrl = error.config?.url || '';
    const isSilent = SILENT_ENDPOINTS.some(ep => requestUrl.includes(ep))
      || (error.config as unknown as Record<string, unknown>)?.silentError === true;
    if (!isSilent) {
      if (status === 403) {
        toast.error(i18n.t('common.noPermission'));
      } else {
        // Prefer the detailed error in `data` (e.g. "Service xxx is not empty, can't be delete.")
        // over the generic `message` (e.g. "service delete failure").
        const detail = typeof error.response?.data?.data === 'string' ? error.response.data.data : '';
        const errorMessage = detail || error.response?.data?.message || error.message || i18n.t('common.requestFailed');
        toast.error(errorMessage);
      }
    }

    return Promise.reject(error);
  }
);

export default client;
