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

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { hubResourceApi, ordinaryResourcePath, resourceErrorMessage, type HubResourceType } from '@/api/hub-resource';
import { useNamespaceStore } from '@/stores/namespace-store';
import { HUB_SYSTEM_NAMESPACE } from '@/lib/hub-skill-utils';
import { hubSkillApi } from '@/api/hub-skill';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

type Source = { hubEntryId: string; name: string; version: string } | { namespaceId: string; name: string; version: string };
interface Props { type: HubResourceType | 'skill'; source: Source; open: boolean; onOpenChange: (open: boolean) => void }
export function ResourceTransferDialog(props: Props) {
  return props.open ? <TransferForm {...props} key={`${props.type}:${'hubEntryId' in props.source ? props.source.hubEntryId : props.source.namespaceId}:${props.source.name}:${props.source.version}`} /> : null;
}
function TransferForm({ type, source, open, onOpenChange }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { currentNamespace, namespaces, fetchNamespaces } = useNamespaceStore();
  const [namespace, setNamespace] = useState(currentNamespace || 'public');
  const [name, setName] = useState(source.name);
  const [version, setVersion] = useState('0.0.1');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { void fetchNamespaces(); }, [fetchNamespaces]);
  const available = namespaces.filter(ns => ns.namespace !== HUB_SYSTEM_NAMESPACE)
    .map(ns => ({ ...ns, namespace: ns.namespace || 'public' }))
    .filter((ns, index, list) => list.findIndex(item => item.namespace === ns.namespace) === index);
  const submit = async () => {
    if (!name.trim() || !version.trim() || !namespace || namespace === HUB_SYSTEM_NAMESPACE) {
      setError(t('hub.invalidTarget')); return;
    }
    setError(''); setBusy(true);
    try {
      if (type !== 'skill' && !('hubEntryId' in source)) throw new Error('Only Hub import is supported');
      const request = {
        ...('hubEntryId' in source ? { hubEntryId: source.hubEntryId } : {
          sourceNamespaceId: source.namespaceId, sourceName: source.name, sourceVersion: source.version,
        }), targetNamespaceId: namespace, targetName: name.trim(), targetVersion: version.trim(),
      };
      const created = type === 'skill'
        ? ('hubEntryId' in source ? await hubSkillApi.import({ ...request, hubEntryId: source.hubEntryId })
          : await hubSkillApi.clone({ sourceNamespaceId: source.namespaceId, sourceName: source.name, sourceVersion: source.version,
            targetNamespaceId: namespace, targetName: name.trim(), targetVersion: version.trim() })).data
        : await hubResourceApi.import(type, { ...request, hubEntryId: 'hubEntryId' in source ? source.hubEntryId : '' });
      onOpenChange(false);
      toast.success(t('hub.importSuccess'));
      navigate(type === 'skill' ? `/skill/${encodeURIComponent(created.targetName)}?${new URLSearchParams({
        namespaceId: created.targetNamespaceId, version: created.draftVersion,
      })}` : ordinaryResourcePath(type, created.targetNamespaceId, created.targetName, created.draftVersion));
    } catch (failure) { setError(resourceErrorMessage(failure, t('hubEntryId' in source ? 'hub.importFailed' : 'hubResource.copyFailure'))); }
    finally { setBusy(false); }
  };
  return <Dialog open={open} onOpenChange={next => { if (!busy) onOpenChange(next); }}>
    <DialogContent className="sm:max-w-lg">
      <DialogHeader><DialogTitle>{t('hubEntryId' in source ? 'hub.import' : 'hubResource.copy')}</DialogTitle>
        <DialogDescription>{t('hubResource.transferHint', { name: source.name, version: source.version })}</DialogDescription></DialogHeader>
      <div className="space-y-3 py-2">
        <div className="space-y-1.5"><Label htmlFor="resource-transfer-namespace">{t('hub.targetNamespace')}</Label>
          <Select value={namespace} onValueChange={setNamespace} disabled={busy}><SelectTrigger id="resource-transfer-namespace"><SelectValue /></SelectTrigger>
            <SelectContent>{available.map(ns => <SelectItem key={ns.namespace} value={ns.namespace}>{ns.namespaceShowName || ns.namespace}</SelectItem>)}</SelectContent></Select></div>
        <div className="space-y-1.5"><Label htmlFor="resource-transfer-name">{t('hubResource.targetName')}</Label><Input id="resource-transfer-name" value={name} disabled={busy} onChange={event => setName(event.target.value)} /></div>
        <div className="space-y-1.5"><Label htmlFor="resource-transfer-version">{t('hub.targetVersion')}</Label><Input id="resource-transfer-version" value={version} disabled={busy} onChange={event => setVersion(event.target.value)} /></div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      </div>
      <DialogFooter><Button variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
        <Button disabled={busy} onClick={submit}>{busy ? t('common.loading') : t('hubEntryId' in source ? 'hub.import' : 'hubResource.copy')}</Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
