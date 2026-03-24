import { Outlet, useLocation } from 'react-router-dom';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';
import { useAppStore } from '@/stores/app-store';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useEffect } from 'react';
import { cn } from '@/lib/utils';

export default function AppLayout() {
  const { sidebarCollapsed } = useAppStore();
  const { loadFromStorage } = useAuthStore();
  const { fetchState } = useServerStore();
  const { fetchNamespaces } = useNamespaceStore();
  const location = useLocation();

  useEffect(() => {
    loadFromStorage();
    fetchState();
    fetchNamespaces();
  }, [loadFromStorage, fetchState, fetchNamespaces]);

  // Clear stale Radix scroll locks on route change.
  // When a Dialog/Sheet/Select is closing (animating out) and the route changes,
  // react-remove-scroll may leave overflow:hidden + data-scroll-locked on <html>.
  useEffect(() => {
    const html = document.documentElement;
    if (html.hasAttribute('data-scroll-locked')) {
      html.removeAttribute('data-scroll-locked');
      html.style.removeProperty('overflow');
      html.style.removeProperty('padding-right');
    }
    // Also clean up body in case of older Radix versions
    const { body } = document;
    if (body.hasAttribute('data-scroll-locked')) {
      body.removeAttribute('data-scroll-locked');
      body.style.removeProperty('overflow');
      body.style.removeProperty('padding-right');
    }
    // Scroll to top on navigation
    window.scrollTo(0, 0);
  }, [location.pathname]);

  return (
    <TooltipProvider delayDuration={200}>
      <div className="min-h-screen bg-background">
        <Sidebar />
        <div
          className={cn(
            'transition-all duration-300',
            sidebarCollapsed ? 'ml-16' : 'ml-64'
          )}
        >
          <Header />
          <main className="p-6">
            <Outlet />
          </main>
        </div>
      </div>
    </TooltipProvider>
  );
}
