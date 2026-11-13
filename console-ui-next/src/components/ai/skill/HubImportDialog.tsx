import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { hubSkillApi } from '@/api/hub-skill';
import { useNamespaceStore } from '@/stores/namespace-store';
import { HUB_SYSTEM_NAMESPACE, hubErrorMessage } from '@/lib/hub-skill-utils';
import type { HubSkillDetail } from '@/types/hub-skill';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

interface Props {
  detail: HubSkillDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function HubImportDialog(props: Props) {
  return props.open ? <HubImportForm key={props.detail.hubEntryId} {...props} /> : null;
}

function HubImportForm({ detail, open, onOpenChange }: Props) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { currentNamespace, namespaces } = useNamespaceStore();
  const available = namespaces.filter(ns => ns.namespace !== HUB_SYSTEM_NAMESPACE)
    .map(ns => ({ ...ns, namespace: ns.namespace || 'public' }))
    .filter((ns, index, list) => list.findIndex(item => item.namespace === ns.namespace) === index);
  const firstNamespace = available[0]?.namespace || 'public';
  const [targetNamespaceId, setTargetNamespaceId] = useState(currentNamespace && currentNamespace !== HUB_SYSTEM_NAMESPACE ? currentNamespace : firstNamespace);
  const [targetName, setTargetName] = useState(detail.displayName);
  const [targetVersion, setTargetVersion] = useState('0.0.1');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const submit = async () => {
    const name = targetName.trim();
    if (!name || !targetVersion.trim() || !targetNamespaceId || targetNamespaceId === HUB_SYSTEM_NAMESPACE) {
      setMessage(t('hub.invalidTarget')); return;
    }
    setBusy(true);
    setMessage('');
    try {
      const response = await hubSkillApi.import({
        hubEntryId: detail.hubEntryId, targetNamespaceId, targetName: name, targetVersion: targetVersion.trim(),
      });
      onOpenChange(false);
      toast.success(t('hub.importSuccess'));
      navigate(`/skill/${encodeURIComponent(response.data.targetName)}?${new URLSearchParams({
        namespaceId: response.data.targetNamespaceId, version: response.data.draftVersion,
      })}`);
    } catch (err) {
      const code = hubErrorMessage(err, 'request');
      setMessage(code === 'conflict' ? t('hub.conflict') : code === 'permission' ? t('hub.noTargetWrite')
        : code === 'unavailable' ? t('hub.unavailable') : t('hub.importFailed'));
    } finally {
      setBusy(false);
    }
  };

  return <Dialog open={open} onOpenChange={next => { if (!busy) onOpenChange(next); }}>
    <DialogContent className="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>{t('hub.import')}</DialogTitle>
        <DialogDescription>{t('hub.importHint', { name: detail.displayName, version: detail.sharedVersion })}</DialogDescription>
      </DialogHeader>
      <div className="space-y-3 py-2">
        <div className="space-y-1.5">
          <Label htmlFor="hub-target-namespace">{t('hub.targetNamespace')}</Label>
          <Select value={targetNamespaceId} onValueChange={setTargetNamespaceId} disabled={busy}>
            <SelectTrigger id="hub-target-namespace"><SelectValue placeholder={t('hub.targetNamespace')} /></SelectTrigger>
            <SelectContent>
              {available.map(ns => <SelectItem key={ns.namespace} value={ns.namespace}>
                {ns.namespaceShowName || ns.namespace}
              </SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="hub-target-name">{t('hub.targetName')}</Label>
          <Input id="hub-target-name" value={targetName} onChange={e => setTargetName(e.target.value)} disabled={busy} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="hub-target-version">{t('hub.targetVersion')}</Label>
          <Input id="hub-target-version" value={targetVersion} onChange={e => setTargetVersion(e.target.value)} disabled={busy} />
        </div>
        <p className="text-xs text-muted-foreground">{t('hub.independentDraft')}</p>
        {message && <p role="alert" className="text-sm text-destructive">{message}</p>}
      </div>
      <DialogFooter>
        <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>{t('common.cancel')}</Button>
        <Button onClick={submit} disabled={busy}>{busy ? t('common.loading') : t('hub.import')}</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}
