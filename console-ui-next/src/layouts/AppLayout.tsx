import { Outlet } from 'react-router-dom';
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

  useEffect(() => {
    loadFromStorage();
    fetchState();
    fetchNamespaces();
  }, [loadFromStorage, fetchState, fetchNamespaces]);

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
