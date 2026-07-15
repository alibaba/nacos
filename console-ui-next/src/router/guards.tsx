import { Navigate, Outlet } from 'react-router-dom';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getToken } from '@/lib/storage';
import { useAuthStore } from '@/stores';
import { useServerStore } from '@/stores/server-store';
import { AlertTriangle } from 'lucide-react';

export function getDefaultRoute(aiEnabled: boolean, functionMode: string) {
  const configAvailable = functionMode !== 'naming' && functionMode !== 'ai';
  const namingAvailable = functionMode !== 'config' && functionMode !== 'ai';
  const aiAvailable = aiEnabled && functionMode !== 'naming' && functionMode !== 'config' && functionMode !== 'microservice';
  if (aiAvailable) {
    return '/skill';
  }
  if (configAvailable) {
    return '/configurationManagement';
  }
  if (namingAvailable) {
    return '/serviceManagement';
  }
  return '/namespace';
}

/**
 * ConsoleDisabledPage - Shown when nacos.console.ui.enabled=false
 */
function ConsoleDisabledPage() {
  const { t } = useTranslation();
  const { guideMsg } = useServerStore();

  return (
    <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-blue-50 via-sky-100 to-blue-100">
      <div className="max-w-md w-full mx-4 bg-white rounded-xl shadow-lg p-8 text-center">
        <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-amber-100 mb-4">
          <AlertTriangle className="h-7 w-7 text-amber-600" />
        </div>
        <h1 className="text-xl font-semibold text-gray-800 mb-4">{t('login.consoleClosed')}</h1>
        {guideMsg && (
          <div
            className="text-sm text-gray-500 leading-relaxed [&_a]:text-blue-600 [&_a]:underline [&_a]:hover:text-blue-700 [&_code]:bg-gray-100 [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:rounded [&_code]:text-gray-700 [&_code]:font-mono [&_code]:text-xs"
            dangerouslySetInnerHTML={{ __html: guideMsg }}
          />
        )}
      </div>
    </div>
  );
}

/**
 * AuthGuard - Protects routes that require authentication
 * Also checks if console UI is enabled
 */
export function AuthGuard() {
  const token = getToken();
  const { consoleUiEnable, authEnabled, loginPageEnabled, stateLoaded, fetchState, fetchGuide } = useServerStore();

  // Fetch server state once on mount
  useEffect(() => {
    if (!stateLoaded) {
      fetchState();
      fetchGuide();
    }
  }, [stateLoaded, fetchState, fetchGuide]);

  // Wait for state to load before making any decision
  if (!stateLoaded) {
    return null;
  }

  if (!consoleUiEnable) {
    return <ConsoleDisabledPage />;
  }

  // When auth is disabled or login page is not enabled, allow access without token
  if (!authEnabled || !loginPageEnabled) {
    return <Outlet />;
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}

/**
 * AiGuard - Protects AI console routes when nacos.extension.ai.enabled=false
 */
export function AiGuard() {
  const { aiEnabled, functionMode, stateLoaded, fetchState } = useServerStore();

  useEffect(() => {
    if (!stateLoaded) {
      fetchState();
    }
  }, [stateLoaded, fetchState]);

  if (!stateLoaded) {
    return null;
  }

  if (!aiEnabled) {
    return <Navigate to={getDefaultRoute(false, functionMode)} replace />;
  }

  return <Outlet />;
}

/**
 * AdminGuard - Protects routes that require admin privileges
 * Checks if user has globalAdmin flag, shows access denied if not
 */
export function AdminGuard() {
  const { globalAdmin, isAuthenticated } = useAuthStore();
  
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  
  if (!globalAdmin) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <h1 className="text-2xl font-bold text-red-600 mb-4">Access Denied</h1>
          <p className="text-gray-600">You need administrator privileges to access this page.</p>
        </div>
      </div>
    );
  }
  
  return <Outlet />;
}

/**
 * GuestGuard - For routes that should only be accessible when not logged in
 * Redirects to home if already authenticated
 */
export function GuestGuard() {
  const token = getToken();
  
  if (token) {
    return <Navigate to="/" replace />;
  }
  
  return <Outlet />;
}
