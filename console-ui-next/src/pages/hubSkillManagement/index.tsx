import { HubResourceCard } from '@/components/ai/hub/HubResourceCard';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, ChevronLeft, ChevronRight, Search, Tag, Wand2, RefreshCw } from 'lucide-react';
import { hubSkillApi } from '@/api/hub-skill';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { SkillManagementTabs } from '@/components/ai/skill/SkillManagementTabs';
import { hubDetailPath, hubErrorMessage, distinctHubEntries } from '@/lib/hub-skill-utils';
import type { HubSkillSummary } from '@/types/hub-skill';

const PAGE_SIZE = 12;

/** Shared Skill directory within Skill management; uses the type-specific Console facade. */
export default function HubSkillManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [queryInput, setQueryInput] = useState('');
  const [tagInput, setTagInput] = useState('');
  const [query, setQuery] = useState('');
  const [bizTag, setBizTag] = useState('');
  const [orderBy, setOrderBy] = useState<'' | 'download_count'>('');
  const [pageNo, setPageNo] = useState(1);
  const [data, setData] = useState<HubSkillSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [refreshId, setRefreshId] = useState(0);

  const refresh = useCallback(() => setRefreshId(x => x + 1), []);
  const search = useCallback(() => {
    setQuery(queryInput.trim());
    setBizTag(tagInput.trim());
    setPageNo(1);
    refresh();
  }, [queryInput, tagInput, refresh]);

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(() => {
      if (cancelled) return;
      setLoading(true);
      setError('');
      return hubSkillApi.list({ query: query || undefined, bizTag: bizTag || undefined,
        orderBy: orderBy || undefined, pageNo, pageSize: PAGE_SIZE });
    })
      .then(response => {
        if (cancelled || !response) return;
        const items = response.data.pageItems ?? [];
        // Fail closed on malformed/publicly ambiguous IDs.
        if (!distinctHubEntries(items)) throw new Error('Invalid or duplicated Hub ID');
        const count = response.data.totalCount;
        if (!Number.isSafeInteger(count) || count < 0) throw new Error('Invalid Hub total');
        const lastPage = Math.max(1, Math.ceil(count / PAGE_SIZE));
        if (pageNo > lastPage) { setPageNo(lastPage); return; }
        setData(items);
        setTotal(response.data.totalCount ?? 0);
      })
      .catch(err => {
        if (cancelled) return;
        setData([]);
        setTotal(0);
        setError(hubErrorMessage(err, t('hub.requestFailed')));
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [query, bizTag, orderBy, pageNo, refreshId, t]);

  const errorText = error === 'permission' ? t('hub.authRequired')
    : error === 'unavailable' ? t('hub.unavailable') : t('hub.requestFailed');
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="space-y-5 relative">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight">{t('hub.catalogTitle', { managementTitle: t('skill.title') })}</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {t('skill.totalSkills', { total })}
          </p>
        </div>
        <Button size="sm" variant="outline" onClick={refresh} disabled={loading}>
          <RefreshCw className="mr-1.5 h-3.5 w-3.5" />{t('common.refresh')}
        </Button>
      </div>
      <SkillManagementTabs shared />
      <div className="flex w-full min-w-0 items-center gap-2 overflow-x-auto px-0.5 py-2">
        <div className="relative min-w-[12rem] flex-1 max-w-md">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <Input aria-label={t('hub.search')} className="pl-8 h-8 text-sm" value={queryInput}
            placeholder={t('skill.searchPlaceholder')}
            onChange={e => setQueryInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') search(); }} />
        </div>
        <div className="relative w-[10.5rem] shrink-0">
          <Tag className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <Input aria-label={t('hub.tags')} className="pl-8 h-8 text-sm" value={tagInput}
            placeholder={t('skill.filterBizTagPlaceholder')} onChange={e => setTagInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') search(); }} />
        </div>
        <Button size="sm" variant="secondary" className="h-8 shrink-0" onClick={search}>{t('common.search')}</Button>
        <Select value={orderBy || 'default'} onValueChange={value => {
          setOrderBy(value === 'download_count' ? 'download_count' : '');
          setPageNo(1);
        }}>
          <SelectTrigger className="w-[8.5rem] h-8 text-xs shrink-0" aria-label={t('skill.sortDefault')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="default">{t('skill.sortDefault')}</SelectItem>
            <SelectItem value="download_count">{t('skill.sortByDownloads')}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {error ? (
        <div role="alert" className="rounded-xl border border-destructive/40 p-8 text-center space-y-3">
          <AlertTriangle className="mx-auto h-7 w-7 text-destructive" />
          <p className="text-sm">{errorText}</p>
          <Button variant="outline" size="sm" onClick={refresh}>{t('common.retry')}</Button>
        </div>
      ) : loading ? (
        <p className="py-16 text-center text-muted-foreground">{t('common.loading')}</p>
      ) : data.length === 0 ? (
        <p className="py-16 text-center text-muted-foreground">{t('hub.noResults')}</p>
      ) : (
        <div>
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs text-muted-foreground">{t('skill.totalSkills', { total: data.length })}</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {data.map(item => (
              <HubResourceCard key={item.hubEntryId} name={item.displayName} version={item.sharedVersion}
                sourceDisplayName={item.sourceDisplayName} description={item.description} author={item.author}
                tags={item.tags} downloadCount={item.downloadCount} updatedAt={item.sharedAt} icon={Wand2}
                gradient="from-violet-500 to-fuchsia-400 shadow-violet-500/15"
                onOpen={() => navigate(hubDetailPath(item.hubEntryId))} />
            ))}
          </div>
        </div>
      )}
      {!error && !loading && total > 0 && pages > 1 && (
        <div className="flex items-center justify-end gap-2 pt-1">
          <Button variant="outline" size="icon" className="h-8 w-8" aria-label={t('common.previous')} disabled={pageNo <= 1}
            onClick={() => setPageNo(p => Math.max(1, p - 1))}><ChevronLeft className="h-4 w-4" /></Button>
          <span className="text-xs text-muted-foreground px-1.5 tabular-nums">{pageNo} / {pages}</span>
          <Button variant="outline" size="icon" className="h-8 w-8" aria-label={t('common.next')} disabled={pageNo >= pages}
            onClick={() => setPageNo(p => p + 1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
      )}
    </div>
  );
}
