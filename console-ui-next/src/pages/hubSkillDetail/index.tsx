import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Copy, Download, ExternalLink, FileText, RefreshCw, ShieldAlert, Wand2 } from 'lucide-react';
import { HubSkillVersionInfo } from '@/components/ai/skill/HubSkillVersionInfo';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { HubSkillMarkdown } from '@/components/ai/skill/HubSkillMarkdown';
import { toast } from 'sonner';
import { hubSkillApi } from '@/api/hub-skill';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { useAuthStore } from '@/stores/auth-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { HubImportDialog } from '@/components/ai/skill/HubImportDialog';
import { SkillResourcePanel } from '@/pages/skillDetail/SkillResourcePanel';
import type { SkillResource } from '@/types/skill';
import type { HubSkillDetail, HubSkillContent, HubShareState } from '@/types/hub-skill';
import { hubErrorMessage, sourceSkillPath, validHubId, SHARED_SKILLS_PATH } from '@/lib/hub-skill-utils';

export default function HubSkillDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hubEntryId = '' } = useParams<{ hubEntryId: string }>();
  const { fetchNamespaces } = useNamespaceStore();
  const { isAuthenticated } = useAuthStore();
  const [detail, setDetail] = useState<HubSkillDetail | null>(null);
  const [content, setContent] = useState<HubSkillContent | null>(null);
  const [adminState, setAdminState] = useState<HubShareState | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [takedownOpen, setTakedownOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [actionBusy, setActionBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [tab, setTab] = useState('markdown');
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setContent(null);
    setAdminState(null);
    setError('');
    setTab('markdown');
    setBusy(false);
    setImportOpen(false);
    setTakedownOpen(false);
    if (!validHubId(hubEntryId)) {
      setError('unavailable');
      return;
    }
    setBusy(true);
    hubSkillApi.detail(hubEntryId)
      .then(async d => {
        if (cancelled) return;
        const c = await hubSkillApi.content(hubEntryId, d.data.sharedVersion, d.data.displayName);
        if (cancelled) return;
        if (d.data.hubEntryId !== hubEntryId || c.data.hubEntryId !== hubEntryId ||
            d.data.sharedVersion !== c.data.sharedVersion) {
          setError('unavailable'); return;
        }
        setDetail(d.data);
        setContent(c.data);
      })
      .catch(err => {
        if (!cancelled) setError(hubErrorMessage(err, 'request'));
      })
      .finally(() => { if (!cancelled) setBusy(false); });
    return () => { cancelled = true; };
  }, [hubEntryId, refresh]);

  useEffect(() => {
    if (!detail || !isAuthenticated) return;
    let cancelled = false;
    hubSkillApi.governanceState(hubEntryId).then(r => {
      if (!cancelled) setAdminState(r.data);
    }).catch(() => { if (!cancelled) setAdminState(null); });
    return () => { cancelled = true; };
  }, [detail, hubEntryId, isAuthenticated]);

  const resourceMap = useMemo<Record<string, SkillResource>>(() => Object.fromEntries(
    Object.entries(content?.resource ?? {}).map(([id, value]) => [id, { ...value, metadata: null }]),
  ), [content]);
  const sourcePath = detail ? sourceSkillPath(detail) : null;
  const errorLabel = error === 'permission' ? t('hub.authRequired')
    : error === 'unavailable' ? t('hub.unavailable') : t('hub.requestFailed');

  const download = async () => {
    if (!detail || downloading) return;
    setDownloading(true);
    try {
      const blob = await hubSkillApi.download(hubEntryId, detail.sharedVersion);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${detail.displayName}-${detail.sharedVersion}.zip`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setRefresh(x => x + 1);
    } catch (err) {
      const code = hubErrorMessage(err, 'request');
      toast.error(code === 'conflict' ? t('hub.staleVersion') : t('hub.requestFailed'));
      setRefresh(x => x + 1);
    } finally { setDownloading(false); }
  };

  const saveMarkdown = () => {
    if (!content) return;
    const url = URL.createObjectURL(new Blob([content.skillMd], { type: 'text/markdown;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'SKILL.md';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const copyMarkdown = async () => {
    try {
      await navigator.clipboard.writeText(content?.skillMd ?? '');
      toast.success(t('skill.resourceCopySuccess'));
    } catch { toast.error(t('hub.requestFailed')); }
  };

  const takeDown = async () => {
    if (!adminState?.active || !adminState.metaVersion || !reason.trim()) return;
    setActionBusy(true);
    try {
      await hubSkillApi.forceUnshare({
        hubEntryId, expectedMetaVersion: adminState.metaVersion, reason: reason.trim(),
      });
      setTakedownOpen(false);
      toast.success(t('hub.takedownSuccess'));
      navigate(SHARED_SKILLS_PATH, { replace: true });
    } catch (err) {
      const code = hubErrorMessage(err, 'request');
      toast.error(code === 'conflict' ? t('hub.staleVersion') : t('hub.requestFailed'));
      setTakedownOpen(false);
      setRefresh(x => x + 1);
    } finally { setActionBusy(false); }
  };

  return <div className="space-y-5 pb-5">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <Button variant="ghost" size="sm" className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2" onClick={() => navigate(SHARED_SKILLS_PATH)}>
        <ArrowLeft className="h-3.5 w-3.5" />{t('hub.backToHub')}
      </Button>
      <Button size="sm" variant="outline" disabled={busy} onClick={() => setRefresh(x => x + 1)}>
        <RefreshCw className="mr-1 h-4 w-4" />{t('common.refresh')}
      </Button>
    </div>
    {busy && <p className="py-12 text-center text-muted-foreground">{t('common.loading')}</p>}
    {!busy && error && (
      <div role="alert" className="rounded-xl border p-8 text-center space-y-2">
        <ShieldAlert className="mx-auto h-8 w-8 text-destructive" />
        <p className="text-sm">{errorLabel}</p>
        <Button variant="outline" onClick={() => setRefresh(x => x + 1)}>{t('common.retry')}</Button>
      </div>
    )}
    {!busy && !error && detail && content && <>
      <div className="rounded-xl border bg-card px-5 py-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 flex-1 items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-400 shadow-lg shadow-violet-500/20">
              <Wand2 className="h-7 w-7 text-white" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center gap-2.5 mb-1">
                <h1 className="break-words text-xl font-bold tracking-tight">{detail.displayName}</h1>
                <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">{detail.sharedVersion}</span>
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">{detail.description || t('skill.noDescription')}</p>
              <p className="mt-2 text-xs text-muted-foreground leading-normal">
                {t('hub.source')}: {detail.sourceDisplayName || t('hub.hidden')}
                {detail.author ? ` · ${t('hub.author')}: ${detail.author}` : ''}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {sourcePath && <Button variant="outline" size="sm" onClick={() => navigate(sourcePath)}>
              <ExternalLink className="mr-1 h-4 w-4" />{t('hub.openSourceVersion')}
            </Button>}
            <Button size="sm" onClick={() => { if (!isAuthenticated) { navigate('/login'); return; } void fetchNamespaces(); setImportOpen(true); }}>
              {t('hub.import')}
            </Button>
            {adminState?.active && <Button variant="destructive" size="sm" onClick={() => setTakedownOpen(true)}>
              <ShieldAlert className="mr-1 h-4 w-4" />{t('hub.takedown')}
            </Button>}
          </div>
        </div>
        {(detail.tags || []).length > 0 && <div className="mt-3 flex flex-wrap gap-1.5">
          {detail.tags?.map(tag => <DetailTagChip key={tag} label={tag} />)}
        </div>}
      </div>
      <Tabs value={tab} onValueChange={setTab} className="flex w-full min-w-0 flex-col">
        <TabsList className="w-fit">
          <TabsTrigger value="markdown" className="gap-1.5"><FileText className="h-3.5 w-3.5" />SKILL.md</TabsTrigger>
          <TabsTrigger value="resources" className="gap-1.5"><Wand2 className="h-3.5 w-3.5" />{t('skill.resources')}</TabsTrigger>
        </TabsList>
        <TabsContent value="markdown">
          <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
            <Card className="overflow-hidden py-0 gap-0 min-w-0 min-h-[580px]">
              <div className="flex h-11 items-center justify-between gap-3 border-b bg-muted/30 px-5">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <FileText className="h-4 w-4 text-muted-foreground" />SKILL.md
                </h2>
                <div className="flex shrink-0 items-center gap-1">
                  <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground" aria-label={t('skill.resourceCopyFile')} onClick={copyMarkdown}>
                    <Copy className="h-3.5 w-3.5" />
                  </Button>
                  <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground" aria-label={t('skill.resourceDownloadFile')} onClick={saveMarkdown}>
                    <Download className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
              <CardContent className="p-5">
                <HubSkillMarkdown source={content.skillMd ?? ''} />
              </CardContent>
            </Card>
            <HubSkillVersionInfo detail={detail} onDownload={download} downloading={downloading} />
          </div>
        </TabsContent>
        <TabsContent value="resources">
          <SkillResourcePanel resources={resourceMap} editable={false} />
        </TabsContent>
      </Tabs>
      <HubImportDialog open={importOpen} onOpenChange={setImportOpen} detail={detail} />
      <Dialog open={takedownOpen} onOpenChange={setTakedownOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t('hub.takedown')}</DialogTitle>
            <DialogDescription>{t('hub.takedownWarning')}</DialogDescription></DialogHeader>
          <Input aria-label={t('hub.reason')} placeholder={t('hub.reason')} value={reason} onChange={e => setReason(e.target.value)} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setTakedownOpen(false)} disabled={actionBusy}>{t('common.cancel')}</Button>
            <Button variant="destructive" disabled={actionBusy || !reason.trim()} onClick={takeDown}>{t('common.confirm')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>}
  </div>;
}
