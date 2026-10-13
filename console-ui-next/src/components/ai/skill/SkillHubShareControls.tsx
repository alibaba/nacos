import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { AlertTriangle, Share2, RefreshCw } from 'lucide-react';
import { hubSkillApi } from '@/api/hub-skill';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { hubErrorMessage } from '@/lib/hub-skill-utils';
import type { HubShareState } from '@/types/hub-skill';
import type { SkillAdminDetail } from '@/types/skill';

interface Props {
  namespaceId: string;
  skillName: string;
  selectedVersion: string;
  detail: SkillAdminDetail;
  onShareChange: (version: string | null) => void;
}

/** Source Skill actions use exact source identity and server-side source WRITE checks. */
export function SkillHubShareControls({ namespaceId, skillName, selectedVersion, detail, onShareChange }: Props) {
  const { t } = useTranslation();
  const generation = useRef({ request: 0 });
  const [share, setShare] = useState<HubShareState | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [confirm, setConfirm] = useState<'share' | 'adjust' | 'unshare' | null>(null);

  const loadShare = useCallback(async () => {
    const request = ++generation.current.request;
    setLoading(true);
    setError('');
    try {
      const response = await hubSkillApi.sourceShare(namespaceId, skillName);
      if (request !== generation.current.request) return;
      setShare(response.data);
      onShareChange(response.data?.active ? response.data.sharedVersion : null);
    } catch (err) {
      if (request !== generation.current.request) return;
      setShare(null);
      onShareChange(null);
      setError(hubErrorMessage(err, 'request'));
    } finally { if (request === generation.current.request) setLoading(false); }
  }, [namespaceId, skillName, onShareChange]);

  useEffect(() => {
    const tracker = generation.current;
    void loadShare();
    return () => { tracker.request++; };
  }, [loadShare, detail]);

  if (!detail.writable) return null;
  const isOnlineVersion = detail.versions?.some(v => v.version === selectedVersion && v.status === 'online');
  const eligible = detail.enable && detail.scope === 'PUBLIC' && isOnlineVersion;
  const active = !!share?.active;
  const updateKind = active ? 'adjust' : 'share';

  const submit = async () => {
    if (!confirm) return;
    setBusy(true);
    const request = generation.current.request;
    try {
      if (confirm === 'unshare' && share?.hubEntryId && share.metaVersion) {
        await hubSkillApi.unshare({ hubEntryId: share.hubEntryId, expectedMetaVersion: share.metaVersion });
      } else if (confirm === 'adjust' && share?.hubEntryId && share.metaVersion && eligible) {
        await hubSkillApi.adjust({
          hubEntryId: share.hubEntryId, expectedMetaVersion: share.metaVersion, targetVersion: selectedVersion,
        });
      } else if (confirm === 'share' && eligible) {
        await hubSkillApi.share({ sourceNamespaceId: namespaceId, sourceName: skillName, sourceVersion: selectedVersion });
      } else {
        throw new Error('Invalid Hub share state');
      }
      if (request !== generation.current.request) return;
      toast.success(t('hub.updateSuccess'));
      setConfirm(null);
      await loadShare();
    } catch (err) {
      if (request !== generation.current.request) return;
      const code = hubErrorMessage(err, 'request');
      toast.error(code === 'conflict' ? t('hub.staleVersion') : code === 'permission'
        ? t('hub.authRequired') : t('hub.requestFailed'));
      setConfirm(null);
      await loadShare();
    } finally { setBusy(false); }
  };

  return <Card className="gap-0 overflow-hidden py-0">
    <div className="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
      <h2 className="inline-flex items-center gap-2 text-sm font-semibold"><Share2 className="h-4 w-4" />{t('hub.shareManagement')}</h2>
      <Button variant="ghost" size="icon" className="h-6 w-6" disabled={loading || busy}
        aria-label={t('common.refresh')} onClick={() => void loadShare()}><RefreshCw className="h-3.5 w-3.5" /></Button>
    </div>
    <CardContent className="space-y-3 p-4">
      {error && <p className="text-xs text-destructive">{error === 'permission' ? t('hub.authRequired') : t('hub.requestFailed')}</p>}
      {!error && <>
        {active ? <div className="flex flex-wrap items-center gap-2 text-xs">
          <Badge variant="secondary">{t('hub.shared')}</Badge>
          <span>{t('hub.sharedVersion')}: <strong>{share?.sharedVersion}</strong></span>
        </div> : <p className="text-xs text-muted-foreground">{t('hub.notShared')}</p>}
        {!eligible && <p className="flex gap-1 text-xs text-amber-600"><AlertTriangle className="h-3.5 w-3.5 shrink-0" />{t('hub.onlyPublicOnline')}</p>}
        <p className="text-xs text-muted-foreground">{t('hub.lifecycleReminder')}</p>
        <div className="flex flex-wrap items-center gap-2">
          {eligible && !(active && share?.sharedVersion === selectedVersion) &&
            <Button size="sm" variant="outline" disabled={loading || busy} onClick={() => setConfirm(updateKind)}>
              {active ? t('hub.adjust') : t('hub.share')}
            </Button>}
          {active && <Button size="sm" variant="destructive" disabled={loading || busy}
            onClick={() => setConfirm('unshare')}>{t('hub.unshare')}</Button>}
        </div>
      </>}
    </CardContent>
    <Dialog open={!!confirm} onOpenChange={open => { if (!open && !busy) setConfirm(null); }}>
      <DialogContent>
        <DialogHeader><DialogTitle>{confirm === 'unshare' ? t('hub.unshare') : confirm === 'adjust' ? t('hub.adjust') : t('hub.share')}</DialogTitle>
          <DialogDescription>{confirm === 'unshare' ? t('hub.unshareConfirm') : t('hub.shareConfirm', { version: selectedVersion })}</DialogDescription></DialogHeader>
        <DialogFooter>
          <Button variant="outline" disabled={busy} onClick={() => setConfirm(null)}>{t('common.cancel')}</Button>
          <Button disabled={busy} onClick={submit}>{t('common.confirm')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </Card>;
}
