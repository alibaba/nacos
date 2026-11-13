import { HubResourceCard } from '@/components/ai/hub/HubResourceCard';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Bot, Cpu, FileBox, MessageSquare, Search, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';
import { hubResourceApi, hubResourceConfig, resourceDetailPath, resourceTypeForPath, type SharedResourcePage } from '@/api/hub-resource';
import { ResourceManagementTabs } from '@/components/ai/hub/ResourceManagementTabs';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const PAGE_SIZE = 12;
export default function HubResourceManagementPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const type = resourceTypeForPath(location.pathname);
  const [input, setInput] = useState('');
  const [name, setName] = useState('');
  const [order, setOrder] = useState('default');
  const [page, setPage] = useState(1);
  const [reload, setReload] = useState(0);
  const [data, setData] = useState<SharedResourcePage>({ totalCount: 0, pageItems: [] });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(''); setData({ totalCount: 0, pageItems: [] });
    hubResourceApi.list(type, { name, pageNo: page, pageSize: PAGE_SIZE,
      orderBy: order === 'download_count' ? order : undefined })
      .then(result => { if (!cancelled) setData(result); })
      .catch(failure => { if (!cancelled) setError(failure instanceof Error ? failure.message : t('hub.requestFailed')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [type, name, order, page, reload, t]);
  const Icon = type === 'mcp' ? Cpu : type === 'agent' ? Bot : type === 'prompt' ? MessageSquare : FileBox;
  const gradient = type === 'prompt' ? 'from-amber-500 to-orange-400 shadow-amber-500/15' :
    type === 'mcp' ? 'from-blue-500 to-cyan-400 shadow-blue-500/15' : 'from-violet-500 to-fuchsia-400 shadow-violet-500/15';
  const search = () => { setName(input); setPage(1); setReload(value => value + 1); };
  const open = (id: string) => navigate(`${resourceDetailPath(type, id)}${location.search}`);
  return <div className="space-y-5">
    <div className="flex items-center justify-between"><div><h1 className="text-xl font-bold tracking-tight">{t('hubResource.catalogTitle', { resource: t(`hubResource.${type}`), managementTitle: t(hubResourceConfig[type].title) })}</h1>
      <p className="mt-0.5 text-sm text-muted-foreground">{t('hubResource.total', { count: data.totalCount })}</p></div>
      <Button size="sm" variant="outline" disabled={loading} onClick={() => setReload(value => value + 1)}><RefreshCw className="mr-1.5 h-3.5 w-3.5" />{t('common.refresh')}</Button></div>
    <ResourceManagementTabs type={type} shared />
    <div className="flex flex-wrap items-center gap-2">
      <div className="relative flex-1 min-w-[220px] max-w-md"><Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
        <Input className="pl-8 h-8 text-sm" value={input} placeholder={t('hubResource.search')} onChange={event => setInput(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') search(); }} /></div>
      <Button size="sm" variant="secondary" className="h-8" onClick={search}>{t('common.search')}</Button>
      <Select value={order} onValueChange={value => { setOrder(value); setPage(1); }}><SelectTrigger className="w-[8.5rem] h-8 text-xs" aria-label={t('skill.sortDefault')}><SelectValue /></SelectTrigger>
        <SelectContent><SelectItem value="default">{t('skill.sortDefault')}</SelectItem><SelectItem value="download_count">{t('skill.sortByDownloads')}</SelectItem></SelectContent></Select>
    </div>
    {error ? <div role="alert" className="rounded-xl border border-destructive/40 p-8 text-center space-y-3"><p className="text-sm">{error}</p>
      <Button size="sm" variant="outline" onClick={() => setReload(value => value + 1)}>{t('common.retry')}</Button></div> :
      loading ? <p className="py-16 text-center text-muted-foreground">{t('common.loading')}</p> :
      data.pageItems.length === 0 ? <p className="py-16 text-center text-muted-foreground">{t('hubResource.noResults')}</p> :
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">{data.pageItems.map(item =>
        <HubResourceCard key={item.hubEntryId} name={item.resourceName} version={item.version}
          protocol={type === 'mcp' ? item.frontProtocol || item.protocol || 'unknown' : undefined}
          sourceDisplayName={item.sourceDisplayName} description={item.description} author={item.author}
          downloadCount={item.downloadCount} updatedAt={item.updateTime} icon={Icon} gradient={gradient}
          onOpen={() => open(item.hubEntryId)} />)}</div>}
    {!loading && !error && data.totalCount > 0 && <div className="flex items-center justify-end gap-2 text-xs text-muted-foreground">
      <Button size="sm" variant="outline" aria-label={t('hubResource.previousPage')} disabled={page <= 1} onClick={() => setPage(value => value - 1)}><ChevronLeft className="h-4 w-4" /></Button>
      <span>{page} / {Math.max(1, Math.ceil(data.totalCount / PAGE_SIZE))}</span>
      <Button size="sm" variant="outline" aria-label={t('hubResource.nextPage')} disabled={page * PAGE_SIZE >= data.totalCount} onClick={() => setPage(value => value + 1)}><ChevronRight className="h-4 w-4" /></Button>
    </div>}
  </div>;
}
