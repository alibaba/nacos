import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ResourceSharingPanel } from '@/components/ai/hub/ResourceSharingPanel';
import { ResourceTransferDialog } from '@/components/ai/hub/ResourceTransferDialog';
import { useAuthStore } from '@/stores/auth-store';
import { canCopyResource } from '@/lib/resource-sharing';
import { hubSkillApi } from '@/api/hub-skill';
import { Button } from '@/components/ui/button';
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
  const authenticated = useAuthStore(state => state.isAuthenticated);
  const [copyOpen, setCopyOpen] = useState(false);
  const generation = useRef({ request: 0 });
  const [share, setShare] = useState<HubShareState | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [confirm, setConfirm] = useState<'share' | 'adjust' | 'unshare' | null>(null);

  const loadShare = useCallback(async () => {
    if (!detail.writable || !authenticated) { onShareChange(null); return; }
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
  }, [namespaceId, skillName, onShareChange, detail.writable, authenticated]);

  useEffect(() => {
    const tracker = generation.current;
    void loadShare();
    return () => { tracker.request++; };
  }, [loadShare, detail]);

  if (!authenticated) return null;
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

  const copyable = canCopyResource('skill', selectedVersion, isOnlineVersion ? 'online' : null, detail.enable);
  return <>
    <ResourceSharingPanel title={t('hub.shareManagement')} version={selectedVersion} share={share}
      canWrite={detail.writable === true} eligible={!!eligible} copyable={copyable}
      loading={loading} busy={busy} error={error ? t(error === 'permission' ? 'hub.authRequired' : 'hub.requestFailed') : ''}
      eligibilityHint={t('hub.onlyPublicOnline')} lifecycleHint={t('hub.lifecycleReminder')}
      copyHint={t('hubResource.skillCopyOnlineOnly')} onRefresh={() => void loadShare()}
      onShare={() => setConfirm(updateKind)} onUnshare={() => setConfirm('unshare')} onCopy={() => setCopyOpen(true)} />
    <ResourceTransferDialog type="skill" source={{ namespaceId, name: skillName, version: selectedVersion }} open={copyOpen} onOpenChange={setCopyOpen} />
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
  </>;
}
