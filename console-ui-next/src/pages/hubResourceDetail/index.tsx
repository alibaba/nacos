import type { ReactNode } from 'react';
import { AgentDetailHeaderFrame } from '@/components/ai/AgentDetailContent';
import { ResourceTransferDialog } from '@/components/ai/hub/ResourceTransferDialog';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Bot, Clock, Cpu, ExternalLink, FileBox, MessageSquare, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { hubResourceApi, sourceResourcePath, resourceListPath, resourceTypeForPath, type SharedResource } from '@/api/hub-resource';
import { resourceDefinition, resourceDownloadName } from '@/lib/hub-resource-downloads';
import { validHubId } from '@/lib/hub-skill-utils';
import { useAuthStore } from '@/stores/auth-store';
import type { HubShareState } from '@/types/hub-skill';
import { Button } from '@/components/ui/button';
import { HubResourceContent } from '@/components/ai/hub/HubResourceContent';
import { HubResourceVersionInfo } from '@/components/ai/hub/HubResourceVersionInfo';
import { agentSpecContent } from '@/lib/hub-resource-content';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';

export default function HubResourceDetailPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const { hubEntryId = '' } = useParams();
  const type = resourceTypeForPath(location.pathname);
  const authenticated = useAuthStore(state => state.isAuthenticated);
  const [importOpen, setImportOpen] = useState(false);
  const globalAdmin = useAuthStore(state => state.globalAdmin);
  const [detail, setDetail] = useState<SharedResource | null>(null);
  const [content, setContent] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const [governance, setGovernance] = useState<HubShareState | null>(null);
  const [takedownOpen, setTakedownOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [acting, setActing] = useState(false);
  const sourcePath = detail ? sourceResourcePath(detail) : null;
  const sourceUpdatedAt = detail?.metadata?.updateTime ?? detail?.metadata?.gmtModified;
  const listPath = `${resourceListPath(type)}${location.search}`;
  useEffect(() => {
    let cancelled = false;
    setDetail(null); setContent(null); setError(''); setGovernance(null);
    setImportOpen(false); setTakedownOpen(false);
    if (!validHubId(hubEntryId)) { setLoading(false); setError(t('hub.unavailable')); return; }
    setLoading(true);
    hubResourceApi.detail(type, hubEntryId).then(async selected => {
      const body = await hubResourceApi.content(type, hubEntryId, selected.version);
      if (type === 'agentspec') agentSpecContent(body);
      else resourceDefinition(selected, body);
      if (!cancelled) { setDetail(selected); setContent(body); }
    }).catch(failure => { if (!cancelled) setError(failure instanceof Error ? failure.message : t('hub.unavailable')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [type, hubEntryId, reload, t]);
  useEffect(() => {
    if (!detail || !globalAdmin) return;
    let cancelled = false;
    hubResourceApi.governance(type, hubEntryId).then(state => { if (!cancelled) setGovernance(state); })
      .catch(() => { if (!cancelled) setGovernance(null); });
    return () => { cancelled = true; };
  }, [type, hubEntryId, detail, globalAdmin]);
  const download = async () => {
    if (!detail || type !== 'prompt' || downloading) return;
    setDownloading(true);
    try {
      // The ordinary Prompt Markdown download revalidates the pinned Hub publication.
      const blob = await hubResourceApi.downloadPrompt(hubEntryId, detail.version);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url; link.download = resourceDownloadName(detail);
      document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
      setReload(value => value + 1);
    } catch (failure) { toast.error(failure instanceof Error ? failure.message : t('hub.requestFailed')); }
    finally { setDownloading(false); }
  };
  const takeDown = async () => {
    if (!governance?.active || !reason.trim()) return;
    setActing(true);
    try {
      await hubResourceApi.forceUnshare(type, governance, reason.trim());
      toast.success(t('hubResource.takedownSuccess')); navigate(listPath, { replace: true });
    } catch (failure) { toast.error(failure instanceof Error ? failure.message : t('hub.requestFailed')); setReload(value => value + 1); }
    finally { setActing(false); setTakedownOpen(false); }
  };
  const Icon = type === 'mcp' ? Cpu : type === 'agent' ? Bot : type === 'prompt' ? MessageSquare : FileBox;
  const safeArgument = /^[a-zA-Z0-9_][a-zA-Z0-9_.+-]*$/;
  const install = type === 'agentspec' && detail && safeArgument.test(detail.resourceName) && safeArgument.test(detail.version)
    ? `nacos-cli agentspec-get ${detail.resourceName} --hub-entry-id ${hubEntryId} --version ${detail.version}` : null;
  return <div className="space-y-5 pb-5">
    <div className="flex items-center justify-between gap-2"><Button variant="ghost" size="sm" className="-ml-2 h-7 gap-1.5" onClick={() => navigate(listPath)}><ArrowLeft className="h-3.5 w-3.5" />{t('hubResource.back', { resource: t(`hubResource.${type}`) })}</Button>
      <Button size="sm" variant="outline" disabled={loading} onClick={() => setReload(value => value + 1)}><RefreshCw className="mr-1 h-4 w-4" />{t('common.refresh')}</Button></div>
    {loading && <p className="py-12 text-center text-muted-foreground">{t('common.loading')}</p>}
    {!loading && error && <div role="alert" className="rounded-xl border p-8 text-center space-y-2"><p className="text-sm">{error}</p>
      <Button size="sm" variant="outline" onClick={() => setReload(value => value + 1)}>{t('common.retry')}</Button></div>}
    {!loading && !error && detail && content && <>
      <HubHeaderFrame agent={type === 'agent'}><div className="flex flex-wrap items-start justify-between gap-4"><div className="flex min-w-0 flex-1 items-start gap-4">
        <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ${type === 'mcp' ? 'from-blue-500 to-cyan-400' : type === 'agent' ? 'from-violet-500 to-purple-400' : type === 'prompt' ? 'from-amber-500 to-orange-400' : 'from-violet-500 to-fuchsia-400'} shadow-lg`}><Icon className="h-7 w-7 text-white" /></div>
        <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h1 className="text-xl font-bold tracking-tight break-words">{detail.resourceName}</h1>
          </div>
          <p className="mt-2 text-sm text-muted-foreground whitespace-pre-wrap">{detail.description}</p>
          {typeof sourceUpdatedAt === 'number' && sourceUpdatedAt > 0 && <span className="mt-2 inline-flex items-center gap-1 text-xs text-muted-foreground"><Clock className="h-3 w-3" />{new Date(sourceUpdatedAt).toLocaleString()}</span>}
          <p className="mt-2 text-xs text-muted-foreground">{t('hubResource.source', { namespace: detail.sourceDisplayName || t('hub.unknownNamespace'), author: detail.author || '-' })}</p>{type === 'agent' && <p className="mt-2 text-xs text-muted-foreground">{t('hub.downloads')}: {detail.downloadCount ?? 0}</p>}</div></div>
        <div className="flex flex-wrap gap-2">
          {sourcePath && <Button variant="outline" size="sm" onClick={() => navigate(sourcePath)}><ExternalLink className="mr-1 h-4 w-4" />{t('hub.openSourceVersion')}</Button>}
          {authenticated && <Button size="sm" onClick={() => setImportOpen(true)}>{t('hub.import')}</Button>}
          {governance?.active && <Button size="sm" variant="destructive" onClick={() => setTakedownOpen(true)}>{t('hub.takedown')}</Button>}</div></div></HubHeaderFrame>
      <HubResourceContent key={`${hubEntryId}@${detail.version}`} detail={detail} content={content} sidebar={<HubResourceVersionInfo detail={detail} content={content} install={install} onDownload={download} downloading={downloading} />} />
      <ResourceTransferDialog type={type} source={{ hubEntryId, name: detail.resourceName, version: detail.version }} open={importOpen} onOpenChange={setImportOpen} />
    </>}
    <Dialog open={takedownOpen} onOpenChange={value => { if (!acting) setTakedownOpen(value); }}><DialogContent>
      <DialogHeader><DialogTitle>{t('hub.takedown')}</DialogTitle><DialogDescription>{t('hubResource.takedownHint')}</DialogDescription></DialogHeader>
      <Input value={reason} maxLength={512} onChange={event => setReason(event.target.value)} aria-label={t('hubResource.reason')} />
      <DialogFooter><Button variant="outline" disabled={acting} onClick={() => setTakedownOpen(false)}>{t('common.cancel')}</Button><Button variant="destructive" disabled={acting || !reason.trim()} onClick={takeDown}>{t('hub.takedown')}</Button></DialogFooter>
    </DialogContent></Dialog>
  </div>;
}

function HubHeaderFrame({ agent, children }: { agent: boolean; children: ReactNode }) {
  return agent ? <AgentDetailHeaderFrame>{children}</AgentDetailHeaderFrame>
    : <div className="rounded-xl border bg-card px-5 py-4">{children}</div>;
}
