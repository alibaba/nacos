import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, ArrowRight, ChevronLeft, ChevronRight, Search, Wand2, RefreshCw, Download, UserRound, Clock } from 'lucide-react';
import { hubSkillApi } from '@/api/hub-skill';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardFooter } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { hubDetailPath, hubErrorMessage, distinctHubEntries } from '@/lib/hub-skill-utils';
import type { HubSkillSummary } from '@/types/hub-skill';
import dayjs from 'dayjs';

const PAGE_SIZE = 12;

/** Dedicated Hub directory. Never queries the ordinary Skill API with the internal Hub namespace. */
export default function HubSkillManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [queryInput, setQueryInput] = useState('');
  const [tagInput, setTagInput] = useState('');
  const [query, setQuery] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [data, setData] = useState<HubSkillSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [refreshId, setRefreshId] = useState(0);

  const refresh = useCallback(() => setRefreshId(x => x + 1), []);
  const search = useCallback(() => {
    setQuery(queryInput.trim());
    setTags(tagInput.split(',').map(s => s.trim()).filter(Boolean));
    setPageNo(1);
    refresh();
  }, [queryInput, tagInput, refresh]);

  useEffect(() => {
    let cancelled = false;
    Promise.resolve().then(() => {
      if (cancelled) return;
      setLoading(true);
      setError('');
      return hubSkillApi.list({ query: query || undefined, tagsAll: tags, pageNo, pageSize: PAGE_SIZE });
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
  }, [query, tags, pageNo, refreshId, t]);

  const errorText = error === 'permission' ? t('hub.authRequired')
    : error === 'unavailable' ? t('hub.unavailable') : t('hub.requestFailed');
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="space-y-5 pb-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('hub.title')}</h1>
          <p className="text-sm text-muted-foreground mt-1">{t('hub.subtitle')}</p>
        </div>
        <Button size="sm" variant="outline" onClick={refresh} disabled={loading}>
          <RefreshCw className="mr-1.5 h-4 w-4" />{t('common.refresh')}
        </Button>
      </div>
      <div className="flex flex-wrap items-center gap-2 rounded-xl border bg-card p-3">
        <Search className="h-4 w-4 text-muted-foreground" />
        <Input aria-label={t('hub.search')} className="min-w-40 flex-1" value={queryInput}
          placeholder={t('hub.searchPlaceholder')}
          onChange={e => setQueryInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') search(); }} />
        <Input aria-label={t('hub.tags')} className="w-52" value={tagInput}
          placeholder={t('hub.tagsPlaceholder')} onChange={e => setTagInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') search(); }} />
        <Button size="sm" onClick={search}>{t('common.search')}</Button>
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
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          {data.map(item => (
            <Card key={item.hubEntryId}
              role="button" tabIndex={0} aria-label={`${t('hub.preview')}: ${item.displayName} @ ${item.sharedVersion}`}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); navigate(hubDetailPath(item.hubEntryId)); } }}
              className="group flex cursor-pointer flex-col gap-0 overflow-hidden py-0 transition-all hover:shadow-sm hover:border-primary/30"
              onClick={() => navigate(hubDetailPath(item.hubEntryId))}>
              <div className="flex items-start gap-3 px-4 pt-4 pb-2">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-400">
                  <Wand2 className="h-4.5 w-4.5 text-white" />
                </div>
                <div className="flex min-w-0 flex-1 items-start justify-between gap-2">
                  <h2 className="min-w-0 flex-1 truncate text-sm font-semibold leading-6" title={item.displayName}>{item.displayName}</h2>
                  <Badge variant="outline" title={item.sourceDisplayName || t('hub.unknownNamespace')}
                    className="max-w-[55%] shrink-0 rounded-full border-blue-200 bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-600 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-blue-400">
                    <span className="truncate">@{item.sourceDisplayName || t('hub.unknownNamespace')}</span>
                  </Badge>
                </div>
              </div>
              <div className="flex-1 px-4 pb-3">
                <p className="min-h-12 line-clamp-3 text-xs leading-relaxed text-muted-foreground">{item.description || t('skill.noDescription')}</p>
                <div className="mt-3 flex items-center gap-3 text-xs text-muted-foreground">
                  <Badge variant="secondary" className="rounded-full bg-muted/50 font-mono text-[11px]">{item.sharedVersion}</Badge>
                  <span className="inline-flex items-center gap-1 tabular-nums" title={t('hub.downloads')}
                    aria-label={`${t('hub.downloads')}: ${item.downloadCount ?? 0}`}>
                    <Download className="h-3.5 w-3.5" />{item.downloadCount ?? 0}
                  </span>
                </div>
                {(item.tags || []).length > 0 && <div className="mt-2 flex flex-wrap gap-1">
                  {item.tags?.slice(0, 3).map(tag => <Badge key={tag} variant="outline" className="text-[10px]">{tag}</Badge>)}
                </div>}
              </div>
              <CardFooter className="flex items-center justify-between gap-3 border-t px-4 py-2 text-xs text-muted-foreground [.border-t]:pt-2">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="inline-flex min-w-0 items-center gap-1" title={`${t('hub.owner')}: ${item.author || '—'}`}>
                    <UserRound className="h-3.5 w-3.5 shrink-0" /><span className="truncate">{item.author || '—'}</span>
                  </span>
                  {item.sharedAt && <span className="inline-flex shrink-0 items-center gap-1">
                    <Clock className="h-3.5 w-3.5" />{dayjs(item.sharedAt).format('YYYY-MM-DD')}
                  </span>}
                </div>
                <span className="inline-flex shrink-0 items-center gap-1">{t('hub.preview')}<ArrowRight className="h-3.5 w-3.5" /></span>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
      {!error && !loading && (
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>{t('hub.total', { count: total })}</span>
          <div className="flex items-center gap-3">
            <Button variant="outline" size="icon" aria-label={t('common.previous')} disabled={pageNo <= 1}
              onClick={() => setPageNo(p => Math.max(1, p - 1))}><ChevronLeft className="h-4 w-4" /></Button>
            <span className="tabular-nums">{pageNo} / {pages}</span>
            <Button variant="outline" size="icon" aria-label={t('common.next')} disabled={pageNo >= pages}
              onClick={() => setPageNo(p => p + 1)}><ChevronRight className="h-4 w-4" /></Button>
          </div>
        </div>
      )}
    </div>
  );
}
