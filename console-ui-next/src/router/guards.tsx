import { Navigate, Outlet } from 'react-router-dom';
import { getToken } from '@/lib/storage';
import { useAuthStore } from '@/stores';

/**
 * AuthGuard - Protects routes that require authentication
 * Checks if token exists in localStorage, redirects to login if not
 */
export function AuthGuard() {
  const token = getToken();
  
  if (!token) {
    return <Navigate to="/login" replace />;
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
