/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { hubResourceApi, resourceErrorMessage, type HubResourceType } from '@/api/hub-resource';
import type { HubShareState } from '@/types/hub-skill';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { canShareResource } from '@/lib/resource-sharing';
import { ResourceSharingPanel } from './ResourceSharingPanel';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props { type: HubResourceType; namespaceId: string; name: string; version?: string | null; status?: string | null; scope?: string | null; enabled?: boolean | null; canWrite?: boolean }
export function ResourceHubActions(props: Props) {
  const hubEnabled = useServerStore(state => state.hubEnabled);
  const authenticated = useAuthStore(state => state.isAuthenticated);
  return hubEnabled && authenticated
    ? <SourceSharing key={`${props.type}:${props.namespaceId}:${props.name}`} {...props} /> : null;
}
function SourceSharing({ type, namespaceId, name, version, status, scope, enabled = true, canWrite = true }: Props) {
  const { t } = useTranslation();
  const generation = useRef({ request: 0 });
  const [confirm, setConfirm] = useState<'share' | 'unshare' | null>(null);
  const [share, setShare] = useState<HubShareState | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const eligible = canShareResource(type, version, status, scope, enabled === true);
  const loadShare = useCallback(async () => {
    if (!canWrite) return;
    const request = ++generation.current.request;
    setLoading(true); setError('');
    try {
      const value = await hubResourceApi.sourceState(type, namespaceId, name);
      if (request === generation.current.request) setShare(value);
    } catch (failure) {
      if (request === generation.current.request) { setShare(null); setError(resourceErrorMessage(failure, t('hub.requestFailed'))); }
    } finally { if (request === generation.current.request) setLoading(false); }
  }, [type, namespaceId, name, canWrite, t]);
  useEffect(() => {
    const tracker = generation.current;
    void loadShare();
    return () => { tracker.request++; };
  }, [loadShare, version, status, scope, enabled]);
  const submit = async () => {
    if (!confirm || !canWrite) return;
    const request = generation.current.request;
    setBusy(true);
    try {
      if (confirm === 'unshare' && share?.active) await hubResourceApi.unshare(type, share);
      else if (confirm === 'share' && version && eligible) {
        if (share?.active) await hubResourceApi.adjust(type, share, version);
        else await hubResourceApi.share(type, namespaceId, name, version);
      } else return;
      if (request !== generation.current.request) return;
      toast.success(t('hubResource.updateSuccess'));
      setConfirm(null);
      await loadShare();
    } catch (failure) {
      if (request !== generation.current.request) return;
      toast.error(resourceErrorMessage(failure, t('hub.requestFailed')));
      setConfirm(null);
      await loadShare();
    } finally { setBusy(false); }
  };
  const title = t('hubResource.shareManagementTitle', { resource: { mcp: 'MCP Server', agent: 'Agent', prompt: 'Prompt', agentspec: 'AgentSpec' }[type] });
  return <>
    <ResourceSharingPanel title={title} version={version} share={share} canWrite={canWrite}
      eligible={eligible} showClone={false} copyable={false} loading={loading} busy={busy} error={error}
      eligibilityHint={t(type === 'prompt' ? 'hubResource.promptOnlyOnline' : 'hubResource.onlyPublicOnline')}
      lifecycleHint={t(type === 'prompt' ? 'hubResource.promptLifecycleReminder' : 'hubResource.lifecycleReminder')}
      copyHint={t('hubResource.copyPublishedOnly')} onRefresh={() => void loadShare()}
      onShare={() => setConfirm('share')} onUnshare={() => setConfirm('unshare')} onCopy={() => {}} />
    <Dialog open={!!confirm} onOpenChange={value => { if (!value && !busy) setConfirm(null); }}><DialogContent><DialogHeader>
      <DialogTitle>{t(confirm === 'unshare' ? 'hub.unshare' : share?.active ? 'hub.adjust' : 'hubResource.share')}</DialogTitle>
      <DialogDescription>{confirm === 'unshare' ? t('hubResource.confirmUnshare') : t('hubResource.shareConfirm', { name, version })}</DialogDescription>
    </DialogHeader><DialogFooter><Button variant="outline" disabled={busy} onClick={() => setConfirm(null)}>{t('common.cancel')}</Button>
      <Button disabled={busy || loading || !!error || (confirm !== 'unshare' && !eligible)} onClick={submit}>{t('common.confirm')}</Button>
    </DialogFooter></DialogContent></Dialog>
  </>;
}
