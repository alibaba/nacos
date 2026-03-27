import { Outlet, useLocation } from 'react-router-dom';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';
import { useAppStore } from '@/stores/app-store';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useEffect, useLayoutEffect } from 'react';
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

  // Clear stale scroll locks from react-remove-scroll on route change.
  //
  // react-remove-scroll locks page scroll via a <style> element whose CSS
  // selector is `body[data-scroll-locked] { overflow:hidden!important }`.
  // When the route changes while an overlay is animating out, the library's
  // useEffect cleanup can race with the transition, leaving the attribute stuck.
  //
  // useLayoutEffect runs synchronously after DOM mutations but BEFORE paint,
  // so we remove the attribute before the browser renders the new page.
  // The library's own useEffect cleanup runs later and handles the rest
  // (removing the <style> element and decrementing its internal counter).
  useLayoutEffect(() => {
    const html = document.documentElement;
    const { body } = document;
    html.removeAttribute('data-scroll-locked');
    body.removeAttribute('data-scroll-locked');
    for (const el of [html, body]) {
      el.style.removeProperty('overflow');
      el.style.removeProperty('padding-right');
      el.style.removeProperty('margin-right');
    }
  }, [location.pathname]);

  // Scroll to top on navigation (passive effect is fine for this)
  useEffect(() => {
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
