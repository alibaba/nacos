import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useServerStore } from '@/stores/server-store';
import { hubResourceConfig, resourceListPath, type HubResourceType } from '@/api/hub-resource';

export function ResourceManagementTabs({ type, shared = false }: { type: HubResourceType; shared?: boolean }) {
  const { t } = useTranslation();
  const { search } = useLocation();
  const enabled = useServerStore(state => state.hubEnabled);
  if (!enabled) return null;
  return <nav aria-label={t(hubResourceConfig[type].title)} className="flex gap-2 border-b pb-3">
    <Link to={{ pathname: hubResourceConfig[type].path, search }} aria-current={!shared ? 'page' : undefined}
      className={cn(buttonVariants({ variant: shared ? 'ghost' : 'secondary', size: 'sm' }))}>{t('hub.currentNamespace')}</Link>
    <Link to={{ pathname: resourceListPath(type), search }} aria-current={shared ? 'page' : undefined}
      className={cn(buttonVariants({ variant: shared ? 'secondary' : 'ghost', size: 'sm' }))}>{t('hubResource.shared', { resource: t(`hubResource.${type}`) })}</Link>
  </nav>;
}
