import { Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { TooltipProvider, Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui/tooltip';
import { Sidebar } from '@/components/layout/sidebar';
import { Header } from '@/components/layout/header';
import { useAppStore } from '@/stores/app-store';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useEffect } from 'react';
import { cn } from '@/lib/utils';
import { ArrowLeftRight } from 'lucide-react';

export default function AppLayout() {
  const { sidebarCollapsed } = useAppStore();
  const { loadFromStorage } = useAuthStore();
  const { fetchState } = useServerStore();
  const { fetchNamespaces } = useNamespaceStore();
  const { t } = useTranslation();

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
        {/* UI version switch button */}
        <Tooltip>
          <TooltipTrigger asChild>
            <a
              href="../legacy/"
              className="fixed right-5 bottom-5 z-50 flex items-center gap-1.5 rounded-full bg-muted/80 backdrop-blur px-3 py-1.5 text-xs text-muted-foreground shadow-md border hover:bg-muted hover:text-foreground transition-colors"
            >
              <ArrowLeftRight className="h-3.5 w-3.5" />
              {t('common.legacyConsole')}
            </a>
          </TooltipTrigger>
          <TooltipContent>{t('common.switchToLegacy')}</TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );
}
