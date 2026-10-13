import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Copy, Download, ExternalLink, FileText, RefreshCw, ShieldAlert, Wand2 } from 'lucide-react';
import { HubSkillVersionInfo } from '@/components/ai/skill/HubSkillVersionInfo';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { HubSkillMarkdown } from '@/components/ai/skill/HubSkillMarkdown';
import { toast } from 'sonner';
import { hubSkillApi } from '@/api/hub-skill';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { useNamespaceStore } from '@/stores/namespace-store';
import { HubImportDialog } from '@/components/ai/skill/HubImportDialog';
import { SkillResourcePanel } from '@/pages/skillDetail/SkillResourcePanel';
import type { SkillResource } from '@/types/skill';
import type { HubSkillDetail, HubSkillContent, HubShareState } from '@/types/hub-skill';
import { hubErrorMessage, sourceSkillPath, validHubId } from '@/lib/hub-skill-utils';

export default function HubSkillDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { hubEntryId = '' } = useParams<{ hubEntryId: string }>();
  const { fetchNamespaces } = useNamespaceStore();
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
    Promise.all([hubSkillApi.detail(hubEntryId), hubSkillApi.content(hubEntryId)])
      .then(([d, c]) => {
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
    if (!detail) return;
    let cancelled = false;
    hubSkillApi.governanceState(hubEntryId).then(r => {
      if (!cancelled) setAdminState(r.data);
    }).catch(() => { if (!cancelled) setAdminState(null); });
    return () => { cancelled = true; };
  }, [detail, hubEntryId]);

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
      navigate('/hub/skills', { replace: true });
    } catch (err) {
      const code = hubErrorMessage(err, 'request');
      toast.error(code === 'conflict' ? t('hub.staleVersion') : t('hub.requestFailed'));
      setTakedownOpen(false);
      setRefresh(x => x + 1);
    } finally { setActionBusy(false); }
  };

  return <div className="space-y-5 pb-8">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <Button variant="ghost" size="sm" onClick={() => navigate('/hub/skills')}>
        <ArrowLeft className="mr-1 h-4 w-4" />{t('hub.backToHub')}
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
      <div className="rounded-xl border bg-card p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-3">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-400">
              <Wand2 className="h-6 w-6 text-white" />
            </div>
            <div className="min-w-0">
              <h1 className="break-words text-xl font-semibold">{detail.displayName}</h1>
              <div className="mt-1 flex items-center gap-2">
                <Badge variant="secondary">{t('hub.readOnly')}</Badge>
                <Badge variant="outline">{detail.sharedVersion}</Badge>
              </div>
              <p className="mt-2 text-sm text-muted-foreground">{detail.description || t('skill.noDescription')}</p>
              <p className="mt-2 text-xs text-muted-foreground">
                {t('hub.source')}: {detail.sourceDisplayName || t('hub.hidden')}
                {detail.author ? ` · ${t('hub.author')}: ${detail.author}` : ''}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {sourcePath && <Button variant="outline" size="sm" onClick={() => navigate(sourcePath)}>
              <ExternalLink className="mr-1 h-4 w-4" />{t('hub.openSourceVersion')}
            </Button>}
            <Button size="sm" onClick={() => { void fetchNamespaces(); setImportOpen(true); }}>
              {t('hub.import')}
            </Button>
            {adminState?.active && <Button variant="destructive" size="sm" onClick={() => setTakedownOpen(true)}>
              <ShieldAlert className="mr-1 h-4 w-4" />{t('hub.takedown')}
            </Button>}
          </div>
        </div>
        {(detail.tags || []).length > 0 && <div className="mt-3 flex flex-wrap gap-1.5">
          {detail.tags?.map(tag => <Badge key={tag} variant="outline">{tag}</Badge>)}
        </div>}
      </div>
      <Tabs value={tab} onValueChange={setTab} className="flex w-full min-w-0 flex-col">
        <TabsList className="w-fit">
          <TabsTrigger value="markdown" className="gap-1.5"><FileText className="h-3.5 w-3.5" />SKILL.md</TabsTrigger>
          <TabsTrigger value="resources" className="gap-1.5"><Wand2 className="h-3.5 w-3.5" />{t('skill.resources')}</TabsTrigger>
        </TabsList>
        <TabsContent value="markdown">
          <div className="flex flex-col items-start gap-4 lg:flex-row">
            <Card className="w-full min-w-0 flex-1 overflow-hidden gap-0 py-0">
              <div className="flex items-center justify-between border-b bg-muted/30 px-5 py-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <FileText className="h-4 w-4 text-muted-foreground" />SKILL.md
                </h2>
                <div className="flex gap-1">
                  <Button size="icon" variant="ghost" className="h-7 w-7" aria-label={t('skill.resourceCopyFile')} onClick={copyMarkdown}>
                    <Copy className="h-3.5 w-3.5" />
                  </Button>
                  <Button size="icon" variant="ghost" className="h-7 w-7" aria-label={t('skill.resourceDownloadFile')} onClick={saveMarkdown}>
                    <Download className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
              <CardContent className="p-5" data-color-mode="light">
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
